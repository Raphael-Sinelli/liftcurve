# gym-progress-tracker — Sprint 3 (Sessões + Domínio Core) — Design

## Status do projeto

Sprint 0-2 estão **completas e mergeadas em `develop`** (Sprint 0-1: branch
`feature/sprint0-1-fundacoes-auth`, 13 commits; Sprint 2: branch
`feature/sprint2-exercicios-rotinas`, revisão de branch inteira feita, 1 finding corrigido
no fim + 1 fix pontual pós-merge no `ApiExceptionMapper`, ver
`docs/superpowers/specs/2026-07-24-sprint2-exercicios-rotinas-design.md`). O design original
completo (Sprint 0-7) continua em
`docs/superpowers/specs/2026-07-23-gym-progress-tracker-design.md` — este documento cobre
só a **Sprint 3**.

Backlog técnico aberto (não bloqueia Sprint 3, mas relevante — ver Decisão 8 abaixo):
`docs/superpowers/notes/technical-backlog.md` documenta `quarkus:dev` quebrado (Postgres Dev
Services crasha por conflito de versão do `testcontainers` vazando pro classpath de
augmentation do Quarkus). Workaround atual: rodar o jar empacotado em vez de dev mode.

**Recomendação sobre granularidade do plano:** mantida — 1 branch + 1 plano + 1 revisão de
branch inteira por sprint. Este documento cobre **só Sprint 3**.

---

## Sprint 3 — Design Detalhado

### Contexto desta sprint

Sprint 3 é o **diferencial central do projeto**: registro de sessão de treino (séries reais,
não planejadas) e a camada de domínio que transforma esses dados brutos em insight —
1RM estimado, volume por grupo muscular/semana, detecção de platô. Diferente de Sprint 2
(CRUD + autorização), aqui a maior parte do risco está em regra de negócio pura (fórmulas,
janelas de agregação, máquina de estados de sessão), não em infraestrutura. Reaproveita
integralmente os padrões já estabelecidos: `CurrentUser`, `ApiException`/`ApiExceptionMapper`,
Panache Repository, DTOs em record, `@Authenticated` a nível de classe, idioma de teste de
integração (Testcontainers + RestAssured + `registerAndGetToken` duplicado por classe).

Duas decisões do usuário já confirmadas nesta rodada de brainstorming (não reabrir):
- **Sessão ativa**: só 1 por vez — `POST /workout-sessions` com sessão aberta → 409.
- **`set_number`**: contador por exercício dentro da sessão (Set 1, Set 2... reinicia a
  cada exercício), não um contador único da sessão inteira.

### Decisão 1 — Ciclo de vida de sessão (`workout_sessions`)

`POST /workout-sessions` cria a sessão com `started_at = now()` (servidor, UTC) e
`finished_at = null`. Corpo: `{routine_id: nullable, notes: nullable}`.

- **Regra "só 1 sessão ativa"**: antes de criar, `WorkoutSessionService` checa se o usuário
  já tem uma sessão com `finished_at IS NULL`. Se sim → `ApiException("SESSION_ALREADY_ACTIVE",
  ..., CONFLICT)` (409), mesmo padrão de `EXERCISE_IN_USE`. Motivo: reflete uso real (você tá
  treinando ou não) e dá uma regra de domínio de verdade, não só CRUD. Igual ao 409 de
  exclusão de Sprint 2, checar-antes-de-criar aqui não tem problema de TOCTOU sério (pior
  caso: dois cliques rápidos criam 2 sessões — impacto baixo, não é dado financeiro/de
  integridade referencial; não justifica lock/constraint de banco adicional pra isso).
- **Referência a rotina**: se `routine_id` vier, precisa ser uma rotina visível ao usuário
  (dono) — reusa `RoutineService`-style de "sempre 404 se não for dono" pro *lookup* direto,
  mas aqui é uma referência dentro de um payload (mesma situação de `exercise_id` dentro de
  `POST /routines` em Sprint 2) → se inválida, **400** `INVALID_ROUTINE_REFERENCE`, não 404
  (404 é reservado pro path param do recurso principal, não pra referência embutida — mesma
  distinção já usada em `INVALID_EXERCISE_REFERENCE`).
