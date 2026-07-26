# Sprint 4 — API Polish + Seed — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar os gaps de documentação OpenAPI nos 7 resources de domínio e construir uma
conta demo pública (seed determinístico de ~4 meses de histórico de treino, incluindo 1
exercício com platô proposital) pra recrutadores testarem o projeto sem cadastrar nada.

**Architecture:** Nenhuma tabela nova. Um bean CDI (`DemoSeeder`) observa `StartupEvent`,
gated por config property, e — só na primeira vez (idempotência por email) — persiste user +
catálogo global de 6 exercícios (owner_id null, primeira vez que esse conceito é realmente
populado no projeto) + 2 rotinas + 48 sessões de treino via os mesmos repositories/entities já
existentes. A geração de dado (datas, progressão de carga, platô) é isolada numa classe pura
(`DemoDataGenerator`, sem CDI/banco) pra ser 100% testável com JUnit puro, mesmo padrão dos
calculators de Sprint 3.

**Tech Stack:** Java 21, Quarkus 3.37.3, Hibernate ORM + Panache Repository, Flyway (sem
migration nova nesta sprint), JUnit 5 + Testcontainers/RestAssured.

## Global Constraints

- `JAVA_HOME` deve apontar pra `C:\tools\jdk-21.0.11.10-hotspot` (`/c/tools/jdk-21.0.11.10-hotspot`
  em bash) em todo comando Maven — o `java` do PATH resolve pra Java 17 via `javapath`.
- Todo timestamp é `Instant` (nunca `LocalDateTime`/`ZonedDateTime` em entity/DTO), toda coluna
  de data é `TIMESTAMPTZ`, tudo em UTC — ver `TIMEZONE.md`.
- `BigDecimal` em todo valor monetário/de carga — nunca `double`/`float`, nunca `==`/`.equals()`
  pra comparação (usar `.compareTo()`), arredondamento só no passo final salvo indicação
  contrária.
- Jackson serializa em `snake_case` globalmente; campos com dígito colado a letra
  (`estimated1rmEpley` etc.) precisam de `@JsonProperty` explícito — não se aplica a nenhum DTO
  novo desta sprint, mas vale re-checar se algum for criado.
- Nunca dar `git push` — o usuário revisa e aprova antes.
- Branch já criada: `feature/sprint4-api-polish-seed`, a partir de `develop` em `a4d61af`.

---

### Task 1: Auditoria e fechamento de gaps de OpenAPI

