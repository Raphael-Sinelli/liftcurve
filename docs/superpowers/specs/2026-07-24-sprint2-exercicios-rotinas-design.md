# gym-progress-tracker — Sprint 2 (Exercícios + Rotinas) — Design

## Status do projeto

Sprint 0 (Fundações) e Sprint 1 (Schema + Auth) estão **completas e mergeadas em `develop`**
(branch `feature/sprint0-1-fundacoes-auth`, 13 commits, revisão de branch inteira feita,
1 finding Important corrigido). O design original completo (Sprint 0-7) continua em
`docs/superpowers/specs/2026-07-23-gym-progress-tracker-design.md` — este documento cobre
só a **Sprint 2**.

**Recomendação sobre granularidade do plano:** um plano de implementação por sprint (não um
plano único cobrindo Sprint 2-7), mesmo padrão usado em Sprint 0-1. Motivo: Sprint 3 (domínio
core — 1RM/volume/platô) tem perfil de trabalho bem diferente de Sprint 2 (CRUD), Sprint 5
(frontend) depende de tudo anterior estar mergeado, e Sprint 6 (deploy) só faz sentido depois
que há algo completo pra deployar. Manter 1 branch + 1 revisão de branch inteira por sprint
mantém os ciclos de revisão administráveis e permite ajustar o plano seguinte com base no que
foi aprendido no anterior (ex.: o finding de validação em Sprint 1 já vai informar como Sprint 2
trata exceções de segurança/autorização desde o início).

---

## Sprint 2 — Design Detalhado

### Contexto desta sprint

Sprint 2 introduz CRUD de Exercícios (catálogo global + custom por usuário) e Rotinas/templates
(`routine_exercises`). É a **primeira sprint com endpoints autenticados** — Sprint 0-1 emitiu
tokens JWT mas nenhum endpoint jamais os validou (todos os `/auth/*` são públicos). Isso significa
que antes de qualquer CRUD, essa sprint precisa resolver como um endpoint REST autentica uma
requisição e descobre "quem está pedindo" — decisão de arquitetura nova, não só repetição do
padrão de Sprint 1.

### Decisão 1 — Autenticação de endpoints (novo)

`quarkus-smallrye-jwt` já é dependência do `pom.xml` — essa extensão sozinha já empacota tanto
o mecanismo de auth HTTP (Bearer) quanto o identity provider (`MpJwtValidator`), sem precisar
de `quarkus-oidc` nem `IdentityProvider` customizado. `@Authenticated` funciona só com essa
extensão presente.

**Bug real encontrado na config existente, nunca exercitado até agora:** `application.properties`
tem `mp.jwt.verify.secretkey=${gymtracker.jwt.secret}` — esse nome de property **está errado**.
O property certo pra segredo simétrico (HS256) é **`smallrye.jwt.verify.secretkey`** (property
específico do SmallRye, sem prefixo `mp.`, diferente de `mp.jwt.verify.publickey` que é do spec
MP-JWT pra RSA). `mp.jwt.verify.issuer` está correto. Como MicroProfile Config ignora properties
desconhecidos silenciosamente, isso nunca deu erro nenhum — só nunca funcionou, porque nunca foi
testado (Sprint 1 só emitia token, nunca validava). Falta também `smallrye.jwt.verify.algorithm=HS256`
— sem isso o default de verificação é RS256, e ele rejeitaria o HS256 que `TokenService` assina.
**Corrigir os dois na Task 1**, antes de qualquer outra coisa — é provável que o teste de "token
válido → 200" quebre primeiro (não os testes de 401), justamente por essa causa.

Abordagem:
- Anotar recursos/métodos protegidos com `@Authenticated` (não `@RolesAllowed`, porque o
  token não carrega claim de role/group — só `sub` com o UUID do usuário — então não há
  role pra checar, só "existe uma identidade válida ou não").
- Criar `security/CurrentUser.java` (`@RequestScoped`, injeta `JsonWebToken` internamente,
  expõe `UUID getId()` via `UUID.fromString(jwt.getSubject())`). Services injetam
  `CurrentUser` em vez de receber `userId` como parâmetro solto em toda assinatura — mantém
  a regra de autorização ("só dono edita/deleta") dentro da camada de Service (BO), não
  espalhada pelo Resource. Guarda defensiva: se `getId()` for chamado num contexto sem
  identidade (endpoint esqueceu `@Authenticated`), lançar erro claro em vez de deixar
  `UUID.fromString(null)` estourar NPE genérico.