- **`PATCH /workout-sessions/{id}`** finaliza a sessão: corpo `{notes: nullable}`, servidor
  seta `finished_at = now()`. Se a sessão já estiver finalizada → 409
  `SESSION_ALREADY_FINISHED`. Sessão sempre 404 se não for do usuário atual (sem catálogo
  compartilhado — mesmo raciocínio de rotinas).
- **`POST /workout-sessions/{id}/sets`** adiciona 1 série à sessão (ver Decisão 2). Se a
  sessão já estiver finalizada → mesmo 409 `SESSION_ALREADY_FINISHED`.
- **Sem DELETE** de sessão nesta sprint — não está na tabela de API original e sessão é
  registro histórico (deletar quebraria dashboard/gráficos). Fora de escopo deliberadamente.
- **FK `routine_id` → `ON DELETE SET NULL`** (não CASCADE, não RESTRICT): se a rotina-base
  for deletada depois, a sessão (fato histórico: "eu treinei isso, possivelmente baseado
  numa rotina que não existe mais") continua existindo, só perde o vínculo. CASCADE
  apagaria histórico de treino por causa da exclusão de um template — errado. RESTRICT
  travaria a exclusão de rotina pra sempre depois do primeiro treino registrado — também
  errado, muito mais restritivo que o necessário.

### Decisão 2 — Registro de séries (`session_sets`) é incremental, não agregado

Diferente de Sprint 2 (rotina cria a lista inteira de exercícios numa única requisição),
aqui cada série é adicionada uma de cada vez via `POST /{id}/sets` — reflete o uso real
(quem treina não sabe todas as séries de antemão, registra à medida que faz). Corpo:
`{exercise_id, weight_kg, reps, rpe: nullable}`. Resposta: o `SessionSetResponse` criado
(201), não a sessão inteira.

- **`exercise_id`** precisa ser visível ao usuário (global ou custom próprio) — reusa
  `ExerciseRepository.findVisibleTo` já existente (Sprint 2). Inválido → 400
  `INVALID_EXERCISE_REFERENCE` (mesmo código já usado em rotinas — é o mesmo tipo de erro:
  referência de exercício inválida dentro de um payload).
- **`set_number`**: calculado no servidor = posição entre as séries já registradas *daquele
  exercício* *naquela sessão*, +1 (não aceito como campo do cliente — mesmo princípio de
  `order_index` derivado em Sprint 2).
- **Validação**: `weight_kg` (`BigDecimal`, `@DecimalMin("0")` inclusive — permite 0 pra
  exercício de peso corporal puro), `reps` (`int`, `@Min(1)` — sem teto, ver Decisão 3 pro
  motivo), `rpe` (`BigDecimal` nullable, `@DecimalMin("1")` `@DecimalMax("10")` — `BigDecimal`
  em vez de `int` porque RPE na prática usa meio-ponto, ex. 7.5/8.5, escala de
  Tuchscherer/RPE-based training; decisão de tipo, não reabre a escala 1-10 já validada).
- **`GET /workout-sessions/{id}`** retorna a sessão com a lista de séries aninhada
  (`sets: [...]`), mesmo padrão de `RoutineResponse.exercises`.
- **`GET /workout-sessions`** lista resumida (id, started_at, finished_at, routine_id
  nullable, set_count) — mesmo padrão de `RoutineSummaryResponse` vs `RoutineResponse`.

### Decisão 3 — `OneRepMaxCalculator` (unitário, sem banco)

Confirma exatamente as fórmulas do design original:
- Epley: `1RM = peso × (1 + reps/30)`
- Brzycki: `1RM = peso × 36 / (37 - reps)`
- `estimated_1rm_best = max(epley, brzycki)`

**Correção/esclarecimento sobre o schema original, encontrada nesta rodada de design** (mesmo
espírito da correção do timing de flush em Sprint 2 — sinalizando com transparência, não
decisão silenciosa): o DER original lista `estimated_1rm_brzycki` sem marcar como nullable,
mas Brzycki **é matematicamente indefinido pra `reps >= 37`** (denominador `37 - reps <= 0`).
A "guard clause" pedida no escopo só faz sentido se a coluna aceitar null nesse caso — do
contrário o guard clause não tem onde gravar o resultado. Decisão:
`session_sets.estimated_1rm_brzycki` é **`NUMERIC` nullable**; quando `reps >= 37`, o
calculator não computa Brzycki (retorna `null` nesse campo) e `estimated_1rm_best = epley`
(o único valor disponível). `estimated_1rm_epley` continua sempre `NOT NULL` (Epley é
definido pra qualquer `reps >= 1`, e a validação de entrada já garante `reps >= 1`).

- Contrato: `record OneRepMaxResult(BigDecimal epley, BigDecimal brzycki, BigDecimal best)`
  — `brzycki` pode ser `null`. Entrada: `BigDecimal weightKg`, `int reps`. Resultado
  arredondado em escala 2 (`RoundingMode.HALF_UP`), mesma escala de `planned_load_kg` — evita
  ruído de ponto flutuante em teste e consistência de precisão de peso no sistema inteiro.
- **`reps = 1`**: sem tratamento especial, ambas as fórmulas já convergem pra ~peso
  (Epley: `peso × 1.033`; Brzycki: `peso × 36/36 = peso`) — só um caso de teste, não um
  guard clause de código.
- **`reps >= 37`**: guard clause explícito, `brzycki = null`, `best = epley`. Testar
  exatamente `reps = 37` (limite) e um valor bem acima (ex. `reps = 50`).
  **`reps <= 0` não é responsabilidade do calculator** — a validação de entrada (`@Min(1)`
  no DTO) já impede isso antes de chegar aqui; o calculator recebe sempre `reps >= 1` como
  invariante, não precisa de guard clause redundante pra isso.
- **`reps > 12`**: sem guard clause — calcula normalmente, mesmo sendo uma estimativa menos
  precisa na literatura pra reps altas. Teste confirma que não lança erro e devolve valor
  plausível (não zero, não negativo).
- Antes de escrever qualquer linha, confirmar (via teste, não assunção) que `BigDecimal`
  com `MathContext`/`RoundingMode` explícito não introduz divergência entre Epley e Brzycki
  por erro de arredondamento intermediário — dividir só na última operação de cada fórmula.

### Decisão 4 — `VolumeCalculator` (unitário, sem banco)

`volume = Σ(reps × peso)` por `muscle_group` e por semana (UTC).

**Esclarecimento sobre a fórmula original** (`Σ(sets × reps × peso)`): como cada linha de
`session_sets` já representa **uma única série executada** (não um bloco planejado tipo
"3 séries de 10"), o termo "sets" da fórmula original é a **contagem de linhas somadas**, não
um multiplicador extra por linha — cada linha já contribui `reps × peso` uma vez, e "volume"
é a soma dessas contribuições. Multiplicar de novo por "número de séries" duplicaria o
volume. Esclarecendo aqui porque a fórmula do design original, lida ao pé da letra numa
única linha, seria ambígua.

- **Agrupamento por semana**: usa `started_at` **da sessão** pra todas as séries daquela
  sessão (não existe timestamp por série — só a sessão tem `started_at`/`finished_at`).
  Início de semana calculado em Java (`java.time`, convenção ISO — segunda-feira 00:00 UTC),
  não `date_trunc('week', ...)` do Postgres, porque o calculator é puro/sem banco por
  requisito explícito — a leitura crua vem do repository, a lógica de bucket é 100% testável
  sem Postgres.
  - **Teste "sessão cruzando virada de semana"**: uma sessão que começa sábado à noite e
    termina domingo de madrugada (UTC) — todas as suas séries caem no bucket da semana de
    `started_at`, mesmo que `finished_at` já seja da semana seguinte. Confirma que o
    calculator não quebra nem duplica/perde volume nesse cruzamento.
- **"Exercício sem grupo muscular linkado"**: no schema atual (`V4__create_exercises.sql`),
  `muscle_group_id` é `NOT NULL` — não existe hoje um exercício sem grupo. O caso de teste do
  design original não é reproduzível via schema real agora. Decisão: manter o calculator
  **defensivo** mesmo assim — contrato de entrada aceita `muscleGroupId` nullable, e um valor
  `null` cai num bucket próprio ("sem categoria") em vez de lançar exceção. Custo de código é
  mínimo (um `Objects.requireNonNullElse` numa chave de agrupamento), cobre o teste pedido
  no escopo, e protege contra qualquer evolução futura do schema sem re-projetar o calculator.
- Contrato: entrada = `List<SetVolumeInput(BigDecimal weightKg, int reps, UUID muscleGroupId
  /* nullable */, Instant sessionStartedAt)>`; saída = `Map<VolumeBucketKey, BigDecimal>` onde
  `VolumeBucketKey(UUID muscleGroupId /* nullable */, Instant weekStartUtc)`.

### Decisão 5 — `PlateauDetectionService` (unitário, sem banco)

Entrada: histórico cronológico (mais antigo → mais recente) de **1 valor por sessão** —
o `estimated_1rm_best` **máximo entre as séries daquele exercício naquela sessão** (redução
de várias séries pra 1 valor por sessão acontece no Service, em Java, antes de chamar o
calculator — não é responsabilidade do calculator agregar séries).

**Algoritmo (esclarecendo a regra "3 sessões consecutivas sem novo 1RM" com precisão
operacional — o design original descreve o resultado, não o algoritmo)**: varredura
cronológica mantendo um "recorde corrente" (`runningMax`) e uma sequência de sessões
consecutivas sem novo recorde (`streak`), terminando na sessão mais recente:
- Primeira sessão sempre inicializa `runningMax` (não conta pra streak).
- Cada sessão seguinte: se `1RM da sessão > runningMax` → **novo recorde**, `runningMax`
  atualiza, `streak` reseta pra 0. Se `1RM da sessão <= runningMax` (empate não conta como
  recorde novo) → `streak += 1`.
- Ao fim: se total de sessões `< 4` → `INSUFFICIENT_DATA` (independente do streak). Senão,
  se `streak >= 3` (streak final, terminando na sessão mais atual) → `PLATEAU_DETECTED`.
  Senão → `NO_PLATEAU`.

Essa formulação bate com todos os 4 casos de teste pedidos:
- **3 sessões estagnadas dispara**: `[100, 100, 100, 100]` → sessão 1 = baseline, sessões
  2-3-4 sem recorde novo → streak final = 3, total = 4 → `PLATEAU_DETECTED`.
- **2 não dispara**: streak final = 2 (ex. penúltima sessão ainda empatada, mas não chega a
  3) → `NO_PLATEAU` (dado que já há `>= 4` sessões no histórico).
- **PR no meio reseta a contagem**: qualquer sessão com 1RM > `runningMax` zera o streak
  imediatamente, mesmo que as sessões anteriores já tivessem streak alto — é justamente o
  motivo de rastrear "streak terminando na sessão mais recente", não "existiu streak de 3 em
  algum ponto do histórico" (essa segunda leitura ficaria presa num platô que já foi
  superado — errado pro propósito do endpoint `/dashboard/plateaus`, que lista **alertas
  ativos**, não histórico de platôs passados).
