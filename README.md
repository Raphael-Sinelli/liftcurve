# Gym Progress Tracker

Projeto de portfólio: rastreador de progressão de treino com estimativa de 1RM (Epley +
Brzycki), cálculo de volume por grupo muscular e detecção automática de platô.

> Em desenvolvimento. Ver `docs/superpowers/specs/` para o design completo e
> `docs/superpowers/plans/` para os planos de implementação sprint a sprint.

## Stack

- **Backend:** Java 21, Quarkus, PostgreSQL, JWT (access token) + refresh token opaco, Flyway
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS, Recharts
- **Testes:** JUnit 5 (unitário) + Testcontainers/RestAssured (integração) no backend, Vitest no frontend
- **Infra:** Docker Compose, GitHub Actions

## Como rodar localmente

Pré-requisitos: Java 21, Node 20+, Docker Desktop.

```bash
# sobe o Postgres
docker compose up -d

# backend (http://localhost:8080)
cd backend
./mvnw quarkus:dev      # Windows: mvnw.cmd quarkus:dev

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

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

## Testes

```bash
# backend — unitários (sem Docker)
cd backend && ./mvnw test

# backend — integração (precisa de Docker rodando)
cd backend && ./mvnw verify

# frontend
cd frontend && npm run build
```

## Status

- [x] Sprint 0 — Fundações
- [x] Sprint 1 — Schema + Auth
- [x] Sprint 2 — Exercícios + Rotinas
- [x] Sprint 3 — Sessões + Domínio Core (1RM, volume, platô)
- [x] Sprint 4 — API polish + Seed
- [ ] Sprint 5 — Frontend Core
- [ ] Sprint 6 — Testes, CI/CD, Deploy
- [ ] Sprint 7 — README final + Polish