**Files:**
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/ExerciseResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/RoutineResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/WorkoutSessionResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/DashboardResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/MuscleGroupResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/UserResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/GreetingResource.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/dto/SessionSetRequest.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/dto/RegisterRequest.java`

**Contexto (levantamento já feito, não precisa re-auditar do zero):** os 7 resources de domínio
já têm `@Tag` (sem duplicata) e `@Operation(summary=...)`/`@APIResponse` em todo método. O que
falta, especificamente, listado abaixo por arquivo — aplique exatamente essas mudanças, sem
inventar novas (isso não é uma auditoria aberta, é uma lista fechada de gaps já identificados).

**Regra geral repetida em todo método `@Authenticated`:** falta `@APIResponse(responseCode =
"401", description = "Token ausente, inválido ou expirado")`. **Regra geral repetida em todo
método que recebe `@Valid` body:** falta `@APIResponse(responseCode = "400", description =
"Payload inválido (erro de validação de campo)")` cobrindo o `VALIDATION_ERROR` genérico do
`ConstraintViolationExceptionMapper` (distinto dos 400 de domínio tipo
`INVALID_EXERCISE_REFERENCE`, que já estão documentados — adicionar o de validação como
`@APIResponse` extra, não substituir os existentes).

- [ ] **Step 1: `AuthResource.java`** — adicionar `description` em cada `@Operation` e o 400 de
  validação nos 3 métodos com `@Valid` body (`register`, `login`, `refresh`, `logout` — os 4
  têm `@Valid`).

```java
    @POST
    @Path("/register")
    @Operation(summary = "Cria uma nova conta de usuário",
            description = "Endpoint público. Faz hash da senha (BCrypt) e já emite o par de tokens (access + refresh) no corpo da resposta.")
    @APIResponse(responseCode = "201", description = "Conta criada, tokens emitidos")
    @APIResponse(responseCode = "400", description = "Payload inválido (email malformado, senha curta, nome vazio)")
    @APIResponse(responseCode = "409", description = "Email já cadastrado")
    public Response register(@Valid RegisterRequest request) {
```

```java
    @POST
    @Path("/login")
    @Operation(summary = "Autentica um usuário existente",
            description = "Endpoint público. Verifica a senha via BCrypt e emite um novo par de tokens.")
    @APIResponse(responseCode = "200", description = "Login bem-sucedido, tokens emitidos")
    @APIResponse(responseCode = "400", description = "Payload inválido (email ou senha ausentes)")
    @APIResponse(responseCode = "401", description = "Credenciais inválidas")
    public Response login(@Valid LoginRequest request) {
```

```java
    @POST
    @Path("/refresh")
    @Operation(summary = "Rotaciona o refresh token e emite novo access token",
            description = "Endpoint público (a autenticação aqui é o próprio refresh token no corpo, não um Bearer header). O refresh token usado é revogado e um novo par é emitido.")
    @APIResponse(responseCode = "200", description = "Novo par de tokens emitido")
    @APIResponse(responseCode = "400", description = "Payload inválido (refresh token ausente)")
    @APIResponse(responseCode = "401", description = "Refresh token inválido, expirado ou já revogado")
    public Response refresh(@Valid RefreshRequest request) {
```

```java
    @POST
    @Path("/logout")
    @Operation(summary = "Revoga o refresh token, encerrando a sessão",
            description = "Endpoint público. Idempotente: revogar um token já revogado ou inexistente também retorna 204.")
    @APIResponse(responseCode = "204", description = "Sessão encerrada")
    @APIResponse(responseCode = "400", description = "Payload inválido (refresh token ausente)")
    public Response logout(@Valid RefreshRequest request) {
```

- [ ] **Step 2: `ExerciseResource.java`** — adicionar `description`, 401 nos 4 métodos, 400 de
  validação em `create`/`update`.

```java
    @GET
    @Operation(summary = "Lista exercícios visíveis ao usuário (catálogo global + custom próprios)",
            description = "Retorna todo exercício com owner nulo (catálogo global) mais os exercícios custom criados pelo próprio usuário autenticado.")
    @APIResponse(responseCode = "200", description = "Lista de exercícios")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response list() {
```

```java
    @POST
    @Operation(summary = "Cria um exercício custom pertencente ao usuário autenticado",
            description = "O exercício criado tem owner igual ao usuário autenticado — nunca entra no catálogo global.")
    @APIResponse(responseCode = "201", description = "Exercício criado")
    @APIResponse(responseCode = "400", description = "Payload inválido ou grupo muscular inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response create(@Valid ExerciseRequest request) {
```

```java
    @PUT
    @Path("/{id}")
    @Operation(summary = "Atualiza um exercício custom do usuário autenticado",
            description = "Só o dono do exercício custom pode editar. Exercícios do catálogo global (owner nulo) retornam 403.")
    @APIResponse(responseCode = "200", description = "Exercício atualizado")
    @APIResponse(responseCode = "400", description = "Payload inválido ou grupo muscular inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser editado")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid ExerciseRequest request) {
```

```java
    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove um exercício custom do usuário autenticado",
            description = "Só o dono do exercício custom pode remover. Falha com 409 se o exercício estiver referenciado em alguma rotina ou sessão de treino registrada.")
    @APIResponse(responseCode = "204", description = "Exercício removido")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser removido")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Exercício está em uso (em rotinas ou sessões de treino registradas)")
    public Response delete(@PathParam("id") UUID id) {
```

- [ ] **Step 3: `RoutineResource.java`** — adicionar `description`, 401 nos 5 métodos, 400 de
  validação em `create`/`update`.

```java
    @GET
    @Operation(summary = "Lista as rotinas do usuário autenticado",
            description = "Retorna só as rotinas do próprio usuário — rotinas não têm catálogo compartilhado.")
    @APIResponse(responseCode = "200", description = "Lista de rotinas (resumo)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response list() {
```

```java
    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma rotina, incluindo exercícios ordenados",
            description = "Rotina de outro usuário sempre retorna 404 (nunca 403), já que não existe catálogo compartilhado de rotinas.")
    @APIResponse(responseCode = "200", description = "Detalhe da rotina")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
```

```java
    @POST
    @Operation(summary = "Cria uma rotina com seus exercícios ordenados",
            description = "A ordem dos exercícios (order_index) é derivada da posição no array recebido — não é um campo aceito no payload.")
    @APIResponse(responseCode = "201", description = "Rotina criada")
    @APIResponse(responseCode = "400", description = "Payload inválido ou exercício inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response create(@Valid RoutineRequest request) {
```

```java
    @PUT
    @Path("/{id}")
    @Operation(summary = "Substitui nome/descrição e recria a lista de exercícios da rotina",
            description = "A lista de exercícios inteira é substituída (delete-and-reinsert), não é um PATCH incremental.")
    @APIResponse(responseCode = "200", description = "Rotina atualizada")
    @APIResponse(responseCode = "400", description = "Payload inválido ou exercício inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid RoutineRequest request) {
```

```java
    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove uma rotina do usuário autenticado",
            description = "Sessões de treino que referenciavam essa rotina não são afetadas — o vínculo (routine_id) só é anulado (ON DELETE SET NULL), o histórico da sessão permanece.")
    @APIResponse(responseCode = "204", description = "Rotina removida")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response delete(@PathParam("id") UUID id) {
```

- [ ] **Step 4: `WorkoutSessionResource.java`** — adicionar `description`, 401 nos 5 métodos, 400
  de validação em `create`/`finish`/`addSet`.

```java
    @GET
    @Operation(summary = "Lista as sessões de treino do usuário autenticado",
            description = "Cada item traz set_count (total de séries já registradas), sem o detalhe de cada série — use GET /{id} para o detalhe completo.")
    @APIResponse(responseCode = "200", description = "Lista de sessões (resumo)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response list() {
```

```java
    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma sessão, incluindo séries registradas",
            description = "Sessão de outro usuário sempre retorna 404 (nunca 403) — sem catálogo compartilhado de sessões.")
    @APIResponse(responseCode = "200", description = "Detalhe da sessão")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
```

```java
    @POST
    @Operation(summary = "Inicia uma nova sessão de treino",
            description = "Só é permitida 1 sessão ativa (finished_at nulo) por usuário por vez. routine_id é opcional — se informado, precisa pertencer ao usuário atual.")
    @APIResponse(responseCode = "201", description = "Sessão iniciada")
    @APIResponse(responseCode = "400", description = "Referência de rotina inválida ou payload malformado")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "409", description = "Já existe uma sessão ativa")
    public Response create(@Valid WorkoutSessionRequest request) {
```

```java
    @PATCH
    @Path("/{id}")
    @Operation(summary = "Finaliza uma sessão de treino",
            description = "Seta finished_at = now() no servidor. Não é possível finalizar uma sessão já finalizada nem adicionar séries depois de finalizada.")
    @APIResponse(responseCode = "200", description = "Sessão finalizada")
    @APIResponse(responseCode = "400", description = "Payload malformado")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já estava finalizada")
    public Response finish(@PathParam("id") UUID id, @Valid FinishWorkoutSessionRequest request) {
```

```java
    @POST
    @Path("/{id}/sets")
    @Operation(summary = "Registra uma série executada na sessão",
            description = "set_number é calculado no servidor (posição entre as séries já registradas daquele exercício naquela sessão, reinicia por exercício) — não é aceito como campo do payload. O 1RM estimado (Epley/Brzycki/melhor) é calculado e persistido no insert.")
    @APIResponse(responseCode = "201", description = "Série registrada")
    @APIResponse(responseCode = "400", description = "Referência de exercício inválida ou payload inválido")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já finalizada")
    public Response addSet(@PathParam("id") UUID id, @Valid SessionSetRequest request) {
```

- [ ] **Step 5: `DashboardResource.java`** — adicionar `description`, 401 nos 3 métodos.

```java
    @GET
    @Path("/progression/{exerciseId}")
    @Operation(summary = "Série temporal de 1RM estimado para um exercício",
            description = "1 ponto por sessão em que o exercício foi executado, valor = maior estimated_1rm_best entre as séries daquela sessão para esse exercício, em ordem cronológica. Exercício sem nenhuma série registrada retorna 200 com lista vazia (não é erro).")
    @APIResponse(responseCode = "200", description = "Pontos de progressão (pode ser lista vazia)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou não visível para este usuário")
    public Response progression(@PathParam("exerciseId") UUID exerciseId) {
```

```java
    @GET
    @Path("/volume")
    @Operation(summary = "Volume de treino agregado por grupo muscular e semana",
            description = "volume = soma de (reps × peso) de todas as séries, agrupado por grupo muscular e por semana ISO (segunda-feira 00:00 UTC), sem paginação — cobre todo o histórico do usuário atual.")
    @APIResponse(responseCode = "200", description = "Buckets de volume semanal")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response volume() {
```

```java
    @GET
    @Path("/plateaus")
    @Operation(summary = "Alertas de platô ativos (exercícios estagnados)",
            description = "Retorna só os exercícios com platô ativo (3+ sessões consecutivas sem novo recorde de 1RM, streak terminando na sessão mais recente, mínimo de 4 sessões no histórico). Exercícios sem platô ou com histórico insuficiente não aparecem na lista.")
    @APIResponse(responseCode = "200", description = "Lista de alertas ativos")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response plateaus() {
```

- [ ] **Step 6: `MuscleGroupResource.java`** — adicionar `description` (não é `@Authenticated`,
  não precisa de 401).

```java
    @GET
    @Operation(summary = "Lista todos os grupos musculares",
            description = "Endpoint público, sem autenticação. Lista fixa (10 grupos, seed via migration V3), usada pra popular formulários de criação de exercício.")
    @APIResponse(responseCode = "200", description = "Lista de grupos musculares")
    public Response list() {
```

- [ ] **Step 7: `UserResource.java`** — adicionar `description` (401 já existia).

```java
    @GET
    @Path("/me")
    @Operation(summary = "Retorna o perfil do usuário autenticado",
            description = "Deriva o usuário do subject (sub) do JWT — não aceita nem precisa de nenhum parâmetro de identificação.")
    @APIResponse(responseCode = "200", description = "Perfil do usuário")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response me() {
```

- [ ] **Step 8: `GreetingResource.java`** — esconder do Swagger (Decisão 5 do design: não é
  um dos 4 grupos de domínio, é health check de infra).

```java
package com.rsinelli.gymtracker.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/ping")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(hidden = true)
    public String ping() {
        return "pong";
    }
}
```

- [ ] **Step 9: `@Schema` nos campos de validação implícita não-óbvia** — `SessionSetRequest.rpe`
  (regra 1-10 com meio-ponto) e `RegisterRequest.password` (tamanho mínimo) não deixam a regra
  óbvia só pelo tipo. Adicionar `@Schema` com `description`:

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record SessionSetRequest(
        @NotNull UUID exerciseId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weightKg,
        @Min(1) int reps,
        @DecimalMin(value = "1") @DecimalMax(value = "10")
        @Schema(description = "Escala de esforço percebido (RPE), 1 a 10, aceita meio-ponto (ex. 7.5). Opcional.")
        BigDecimal rpe) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Mínimo 8 caracteres, máximo 72 (limite do BCrypt).")
        String password,
        @NotBlank @Size(max = 120) String name) {
}
```

- [ ] **Step 10: Rodar os testes de integração existentes** (garantir que nenhuma mudança de
  anotação quebrou nada — são só annotations, sem lógica nova, mas confirma que o projeto ainda
  compila e os ~63 testes continuam verdes).

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify
```
Expected: `BUILD SUCCESS`, mesma contagem de testes de antes (nenhum teste novo nesta task).

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/resource backend/src/main/java/com/rsinelli/gymtracker/dto/SessionSetRequest.java backend/src/main/java/com/rsinelli/gymtracker/dto/RegisterRequest.java
git commit -m "docs(backend): fechar gaps de completude OpenAPI nos 7 resources de domínio"
```

---

### Task 2: `DemoDataGenerator` — geração pura e determinística de histórico de treino

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/DemoDataGenerator.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/GeneratedSession.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/GeneratedSet.java`
- Test: `backend/src/test/java/com/rsinelli/gymtracker/unit/DemoDataGeneratorTest.java`

**Interfaces:**
- Produces: `DemoDataGenerator.generate(Instant anchorNow): List<GeneratedSession>`,
  `GeneratedSession(Instant startedAt, Instant finishedAt, List<GeneratedSet> sets)`,
  `GeneratedSet(int exerciseIndex, BigDecimal weightKg, int reps, BigDecimal rpe /* nullable */)`,
  constantes públicas `DemoDataGenerator.EXERCISE_NAMES` (String[6]) e
  `DemoDataGenerator.EXERCISE_MUSCLE_GROUPS` (String[6], nomes batendo com
  `V3__create_muscle_groups.sql`: "Peito", "Pernas", "Costas", "Ombros", "Bíceps"),
  `DemoDataGenerator.PLATEAU_EXERCISE_INDEX = 4`. Consumidos pela Task 3 (`DemoSeeder`).

**Algoritmo (fixado aqui, não é decisão do implementador):**
- 16 semanas terminando na semana de `anchorNow`, 3 sessões por semana (segunda/quarta/sexta),
  6 exercícios em 2 grupos de 3: Grupo A = índices `{0,1,2}` (Supino Reto/Peito, Agachamento
  Livre/Pernas, Levantamento Terra/Costas), Grupo B = índices `{3,4,5}` (Desenvolvimento
  Militar/Ombros, Rosca Direta/Bíceps **[exercício do platô]**, Puxada Alta/Costas).
- Escala de treino por semana alterna: semana par (`weekIndex % 2 == 0`) treina
  `[A, B, A]` (segunda/quarta/sexta), semana ímpar treina `[B, A, B]` — cada grupo acumula
  exatamente 24 ocorrências ao longo das 16 semanas (3 sessões/semana × 16 semanas × metade
  em cada grupo, com o desbalanceamento de uma sessão a mais/menos por semana se cancelando
  entre semanas pares/ímpares).
- 1 `java.util.Random` com seed fixo `424242L`, criado uma vez no início de `generate(...)` e
  consumido em ordem estritamente determinística (sessão por sessão, cronologicamente; dentro
  de cada sessão, exercício por exercício na ordem do grupo; dentro de cada exercício, todos os
  sets daquele exercício antes de passar pro próximo) — garante mesma seed → mesma saída.
- Carga por exercício `e`, na ocorrência `k` (0-indexed, própria daquele exercício — não da
  sessão global): `peso(k) = baseWeight[e] + trend(k) + noise(k)`, onde
  `trend(k) = floor(k/2) * 2.5` (tendência de alta, +2.5kg a cada 2 sessões) e
  `noise(k) = (rng.nextInt(5) - 2) * 1.25` (ruído em `{-2.5, -1.25, 0, 1.25, 2.5}`, nunca mais
  que 1 passo de anilha de 2.5kg pra qualquer lado). `baseWeight = {40, 60, 70, 30, 20, 45}`
  (kg, por índice de exercício 0-5).
- Reps por exercício `e`, ocorrência `k`: `reps(k) = baseReps[e] + (rng.nextInt(5) - 2)`,
  clampado a `[6, 10]`. `baseReps = {8, 8, 6, 8, 10, 8}` (Levantamento Terra usa reps-base mais
  baixo, padrão comum pra levantamento composto pesado).
- **Platô (exercício índice 4, Rosca Direta):** para `k >= 19` (as últimas 5 das 24 ocorrências
  totais), **não** aplicar `trend`/`noise` normais — `peso(k)` e `reps(k)` ficam fixos, igual ao
  valor já calculado em `k = 18` (a última ocorrência "progredindo"). Isso é intencional e
  crítico: como as fórmulas de Epley/Brzycki crescem com peso e com reps, qualquer variação
  positiva de peso OU reps nessas 5 últimas ocorrências contaria como "novo recorde" e quebraria
  o platô que o dashboard precisa mostrar. `rpe`, que não entra em nenhuma fórmula de 1RM,
  continua variando normalmente nessas 5 ocorrências pra não parecer 100% idêntico.
- `rpe`: 70% de chance de estar presente (`rng.nextDouble() < 0.7`); quando presente,
  `6.5 + rng.nextInt(7) * 0.5` (passos de 0.5, de 6.5 a 9.5).
- 3 a 4 sets por exercício por sessão: `3 + rng.nextInt(2)`.
- Horário da sessão: hora-base por dia da semana (`19:00` UTC segunda/quarta,
  `19:30` UTC sexta) + jitter de `rng.nextInt(91) - 45` minutos (±45min). Duração:
  `finishedAt = startedAt.plusSeconds(3000 + rng.nextInt(1200))` (50 a 70 minutos).

- [ ] **Step 1: Criar os records de saída**

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GeneratedSession(Instant startedAt, Instant finishedAt, List<GeneratedSet> sets) {
}
```

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;

public record GeneratedSet(int exerciseIndex, BigDecimal weightKg, int reps, BigDecimal rpe) {
}
```

- [ ] **Step 2: Escrever o teste que falha primeiro**

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.DemoDataGenerator;
import com.rsinelli.gymtracker.service.GeneratedSession;
import com.rsinelli.gymtracker.service.GeneratedSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataGeneratorTest {

    private static final Instant ANCHOR = Instant.parse("2026-07-25T12:00:00Z");

    private final DemoDataGenerator generator = new DemoDataGenerator();

    @Test
    void generatesFortyEightSessionsAcrossSixteenWeeks() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        assertEquals(48, sessions.size());
    }

    @Test
    void sessionsAreInChronologicalOrder() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        for (int i = 1; i < sessions.size(); i++) {
            assertTrue(sessions.get(i).startedAt().isAfter(sessions.get(i - 1).startedAt()),
                    "sessão " + i + " deveria ser depois da sessão " + (i - 1));
        }
    }

    @Test
    void eachExerciseAppearsInTwentyFourSessions() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);
        Map<Integer, Integer> occurrencesByExercise = new HashMap<>();

        for (GeneratedSession session : sessions) {
            for (int exerciseIndex : distinctExerciseIndexes(session)) {
                occurrencesByExercise.merge(exerciseIndex, 1, Integer::sum);
            }
        }

        for (int e = 0; e < 6; e++) {
            assertEquals(24, occurrencesByExercise.getOrDefault(e, 0), "exercício " + e);
        }
    }

    @Test
    void plateauExerciseHasIdenticalWeightAndRepsInLastFiveOccurrences() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);
        List<GeneratedSet> plateauOccurrences = firstSetPerOccurrence(sessions, DemoDataGenerator.PLATEAU_EXERCISE_INDEX);

        assertEquals(24, plateauOccurrences.size());
        List<GeneratedSet> lastFive = plateauOccurrences.subList(19, 24);
        BigDecimal frozenWeight = lastFive.get(0).weightKg();
        int frozenReps = lastFive.get(0).reps();

        for (GeneratedSet occurrence : lastFive) {
            assertEquals(0, frozenWeight.compareTo(occurrence.weightKg()), "peso deveria estar congelado");
            assertEquals(frozenReps, occurrence.reps(), "reps deveriam estar congeladas");
        }
    }

    @Test
    void nonPlateauExercisesTrendUpwardOverTime() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        for (int e = 0; e < 6; e++) {
            if (e == DemoDataGenerator.PLATEAU_EXERCISE_INDEX) {
                continue;
            }
            List<GeneratedSet> occurrences = firstSetPerOccurrence(sessions, e);
            double avgFirstFour = occurrences.subList(0, 4).stream()
                    .mapToDouble(s -> s.weightKg().doubleValue()).average().orElseThrow();
            double avgLastFour = occurrences.subList(occurrences.size() - 4, occurrences.size()).stream()
                    .mapToDouble(s -> s.weightKg().doubleValue()).average().orElseThrow();

            assertTrue(avgLastFour > avgFirstFour, "exercício " + e + " deveria progredir: primeiras=" + avgFirstFour + " últimas=" + avgLastFour);
        }
    }

    @Test
    void generationIsDeterministic() {
        List<GeneratedSession> first = generator.generate(ANCHOR);
        List<GeneratedSession> second = generator.generate(ANCHOR);

        assertEquals(first, second);
    }

    private static List<Integer> distinctExerciseIndexes(GeneratedSession session) {
        List<Integer> result = new ArrayList<>();
        for (GeneratedSet set : session.sets()) {
            if (!result.contains(set.exerciseIndex())) {
                result.add(set.exerciseIndex());
            }
        }
        return result;
    }

    /** 1 elemento por sessão em que o exercício apareceu — pega o primeiro set daquela sessão pra esse exercício. */
    private static List<GeneratedSet> firstSetPerOccurrence(List<GeneratedSession> sessions, int exerciseIndex) {
        List<GeneratedSet> result = new ArrayList<>();
        for (GeneratedSession session : sessions) {
            session.sets().stream()
                    .filter(s -> s.exerciseIndex() == exerciseIndex)
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }
}
```

- [ ] **Step 3: Rodar o teste pra confirmar que falha**

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test -Dtest=DemoDataGeneratorTest
```
Expected: FAIL — `DemoDataGenerator` não existe ainda (erro de compilação).

- [ ] **Step 4: Implementar `DemoDataGenerator`**

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DemoDataGenerator {

    public static final String[] EXERCISE_NAMES = {
            "Supino Reto", "Agachamento Livre", "Levantamento Terra",
            "Desenvolvimento Militar", "Rosca Direta", "Puxada Alta"
    };

    public static final String[] EXERCISE_MUSCLE_GROUPS = {
            "Peito", "Pernas", "Costas", "Ombros", "Bíceps", "Costas"
    };

    public static final int PLATEAU_EXERCISE_INDEX = 4;

    private static final long SEED = 424242L;
    private static final int WEEKS = 16;
    private static final int[] GROUP_A = {0, 1, 2};
    private static final int[] GROUP_B = {3, 4, 5};
    private static final double[] BASE_WEIGHT_KG = {40, 60, 70, 30, 20, 45};
    private static final int[] BASE_REPS = {8, 8, 6, 8, 10, 8};
    private static final int PLATEAU_FROZEN_FROM_OCCURRENCE = 19;

    public List<GeneratedSession> generate(Instant anchorNow) {
        Random rng = new Random(SEED);
        int[] occurrenceIndexByExercise = new int[6];
        BigDecimal[] frozenWeightByExercise = new BigDecimal[6];
        int[] frozenRepsByExercise = new int[6];

        LocalDate today = anchorNow.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate firstMonday = today.minusWeeks(WEEKS).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        List<GeneratedSession> sessions = new ArrayList<>();
        for (int week = 0; week < WEEKS; week++) {
            LocalDate monday = firstMonday.plusWeeks(week);
            int[][] slots = week % 2 == 0
                    ? new int[][]{GROUP_A, GROUP_B, GROUP_A}
                    : new int[][]{GROUP_B, GROUP_A, GROUP_B};
            LocalDate[] days = {monday, monday.plusDays(2), monday.plusDays(4)};
            int[] baseHour = {19, 19, 19};
            int[] baseMinute = {0, 0, 30};

            for (int slot = 0; slot < 3; slot++) {
                Instant startedAt = days[slot]
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusHours(baseHour[slot])
                        .plusMinutes(baseMinute[slot])
                        .toInstant()
                        .plusSeconds((rng.nextInt(91) - 45) * 60L);
                Instant finishedAt = startedAt.plusSeconds(3000 + rng.nextInt(1200));

                List<GeneratedSet> sets = new ArrayList<>();
                for (int exerciseIndex : slots[slot]) {
                    int occurrence = occurrenceIndexByExercise[exerciseIndex]++;
                    boolean frozen = exerciseIndex == PLATEAU_EXERCISE_INDEX && occurrence >= PLATEAU_FROZEN_FROM_OCCURRENCE;

                    BigDecimal weightKg;
                    int reps;
                    if (frozen) {
                        weightKg = frozenWeightByExercise[exerciseIndex];
                        reps = frozenRepsByExercise[exerciseIndex];
                    } else {
                        weightKg = weightForOccurrence(exerciseIndex, occurrence, rng);
                        reps = repsForOccurrence(exerciseIndex, rng);
                        if (exerciseIndex == PLATEAU_EXERCISE_INDEX && occurrence == PLATEAU_FROZEN_FROM_OCCURRENCE - 1) {
                            frozenWeightByExercise[exerciseIndex] = weightKg;
                            frozenRepsByExercise[exerciseIndex] = reps;
                        }
                    }

                    int setCount = 3 + rng.nextInt(2);
                    for (int s = 0; s < setCount; s++) {
                        BigDecimal rpe = rng.nextDouble() < 0.7
                                ? BigDecimal.valueOf(6.5 + rng.nextInt(7) * 0.5).setScale(1, RoundingMode.HALF_UP)
                                : null;
                        sets.add(new GeneratedSet(exerciseIndex, weightKg, reps, rpe));
                    }
                }

                sessions.add(new GeneratedSession(startedAt, finishedAt, sets));
            }
        }

        return sessions;
    }

    private BigDecimal weightForOccurrence(int exerciseIndex, int occurrence, Random rng) {
        BigDecimal trend = BigDecimal.valueOf((occurrence / 2) * 2.5);
        BigDecimal noise = BigDecimal.valueOf((rng.nextInt(5) - 2) * 1.25);
        return BigDecimal.valueOf(BASE_WEIGHT_KG[exerciseIndex])
                .add(trend)
                .add(noise)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int repsForOccurrence(int exerciseIndex, Random rng) {
        int reps = BASE_REPS[exerciseIndex] + (rng.nextInt(5) - 2);
        return Math.max(6, Math.min(10, reps));
    }
}
```

- [ ] **Step 5: Rodar o teste pra confirmar que passa**

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test -Dtest=DemoDataGeneratorTest
```
Expected: PASS, 6/6 testes.

**Nota se `nonPlateauExercisesTrendUpwardOverTime` falhar por causa do ruído:** a margem entre
tendência (+2.5kg a cada 2 ocorrências, ~27.5kg acumulado em 24 ocorrências) e ruído
(±2.5kg por ocorrência isolada) é grande o suficiente pra essa asserção passar de forma
determinística com a seed fixa `424242L` — se falhar, o bug está na implementação (ex. trend
não sendo aplicado), não no teste; não ajustar a asserção pra "consertar", investigar a causa.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/service/DemoDataGenerator.java backend/src/main/java/com/rsinelli/gymtracker/service/GeneratedSession.java backend/src/main/java/com/rsinelli/gymtracker/service/GeneratedSet.java backend/src/test/java/com/rsinelli/gymtracker/unit/DemoDataGeneratorTest.java
git commit -m "feat(backend): adicionar DemoDataGenerator com progressão determinística e platô proposital"
```

---

### Task 3: `DemoSeeder` — persistência da conta demo no boot

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/seed/DemoSeeder.java`
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/repository/MuscleGroupRepository.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Consome: `DemoDataGenerator.generate(Instant): List<GeneratedSession>` (Task 2),
  `AuthService.register(String, String, String): AuthResponse` (existente),
  `OneRepMaxCalculator.calculate(BigDecimal, int): OneRepMaxResult` (existente, Sprint 3),
  `UserRepository.findByEmail(String): Optional<UserEntity>` (existente).
- Produz: `DemoSeeder.DEMO_EMAIL`, `DemoSeeder.DEMO_PASSWORD`, `DemoSeeder.DEMO_NAME` (constantes
  `public static final`, consumidas pela Task 4 e citadas no README na Task 5).

**Decisão crítica confirmada nesta task (corrige uma suposição do design que não se sustenta no
código real — sinalizando com transparência, mesmo espírito das correções de Sprint 2/3):**
`RoutineService` e `WorkoutSessionService` injetam `CurrentUser`, que por sua vez é
`@RequestScoped` e lê o `sub` do JWT da requisição HTTP atual. Um observer de `StartupEvent` não
roda dentro de nenhum request HTTP — não existe JWT, não existe request scope ativo. Chamar
qualquer método desses dois services a partir do seeder lançaria
`ContextNotActiveException` (RequestScoped inativo) antes mesmo de chegar no
`IllegalStateException` que `CurrentUser.getId()` já lança pra esse cenário. Por isso o
`DemoSeeder` **não injeta `RoutineService` nem `WorkoutSessionService`** — persiste
`RoutineEntity`/`RoutineExerciseEntity`/`WorkoutSessionEntity`/`SessionSetEntity` diretamente via
repository, exatamente como `DashboardResourceIT`/`ExerciseResourceIT` já fazem em teste (mesmo
idioma, ambiente diferente). `AuthService` continua seguro de usar — não depende de
`CurrentUser`.

**Segunda decisão que corrige uma suposição do design:** o catálogo global de exercícios
(`owner_id IS NULL`) nunca foi populado por nenhuma sprint anterior — `ExerciseService.create`
sempre seta `owner = currentUser`, não existe hoje nenhum caminho de código que crie um
exercício com `owner_id` nulo. O `DemoSeeder` é, na prática, o primeiro código do projeto a
cumprir a promessa original de design ("catálogo global, sem dono") — os 6 exercícios da demo
são criados com `owner = null`, persistidos direto via `ExerciseRepository` (não via
`ExerciseService`, que não tem um caminho pra criar exercício sem dono).

- [ ] **Step 1: Adicionar `findByName` em `MuscleGroupRepository`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MuscleGroupRepository implements PanacheRepositoryBase<MuscleGroupEntity, UUID> {

    public List<MuscleGroupEntity> listAllOrderedByName() {
        return list("ORDER BY name");
    }

    public Optional<MuscleGroupEntity> findByName(String name) {
        return find("name", name).firstResultOptional();
    }
}
```

- [ ] **Step 2: Adicionar a config property em `application.properties`**

Adicionar ao final do arquivo:

```properties

# Seed da conta demo pública (ver docs/superpowers/plans/2026-07-25-sprint4-api-polish-seed.md).
# Roda uma única vez no boot, idempotente por email — desligado por padrão, nunca liga sozinho
# em dev/test/CI. Em produção (Render), setar GYMTRACKER_SEED_DEMO=true uma vez.
gymtracker.seed.demo.enabled=${GYMTRACKER_SEED_DEMO:false}
```

- [ ] **Step 3: Implementar `DemoSeeder`**

```java
package com.rsinelli.gymtracker.seed;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import com.rsinelli.gymtracker.repository.RoutineExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.service.AuthService;
import com.rsinelli.gymtracker.service.DemoDataGenerator;
import com.rsinelli.gymtracker.service.GeneratedSession;
import com.rsinelli.gymtracker.service.GeneratedSet;
import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DemoSeeder {

    public static final String DEMO_EMAIL = "demo@gymtracker.app";
    public static final String DEMO_PASSWORD = "DemoGymTracker2026!";
    public static final String DEMO_NAME = "Conta Demo";

    private static final Logger LOG = Logger.getLogger(DemoSeeder.class);

    @ConfigProperty(name = "gymtracker.seed.demo.enabled", defaultValue = "false")
    boolean seedEnabled;

    @Inject
    UserRepository userRepository;

    @Inject
    AuthService authService;

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    RoutineRepository routineRepository;

    @Inject
    RoutineExerciseRepository routineExerciseRepository;

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    OneRepMaxCalculator oneRepMaxCalculator;

    @Inject
    DemoDataGenerator demoDataGenerator;

    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled) {
            return;
        }
        if (userRepository.findByEmail(DEMO_EMAIL).isPresent()) {
            LOG.info("Conta demo já existe, seed ignorado.");
            return;
        }
        seed();
        LOG.infof("Conta demo semeada com sucesso: %s", DEMO_EMAIL);
    }

    @Transactional
    void seed() {
        authService.register(DEMO_EMAIL, DEMO_PASSWORD, DEMO_NAME);
        UserEntity demoUser = userRepository.findByEmail(DEMO_EMAIL).orElseThrow();

        List<ExerciseEntity> exercises = createGlobalExercises();
        createDemoRoutines(demoUser, exercises);
        seedWorkoutHistory(demoUser, exercises);
    }

    private List<ExerciseEntity> createGlobalExercises() {
        List<ExerciseEntity> exercises = new ArrayList<>();
        for (int i = 0; i < DemoDataGenerator.EXERCISE_NAMES.length; i++) {
            MuscleGroupEntity muscleGroup = muscleGroupRepository.findByName(DemoDataGenerator.EXERCISE_MUSCLE_GROUPS[i])
                    .orElseThrow(() -> new IllegalStateException(
                            "Grupo muscular '" + DemoDataGenerator.EXERCISE_MUSCLE_GROUPS[i] + "' não encontrado — a migration V3 rodou?"));

            ExerciseEntity exercise = new ExerciseEntity();
            exercise.setName(DemoDataGenerator.EXERCISE_NAMES[i]);
            exercise.setMuscleGroup(muscleGroup);
            exercise.setOwner(null);
            exerciseRepository.persist(exercise);
            exercises.add(exercise);
        }
        return exercises;
    }

    private void createDemoRoutines(UserEntity owner, List<ExerciseEntity> exercises) {
        createRoutine(owner, "Treino A — Peito/Pernas/Costas",
                "Treino de força, foco em grandes compostos.",
                List.of(exercises.get(0), exercises.get(1), exercises.get(2)));
        createRoutine(owner, "Treino B — Ombros/Bíceps/Costas",
                "Treino complementar, foco em membros superiores.",
                List.of(exercises.get(3), exercises.get(4), exercises.get(5)));
    }

    private void createRoutine(UserEntity owner, String name, String description, List<ExerciseEntity> items) {
        RoutineEntity routine = new RoutineEntity();
        routine.setUser(owner);
        routine.setName(name);
        routine.setDescription(description);
        routineRepository.persist(routine);

        for (int i = 0; i < items.size(); i++) {
            RoutineExerciseEntity routineExercise = new RoutineExerciseEntity();
            routineExercise.setRoutine(routine);
            routineExercise.setExercise(items.get(i));
            routineExercise.setOrderIndex(i);
            routineExercise.setPlannedSets(4);
            routineExercise.setPlannedReps(8);
            routineExercise.setPlannedLoadKg(null);
            routineExerciseRepository.persist(routineExercise);
        }
    }

    private void seedWorkoutHistory(UserEntity demoUser, List<ExerciseEntity> exercises) {
        List<GeneratedSession> generatedSessions = demoDataGenerator.generate(Instant.now());

        for (GeneratedSession generatedSession : generatedSessions) {
            WorkoutSessionEntity session = new WorkoutSessionEntity();
            session.setUser(demoUser);
            session.setStartedAt(generatedSession.startedAt());
            session.setFinishedAt(generatedSession.finishedAt());
            workoutSessionRepository.persist(session);

            Map<Integer, Integer> setNumberByExercise = new HashMap<>();
            for (GeneratedSet generatedSet : generatedSession.sets()) {
                ExerciseEntity exercise = exercises.get(generatedSet.exerciseIndex());
                int setNumber = setNumberByExercise.merge(generatedSet.exerciseIndex(), 1, Integer::sum);
                OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(generatedSet.weightKg(), generatedSet.reps());

                SessionSetEntity set = new SessionSetEntity();
                set.setSession(session);
                set.setExercise(exercise);
                set.setSetNumber(setNumber);
                set.setWeightKg(generatedSet.weightKg());
                set.setReps(generatedSet.reps());
                set.setRpe(generatedSet.rpe());
                set.setEstimated1rmEpley(oneRepMax.epley());
                set.setEstimated1rmBrzycki(oneRepMax.brzycki());
                set.setEstimated1rmBest(oneRepMax.best());
                sessionSetRepository.persist(set);
            }
        }
    }
}
```

- [ ] **Step 4: Verificar que o projeto compila e os testes existentes continuam verdes** (a
  config property default é `false`, então nenhum teste existente deve ser afetado — o seeder
  fica inerte em todo o resto da suíte).

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify
```
Expected: `BUILD SUCCESS`, mesma contagem de testes de antes (a IT do seeder é a Task 4).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/seed/DemoSeeder.java backend/src/main/java/com/rsinelli/gymtracker/repository/MuscleGroupRepository.java backend/src/main/resources/application.properties
git commit -m "feat(backend): adicionar DemoSeeder — popula conta demo no boot via StartupEvent"
```

---

### Task 4: `DemoSeederIT` — cobertura de integração do seed

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/DemoSeederIT.java`

**Interfaces:**
- Consome: `DemoSeeder.DEMO_EMAIL`, `DemoSeeder.DEMO_PASSWORD` (Task 3),
  `PostgresTestResource` (existente, `backend/src/test/java/com/rsinelli/gymtracker/integration/PostgresTestResource.java`).

**Padrão novo neste projeto:** nenhuma classe de teste usa `@TestProfile` ainda — as ~14 classes
de IT existentes compartilham a config default (`gymtracker.seed.demo.enabled=false`, herdado do
`application.properties`). Só esta classe precisa da seed ligada, então usa
`io.quarkus.test.junit.QuarkusTestProfile` pra sobrescrever a property **só nessa classe** — não
mexer no `application.properties` principal nem criar um `application.properties` de teste
(afetaria as outras IT classes). Isso força essa classe a subir seu próprio contexto Quarkus
(mais lento que as outras, que compartilham contexto) — é o comportamento esperado do
`@TestProfile`, não um bug.

- [ ] **Step 1: Criar `DemoSeederIT.java`**

```java
package com.rsinelli.gymtracker.integration;

import com.rsinelli.gymtracker.seed.DemoSeeder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(DemoSeederIT.EnableDemoSeedProfile.class)
class DemoSeederIT {

    public static class EnableDemoSeedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gymtracker.seed.demo.enabled", "true");
        }
    }

    @Test
    void demoAccountCanLoginWithDocumentedCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(DemoSeeder.DEMO_EMAIL, DemoSeeder.DEMO_PASSWORD))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("access_token", not(equalTo(null)));
    }

    @Test
    void demoAccountHasPopulatedDashboard() {
        String token = loginAsDemo();

        String plateauExerciseId = given()
                .header("Authorization", "Bearer " + token)
                .when().get("/exercises")
                .then().statusCode(200)
                .extract().path("find { it.name == 'Rosca Direta' }.id");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/plateaus")
                .then().statusCode(200)
                .body("exercise_id", hasItem(plateauExerciseId));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + plateauExerciseId)
                .then().statusCode(200)
                .body("points.size()", greaterThanOrEqualTo(24));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/volume")
                .then().statusCode(200)
                .body("size()", greaterThan(0));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/routines")
                .then().statusCode(200)
                .body("size()", equalTo(2));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/workout-sessions")
                .then().statusCode(200)
                .body("size()", equalTo(48));
    }

    private String loginAsDemo() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(DemoSeeder.DEMO_EMAIL, DemoSeeder.DEMO_PASSWORD))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("access_token");
    }
}
```

**Nota sobre idempotência:** a idempotência do `DemoSeeder` (não duplicar em restart) já é
coberta estruturalmente pelo teste `demoAccountHasPopulatedDashboard` — ele assume exatamente
48 sessões e 2 rotinas; `@QuarkusTest` reusa o mesmo contexto de aplicação (logo o mesmo boot,
logo uma única chamada a `onStart`) entre os métodos de teste dessa classe, então uma segunda
execução acidental do seeder dentro da mesma suíte já quebraria essas asserções de contagem
exata — não precisa de um teste dedicado chamando o bean duas vezes manualmente.

- [ ] **Step 2: Rodar `./mvnw verify`, confirmar passa, commit**

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify
```
Expected: `BUILD SUCCESS`, incluindo `DemoSeederIT` (2 novos testes de integração).

```bash
git add backend/src/test/java/com/rsinelli/gymtracker/integration/DemoSeederIT.java
git commit -m "test(backend): cobrir DemoSeeder via DemoSeederIT com TestProfile dedicado"
```

---

### Task 5: README — seção "Conta demo" + documentação da env var

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Adicionar a seção "Conta demo" logo após "Como rodar localmente"**

```markdown
## Conta demo

O projeto inclui uma conta pública com histórico de treino já populado (16 semanas, 48
sessões, 6 exercícios em 5 grupos musculares, incluindo 1 exercício com platô proposital) —
pra testar o app sem precisar cadastrar nada.

**Credenciais** (fixas, propositalmente públicas — essa conta não guarda nenhum dado
sensível, é 100% sintética; ver decisão de design em
`docs/superpowers/plans/2026-07-25-sprint4-api-polish-seed.md`):

- Email: `demo@gymtracker.app`
- Senha: `DemoGymTracker2026!`

O que você vai ver logando com essa conta:
- **Progressão de 1RM**: gráfico crescente em 5 dos 6 exercícios ao longo de ~4 meses.
- **Volume semanal**: agregado por grupo muscular, toda a janela de 16 semanas.
- **Alerta de platô**: "Rosca Direta" (Bíceps) mostra platô ativo — carga travada nas últimas
  5 sessões, feature de detecção funcionando de ponta a ponta.
- 2 rotinas pré-montadas ("Treino A" e "Treino B").

**Ligar a seed** (desligada por padrão — nunca roda sozinha em dev/test/CI): setar a env var
`GYMTRACKER_SEED_DEMO=true` antes de subir a aplicação. Roda uma única vez no boot
(idempotente — checa se a conta já existe antes de semear de novo). Localmente:

```bash
GYMTRACKER_SEED_DEMO=true ./mvnw quarkus:dev      # Windows: set GYMTRACKER_SEED_DEMO=true && mvnw.cmd quarkus:dev
```
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: adicionar seção de conta demo com credenciais e instruções da seed"
```

---

### Task 6: Wrap-up

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Atualizar o checklist de status no README**

Trocar:
```markdown
- [ ] Sprint 4 — API polish + Seed
```
por:
```markdown
- [x] Sprint 4 — API polish + Seed
```

- [ ] **Step 2: Rodar a suíte completa**

```bash
cd backend
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify
```
Expected: `BUILD SUCCESS` nos dois — unitários (incluindo `DemoDataGeneratorTest`, 6 testes
novos) e integração (incluindo `DemoSeederIT`, 2 testes novos; total de IT classes = 15).

- [ ] **Step 3: Revisão manual do `openapi.yaml` gerado**

```bash
JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw package -DskipTests -Dquarkus.swagger-ui.always-include=true
java -jar target/quarkus-app/quarkus-run.jar &
sleep 5
curl -s http://localhost:8080/q/openapi | grep -c "^  /"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/q/swagger-ui/
kill %1
```
Expected: o `grep -c` mostra as rotas de domínio (não deve incluir `/ping`, que agora está
`hidden`); o `curl` do swagger-ui retorna `200`. Confirmar visualmente (abrir
`http://localhost:8080/q/swagger-ui` num navegador, se disponível) que nenhum dos 4 grupos
(`Auth`, `Exercises`/`Routines`, `WorkoutSessions`, `Dashboard`) tem operação sem descrição.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: marcar Sprint 4 completa no status do README"
```

---

## Verificação (fim a fim, pós Task 6)

- `cd backend && ./mvnw test` — verde, incluindo `DemoDataGeneratorTest` (6 testes).
- `cd backend && ./mvnw verify` — verde, incluindo `DemoSeederIT` (2 testes), Testcontainers.
- Manual: `GYMTRACKER_SEED_DEMO=true` + `quarkus:dev`, login com `demo@gymtracker.app` /
  `DemoGymTracker2026!`, `GET /dashboard/plateaus` mostra "Rosca Direta", `GET /routines` mostra
  2 rotinas, `GET /workout-sessions` mostra 48 sessões. Reiniciar o processo com a mesma env var
  ligada e confirmar que os números não dobram (idempotência).
- `/q/swagger-ui` acessível, sem `/ping` na lista, todos os grupos com `description` em cada
  operação.

## Self-Review Checklist (rodar antes de iniciar a execução)

- [x] **Cobertura do design** — cada Decisão do design de Sprint 4 mapeia pra uma task: Decisão 1
  (StartupEvent + config gate + idempotência) → Task 3; Decisão 2 (realismo da progressão,
  platô) → Task 2; Decisão 3 (credenciais fixas no README) → Task 5; Decisão 4 (auditoria
  OpenAPI) → Task 1; Decisão 5 (`GreetingResource` hidden) → Task 1 Step 8.
- [x] **Sem placeholder** — nenhum "TBD"/"similar to Task N"/instrução sem código; os dois gaps
  de design que não se sustentavam no código real (`RoutineService`/`WorkoutSessionService`
  dependerem de `CurrentUser` RequestScoped; catálogo global de exercícios nunca ter sido
  populado antes) foram resolvidos explicitamente na Task 3, não deixados em aberto.
- [x] **Consistência de tipo/nome entre tasks** — `DemoDataGenerator.generate(Instant):
  List<GeneratedSession>` (Task 2) bate com o uso em `DemoSeeder.seedWorkoutHistory` (Task 3);
  `GeneratedSession`/`GeneratedSet` (Task 2) batem com os campos lidos em `DemoSeeder` (Task 3);
  `DemoSeeder.DEMO_EMAIL`/`DEMO_PASSWORD` (Task 3) batem com o uso em `DemoSeederIT` (Task 4) e
  com o texto exato documentado na Task 5; `MuscleGroupRepository.findByName` (Task 3 Step 1)
  bate com o uso em `DemoSeeder.createGlobalExercises` (Task 3 Step 3).