- **Menos de 4 sessões**: `INSUFFICIENT_DATA` sempre, não importa o streak.
- Contrato: `record PlateauResult(PlateauStatus status, BigDecimal currentMax,
  String suggestion /* só quando PLATEAU_DETECTED */)`, `enum PlateauStatus
  {INSUFFICIENT_DATA, NO_PLATEAU, PLATEAU_DETECTED}`. Mensagem de sugestão fixa (constante),
  ex. `"Considere reduzir a carga em ~10% por 1 semana (deload)."` — percentual fixo (10%),
  não calculado sobre a carga real (é orientação textual, não recomendação numérica de peso
  de trabalho — escopo do texto do design original, não expandir).

### Decisão 6 — Endpoints de dashboard (somente leitura, sem persistência própria)

- **`GET /dashboard/progression/{exerciseId}`**: série temporal do `estimated_1rm_best` por
  sessão pro exercício informado. `exerciseId` precisa ser visível ao usuário (mesmo
  `findVisibleTo` de Sprint 2) → inválido/inexistente = 404. Se visível mas o usuário nunca
  registrou série nenhuma desse exercício → **200 com lista vazia** (é uma questão de dado
  disponível, não de autorização — diferente de 404).
- **`GET /dashboard/volume`**: agregado de volume por grupo muscular/semana, **todo o
  histórico do usuário atual**, sem paginação/filtro de data nesta sprint — volume de dados
  de um projeto de portfólio é trivial (no máximo alguns meses de treino simulado), não
  justifica complexidade de paginação agora. Decisão consciente de escopo MVP, não omissão.