- **Ponto que já nos mordeu uma vez em Sprint 1 (revisão final) e não pode se repetir aqui:**
  falha de autenticação não passa pelo nosso `ApiExceptionMapper` (que só trata `ApiException`
  explícito) — cairia no fallback genérico 500 em vez do formato `{"error":{...}}` do projeto.
  Mesma causa raiz do bug de validação corrigido no fim de Sprint 1. Mas são **três** cenários
  distintos, não dois — confirmado decompilando `quarkus-security` e `quarkus-rest` reais:
  - `io.quarkus.security.UnauthorizedException` — identidade anônima (nenhum header
    `Authorization` presente) → mapper próprio, 401.
  - `io.quarkus.security.AuthenticationFailedException` — token presente mas inválido
    (assinatura errada, expirado, malformado) → **mapper separado**, 401. Precisa de
    `@Provider @Priority(Priorities.AUTHENTICATION)` explícito (não só `@Provider` como nos
    outros mappers) — esse tipo de mapper tem histórico documentado de ser silenciosamente
    ignorado em versões antigas do Quarkus por rodar em camada anterior à resolução de
    exception mapper do JAX-RS; a prioridade explícita é o que garante que ele participe.
  - `io.quarkus.security.ForbiddenException` — 403 (não deve disparar nesta sprint, já que
    não há `@RolesAllowed` com role real, mas o mapper entra por completude/futuro).
- Prova de conceito: a primeira tarefa de autenticação sai com teste de integração cobrindo
  **três** casos separados (não dois): sem token → 401 formato certo; token válido → 200;
  token expirado **e** token malformado como dois casos distintos (ambos passam por
  `AuthenticationFailedException`, mas testar os dois separadamente confirma que nenhum dos
  dois escapa por um caminho diferente) → 401 formato certo. Não assumir que "configurar a
  property" é suficiente — provar contra o app real.

### Decisão 2 — Modelo de propriedade (exercícios vs. rotinas)

Princípio geral: se a existência do item já é conhecida publicamente, 403 é honesto; se não é,
404 esconde (evita confirmar existência de recurso privado de terceiro). Mesmo padrão que
APIs de repositório privado usam (GitHub, etc.).

- **Exercícios** — duas sub-regras, não uma (assimetria encontrada na revisão do design: um
  exercício custom de **outro usuário** não é listado pra mim via `GET /exercises`
  — `WHERE owner_id IS NULL OR owner_id = :currentUserId` — logo sua existência não é
  conhecida por mim, exatamente a condição que justifica 404 nas rotinas; 403 ali vazaria a
  existência de recurso privado de terceiro):
  - Editar/deletar exercício **global** (`owner_id IS NULL`) → **403** (`NOT_EXERCISE_OWNER`)
    — existência já é pública/listada pra todos.
  - Editar/deletar exercício custom **de outro usuário** → **404** — mesmo raciocínio de
    rotinas, não distinguível de "não existe".
- **Rotinas**: sempre privadas ao dono, sem catálogo compartilhado. `GET /routines` só lista
  as do usuário atual. Pedir uma rotina de outro usuário por ID (`GET/PUT/DELETE /routines/{id}`)
  → **404**, não 403.

### Decisão 3 — Conflito de exclusão (aprendendo com o TOCTOU de Sprint 1)

`routine_exercises` referencia `exercises` por FK. Se um usuário tenta deletar um exercício
custom seu que está em uso numa rotina, a FK (`ON DELETE RESTRICT`, padrão) rejeita no banco.
Em vez de deixar isso estourar como 500 genérico (mesma classe de bug do finding de TOCTOU em
`AuthService.register`, deixado como backlog em Sprint 1), `ExerciseService.delete` já nasce
tratando esse conflito e traduzindo pra `ApiException("EXERCISE_IN_USE", ..., CONFLICT)` (409).
Checar referências antes de deletar (query prévia) reintroduziria o mesmo TOCTOU do backlog
de Sprint 1 — pior aqui, porque um insert concorrente entre o check e o delete corromperia
integridade referencial silenciosamente, não só geraria um 500 feio. Capturar no boundary do
banco é o padrão certo. Dois detalhes de implementação que decidem se isso funciona de verdade:
- **Timing do flush**: dentro de método `@Transactional`, `repository.delete(entity)` agenda
  uma remoção gerenciada pelo Hibernate que só executa (e só lançaria a violação de FK) no
  commit — depois que o try/catch do método já retornou. Usar `repository.deleteById(id)`
  (compila pra `DELETE ... executeUpdate()` imediato, não removal gerenciada) ou chamar
  `.flush()` logo após o `.delete(entity)` dentro do try, pra violação de FK estourar de forma
  síncrona onde dá pra capturar.
- **Tipo de exceção certo**: capturar `org.hibernate.exception.ConstraintViolationException`
  — atenção, esse nome simples colide com `jakarta.validation.ConstraintViolationException`
  (já importado em outro lugar do projeto pra Bean Validation); são classes diferentes, fácil
  importar a errada. Preferir checar `getSQLState() == "23503"` (violação de FK no Postgres)
  em vez de comparar nome de constraint por string (frágil contra rename de migration).

### Decisão 4 — Rotina como agregado (routine + routine_exercises)

`POST /routines` cria a rotina **e** a lista de exercícios associados numa única requisição
(`{name, description, exercises: [{exerciseId, plannedSets, plannedReps, plannedLoadKg}]}`) —
UX de "montar a rotina inteira numa tela" bate com o que Sprint 5 (frontend) vai construir.
`order_index` é **derivado no servidor** pela posição no array recebido (não aceito como campo
solto do cliente) — reduz superfície de input inválido (índices duplicados/fora de ordem).
`PUT /routines/{id}` substitui nome/descrição e **recria a lista inteira** de
`routine_exercises` (delete-and-reinsert) em vez de PATCH incremental — mais simples e
correto pra MVP, custo aceitável pro tamanho de uma rotina de treino.
Regra de validação: uma rotina precisa de pelo menos 1 exercício (`@NotEmpty` na lista).

