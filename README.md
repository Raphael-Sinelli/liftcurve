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
- [ ] Sprint 4 — API polish + Seed
- [ ] Sprint 5 — Frontend Core
- [ ] Sprint 6 — Testes, CI/CD, Deploy
- [ ] Sprint 7 — README final + Polish