- **`GET /dashboard/plateaus`**: roda `PlateauDetectionService` pra cada exercício que o
  usuário já registrou pelo menos 1 série, retorna só os que deram `PLATEAU_DETECTED`.
  Implementação faz 1 query por exercício (N+1) — aceitável pro volume de dados esperado
  (portfólio, não produção em escala); sinalizando a simplificação explicitamente, não como
  descuido.
- Todos os 3 endpoints são escopados ao usuário autenticado via `CurrentUser` — nenhum
  aceita `userId` de outro usuário, nem como admin nem de nenhuma forma (não existe conceito
  de admin no sistema).

### Decisão 7 — Onde vive a "redução de séries pra 1 valor por sessão"

Os calculators (`VolumeCalculator`, `PlateauDetectionService`) são funções puras que recebem
dados já no formato certo — não fazem `GROUP BY` nem sabem o que é uma "sessão" com múltiplas
séries. A ponte fica no Service (`DashboardService`), em Java simples: busca as linhas cruas
via repository (`SessionSetRepository`, queries HQL simples no mesmo idioma Panache já usado
— `list("session.user.id = ?1 and exercise.id = ?2 order by session.startedAt", ...)`), agrupa
por sessão em memória (`Collectors.groupingBy` + `max`), monta a lista/mapa de entrada dos
calculators. Mantém a mesma separação de responsabilidade já usada em Sprint 2 (Repository =
acesso a dado, Service = orquestração + regra que precisa de contexto de banco, calculator/
regra pura = testável isoladamente sem Quarkus/CDI/Postgres).