**Como modelar o relacionamento — decisão importante, evitar coleção JPA cascateada.** Nada
no código atual usa `@OneToMany`/cascade (`RefreshTokenEntity`→`UserEntity` é modelado do lado
filho, via `@ManyToOne`, gerenciado pelo próprio repository — não há coleção no lado pai). Não
introduzir isso agora: "limpar e re-adicionar" numa `List` com `cascade=ALL, orphanRemoval=true`
é uma pegadinha conhecida do Hibernate — a ordem de flush padrão processa inserts antes de
deletes, então recriar as linhas pode violar transitoriamente uma constraint única tipo
`(routine_id, order_index)` antes das linhas antigas serem de fato removidas, mesmo com o
código aplicativo fazendo delete-antes-de-insert na ordem "certa". Em vez disso, seguir o
padrão já estabelecido: `RoutineExerciseEntity` é entidade própria com seu próprio repository
(`RoutineExerciseRepository`), sem mapeamento de coleção em `RoutineEntity`.
`RoutineService.update` faz explicitamente
`routineExerciseRepository.delete("routine.id = ?1", routineId)` seguido de `persist()` pra
cada linha nova, dentro do mesmo método `@Transactional` — evita cascade/orphanRemoval e a
armadilha de ordem de flush inteiramente.

### Decisão 5 — `muscle_groups`: endpoint extra não previsto no design original

O design original lista `/exercises` como única rota da sprint, mas o frontend (Sprint 5)
precisa saber quais grupos musculares existem pra popular o formulário de criar exercício.
Adição pequena e justificada: `GET /muscle-groups` (somente leitura, lista fixa, não
autenticado — é referência pública, não dado de usuário). Sinalizando aqui com transparência
por ser algo fora da tabela de API original, não uma decisão silenciosa.

### Migrations novas (V3 a V6, seguindo convenção 1 tabela por arquivo)

- `V3__create_muscle_groups.sql` — tabela + seed fixo (10 grupos: Peito, Costas, Pernas,
  Ombros, Bíceps, Tríceps, Core, Glúteos, Panturrilha, Cardio/Outro).
- `V4__create_exercises.sql` — `id UUID PK`, `name`, `muscle_group_id FK`, `owner_id FK
  nullable → users(id)`, `created_at`. Índice em `owner_id` e `muscle_group_id`.
- `V5__create_routines.sql` — `id UUID PK`, `user_id FK NOT NULL`, `name`, `description
  nullable`, `created_at`. Índice em `user_id`.
- `V6__create_routine_exercises.sql` — `id UUID PK`, `routine_id FK` (`ON DELETE CASCADE`),
  `exercise_id FK` (`ON DELETE RESTRICT`, padrão), `order_index`, `planned_sets`,
  `planned_reps`, `planned_load_kg nullable`. Índice em `routine_id`.

### Testes

Mesma filosofia de Sprint 1: unitário só onde há lógica pura (praticamente nenhuma aqui —
Sprint 2 é CRUD + regra de autorização, que só se prova de verdade contra Postgres real).
Integração via Testcontainers (`ExerciseResourceIT`, `RoutineResourceIT`), reusando o idioma
já estabelecido em `AuthResourceIT` (registrar+logar pra obter token, `System.nanoTime()`
pra dados únicos, `given()...then().statusCode(...).body("error.code", equalTo(...))`).
Casos a cobrir: visibilidade global+custom, enforcement de dono (403/404 conforme Decisão 2),
conflito de exclusão (409), validação de payload, fluxo completo de rotina com exercícios
ordenados.

### Tasks (nível de detalhe: implementação completa vem depois, via writing-plans)

1. **Autenticação JWT** — corrigir `application.properties` (`smallrye.jwt.verify.secretkey` +
   `smallrye.jwt.verify.algorithm=HS256`), `CurrentUser` bean, `@Authenticated` em ação, três
   mappers (`UnauthorizedException`, `AuthenticationFailedException` com `@Priority`,
   `ForbiddenException`), IT de prova de conceito (sem token / válido / expirado / malformado).
2. **`muscle_groups`** — migration V3 + seed, entity, repository, `GET /muscle-groups`.
3. **`exercises` (schema)** — migration V4, entity, repository.
4. **`exercises` (service + resource)** — DTOs, `ExerciseService` (list/create/update/delete
   com Decisões 2 e 3), `ExerciseResource` com OpenAPI.
5. **`ExerciseResourceIT`** — cobertura completa.
6. **`routines` + `routine_exercises` (schema)** — migrations V5+V6, entities, repositories.
7. **Rotinas (service + resource)** — DTOs, `RoutineService` (Decisão 4), `RoutineResource`
   com OpenAPI.
8. **`RoutineResourceIT`** — cobertura completa.
9. **Wrap-up** — `mvn verify` completo, atualizar checklist de sprint no README, atualizar
   OpenAPI tags.