### Decisão 8 — Backlog técnico (`quarkus:dev`/Testcontainers)

Investigação (não decisão de código ainda — fica como task de implementação com escape
hatch): o `technical-backlog.md` já mapeou a causa raiz (override de `testcontainers` em
`<dependencyManagement>` vazando pro classpath de build/augmentation do Quarkus) e dois
caminhos candidatos de fix real: (a) parar de forçar `testcontainers` pra trás e em vez
disso atualizar `org.testcontainers:postgresql` pra uma versão compatível com o
`testcontainers` `2.0.5` que o `quarkus-bom` já espera, ou (b) excluir `testcontainers`
especificamente da resolução de dependência que o Dev Services processor usa, sem mexer no
que os testes do projeto usam. Nenhuma das duas foi tentada de verdade ainda (só
diagnosticada). Como pedido, entra como task com **time-box explícito**: se resolver, ótimo,
atualiza backlog + README; se não resolver com esforço razoável, mantém documentado pra
Sprint 7 exatamente como já está — não força.

### Migrations novas (V7 e V8)

- `V7__create_workout_sessions.sql` — `id UUID PK`, `user_id UUID FK -> users NOT NULL
  ON DELETE CASCADE`, `routine_id UUID FK -> routines NULLABLE ON DELETE SET NULL` (ver
  Decisão 1), `started_at TIMESTAMPTZ NOT NULL`, `finished_at TIMESTAMPTZ NULLABLE`,
  `notes TEXT NULLABLE`. Índice em `user_id`.
- `V8__create_session_sets.sql` — `id UUID PK`, `session_id UUID FK -> workout_sessions
  NOT NULL ON DELETE CASCADE` (série pertence inteiramente à sessão — mesmo padrão de
  `routine_exercises -> routines`), `exercise_id UUID FK -> exercises NOT NULL` (sem
  cascade — mesmo padrão de `routine_exercises -> exercises`; efeito colateral bom: o
  `ExerciseService.delete` já existente, que captura `SQLState 23503` genericamente e
  devolve 409, automaticamente também protege contra apagar um exercício com histórico de
  treino registrado, sem precisar de código novo), `set_number INT NOT NULL`,
  `weight_kg NUMERIC NOT NULL`, `reps INT NOT NULL`, `rpe NUMERIC NULLABLE`,
  `estimated_1rm_epley NUMERIC NOT NULL`, `estimated_1rm_brzycki NUMERIC NULLABLE` (ver
  Decisão 3), `estimated_1rm_best NUMERIC NOT NULL`, `created_at TIMESTAMPTZ`. Índices em
  `session_id` e `exercise_id`.

### Testes

**Unitário** (`com.rsinelli.gymtracker.unit`, JUnit 5 puro, instanciação direta, sem
`@QuarkusTest` — mesmo padrão de `PasswordHasherTest`/`TokenServiceTest` de Sprint 1):
`OneRepMaxCalculatorTest`, `VolumeCalculatorTest`, `PlateauDetectionServiceTest`. Cobertura
pesada de verdade (pedido explícito) — todos os casos de borda listados nas Decisões 3-5,
não só o caminho feliz. Essa é a parte que mais diferencia o projeto, não pode ficar rasa.

**Integração** (Testcontainers + RestAssured, mesmo idioma de `RoutineResourceIT`):
`WorkoutSessionResourceIT` — ciclo completo (criar sessão → adicionar séries → finalizar),
409 de sessão já ativa, 409 de sessão já finalizada, 400 de referência inválida de
rotina/exercício, 404 de sessão de outro usuário, `set_number` reiniciando por exercício.
`DashboardResourceIT` — pra montar fixtures de múltiplas semanas/sessões precisas (necessário
pros testes de volume por semana e platô), semeia dados diretamente via repository dentro de
`QuarkusTransaction.requiringNew().call(...)`, mesmo padrão já usado em `ExerciseResourceIT`
pra criar exercícios globais — não dá pra controlar `started_at` de sessões passadas só via
API (que sempre usa `now()`).

### Tasks (nível de detalhe: implementação completa vem depois, via writing-plans)

1. **Schema** — migrations V7+V8, `WorkoutSessionEntity`, `SessionSetEntity`,
   `WorkoutSessionRepository`, `SessionSetRepository` (finders necessários pra Decisão 7).
2. **`OneRepMaxCalculator`** — TDD, cobertura pesada (Decisão 3).
3. **`VolumeCalculator`** — TDD, cobertura pesada incl. virada de semana e grupo nulo
   defensivo (Decisão 4).
4. **`PlateauDetectionService`** — TDD, cobertura pesada, os 4 cenários + insuficiente
   (Decisão 5).
5. **Sessões (service + resource)** — DTOs, `WorkoutSessionService` (ciclo de vida, Decisão
   1), `WorkoutSessionResource` com OpenAPI.
6. **Séries (service + resource)** — `POST /{id}/sets` na mesma service/resource de sessão
   ou service dedicado (decidir no plano técnico), cálculo de 1RM no insert (Decisão 2).
7. **`WorkoutSessionResourceIT`** — cobertura completa.
8. **Dashboard (service + resource)** — `DashboardService` (Decisão 7), `DashboardResource`
   com os 3 endpoints (Decisão 6), OpenAPI.
9. **`DashboardResourceIT`** — cobertura completa com fixtures multi-sessão/multi-semana.
10. **Backlog técnico `quarkus:dev`** — investigação time-boxed (Decisão 8): tentar fix real,
    documentar resultado (resolvido ou mantido em backlog) de qualquer forma.
11. **Wrap-up** — `mvn verify` completo, checklist de sprint no README, tags OpenAPI.
