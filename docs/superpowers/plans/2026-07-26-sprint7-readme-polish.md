# Sprint 7 — README + Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the project's final polish pass — fix a leftover internal-codename branding
inconsistency ("GPT"/"Gym Progress Tracker" → "LiftCurve"), write the definitive README, and
close out the project.

**Architecture:** No new features, no backend changes. Pure frontend text/asset edits (nav
logo, page title, login heading, favicon, build metadata) plus a full README rewrite. Two
supporting activities — screenshot capture and golden-path QA — happen against the live
production deployment via Playwright, outside the task-review loop (no code diff to review).

**Tech Stack:** React 19 + TypeScript + Vite (frontend only — this sprint touches no Java).

## Global Constraints

- No backend/Java changes in this sprint — `backend/` is untouched.
- Every task that touches `frontend/` must leave `npm run lint && npm run test && npm run build` green.
- Branch: `feature/sprint7-readme-polish`, created off `develop` at commit `b4e1079`.
- Never push to `origin` without the user's separate, explicit approval — same rule as every prior sprint.
- Historical docs (`docs/superpowers/specs/*.md`, `docs/superpowers/plans/*.md` — all files that already existed before this sprint) are NOT edited. Only "live" docs (`README.md`, `docs/DEPLOY.md`) get the name correction.
- Production URLs: frontend `https://liftcurve.vercel.app`, backend `https://liftcurve.onrender.com`. GitHub repo: `Raphael-Sinelli/liftcurve`.
- Demo account credentials (unchanged, do not touch): `demo@gymtracker.app` / `DemoGymTracker2026!`.

---

### Task 1: Identidade visual e consistência de nome

**Files:**
- Modify: `frontend/src/routes/AppLayout.tsx`
- Modify: `frontend/index.html`
- Modify: `frontend/src/features/auth/LoginPage.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/package.json`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/DEPLOY.md`
- Modify: `frontend/public/favicon.svg`

**Interfaces:** None new — pure text/asset edits, nothing consumed or produced across tasks.

- [ ] **Step 1: Fix the nav-rail logo (GPT → LC)**

In `frontend/src/routes/AppLayout.tsx`, line 20, change:

```tsx
          <p className="font-display text-lg font-bold text-ink">GPT</p>
```

to:

```tsx
          <p className="font-display text-lg font-bold text-ink">LC</p>
```

Nothing else in that file changes — same classes, same container, same spacing.

- [ ] **Step 2: Fix the browser tab title**

In `frontend/index.html`, line 7, change:

```html
    <title>frontend</title>
```

to:

```html
    <title>LiftCurve</title>
```

- [ ] **Step 3: Fix the login page heading**

In `frontend/src/features/auth/LoginPage.tsx`, line 55, change:

```tsx
        <h1 className="font-display text-3xl font-bold text-ink">Gym Progress Tracker</h1>
```

to:

```tsx
        <h1 className="font-display text-3xl font-bold text-ink">LiftCurve</h1>
```

- [ ] **Step 4: Update the tests coupled to the old heading text**

In `frontend/src/App.test.tsx`, both occurrences of `'Gym Progress Tracker'` (lines 9 and 14)
change to `'LiftCurve'`. Full resulting file:

```tsx
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from './test/renderWithProviders'
import { App } from './App'

describe('App', () => {
  it('redirects an unauthenticated visitor at "/" to /login', async () => {
    renderWithProviders(<App />, { route: '/' })
    await waitFor(() => expect(screen.getByText('LiftCurve')).toBeInTheDocument())
  })

  it('redirects an unknown URL through the root redirect to /login when unauthenticated', async () => {
    renderWithProviders(<App />, { route: '/this-does-not-exist' })
    await waitFor(() => expect(screen.getByText('LiftCurve')).toBeInTheDocument())
  })
})
```

- [ ] **Step 5: Run the frontend test suite to confirm Steps 1-4 didn't break anything**

Run: `cd frontend && npm run test`
Expected: all test files pass, including `App.test.tsx`'s 2 tests now matching the new text.

- [ ] **Step 6: Rename the build metadata**

In `frontend/package.json`, line 2, change:

```json
  "name": "frontend",
```

to:

```json
  "name": "liftcurve-frontend",
```

- [ ] **Step 7: Rename the CI Docker image tags**

In `.github/workflows/ci.yml`, in the `docker-build` job, change:

```yaml
      - name: Build backend image
        run: docker build -t gym-progress-tracker-backend ./backend
      - name: Build frontend image
        run: docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t gym-progress-tracker-frontend ./frontend
```

to:

```yaml
      - name: Build backend image
        run: docker build -t liftcurve-backend ./backend
      - name: Build frontend image
        run: docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t liftcurve-frontend ./frontend
```

Nothing else in this file changes (triggers, other jobs, steps all stay as-is).

- [ ] **Step 8: Fix the DEPLOY.md title**

In `docs/DEPLOY.md`, line 1, change:

```markdown
# Deploy — gym-progress-tracker
```

to:

```markdown
# Deploy — LiftCurve
```

Nothing else in this file changes — this is a 1-line title fix, the rest of the deploy
walkthrough content stays exactly as-is (it was reviewed and approved in Sprint 6).

- [ ] **Step 9: Replace the favicon**

Replace the entire content of `frontend/public/favicon.svg` (currently the unrelated default
Vite/React scaffold purple-blob icon) with:

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
  <rect width="32" height="32" rx="4" fill="#1A1815"/>
  <text x="16" y="17" text-anchor="middle" dominant-baseline="central" font-family="Arial, Helvetica, sans-serif" font-weight="700" font-size="14" fill="#C1442B" letter-spacing="0.5">LC</text>
</svg>
```

This reuses the existing "Ferro & Giz" palette tokens directly (`#1A1815` = `--color-bg`,
`#C1442B` = `--color-accent`) — no new dependency, no new color introduced.

- [ ] **Step 10: Full frontend verification**

Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: all three green — same warnings as before (the 2 pre-existing
`only-export-components` oxlint warnings), no new errors, no test failures.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/routes/AppLayout.tsx frontend/index.html frontend/src/features/auth/LoginPage.tsx frontend/src/App.test.tsx frontend/package.json .github/workflows/ci.yml docs/DEPLOY.md frontend/public/favicon.svg
git commit -m "fix(frontend): corrigir inconsistencia de nome (GPT/Gym Progress Tracker -> LiftCurve) e favicon"
```

---

### Task 2: README completo

**Files:**
- Modify: `README.md`
- Create (already captured before this task is dispatched — see "Pre-task activity" below): `docs/screenshots/dashboard.png`

**Interfaces:** None — pure documentation. Consumes the already-captured screenshot file from
the pre-task activity described below (this task does not capture it).

**Pre-task activity (not a plan step — done by the controller before dispatching this task):**
Before this task is dispatched, a screenshot of the production dashboard
(`https://liftcurve.vercel.app/dashboard`, logged in as the demo account, showing the 1RM
progression chart, weekly volume chart, and plateau alert) is captured via Playwright and
saved to `docs/screenshots/dashboard.png` in the working tree. This task's implementer should
find that file already present — Step 1 below is to confirm it exists, not to create it.

- [ ] **Step 1: Confirm the screenshot file exists**

Run: `ls -la docs/screenshots/dashboard.png` (or equivalent) — confirm the file exists and is
a non-trivial size (a real screenshot, not an empty/corrupt file). If it's missing, STOP and
report NEEDS_CONTEXT — do not generate a placeholder image.

- [ ] **Step 2: Replace the full content of README.md**

Replace the entire content of `README.md` with:

```markdown
# LiftCurve

[![CI](https://github.com/Raphael-Sinelli/liftcurve/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Raphael-Sinelli/liftcurve/actions/workflows/ci.yml?query=branch%3Adevelop)
![Java](https://img.shields.io/badge/Java-21-blue)
![Quarkus](https://img.shields.io/badge/Quarkus-3.37-blue)
![React](https://img.shields.io/badge/React-19-blue)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue)

Rastreador de progressão de treino de força com estimativa de 1RM, agregação de volume por
grupo muscular e detecção automática de platô.

**App em produção:** [liftcurve.vercel.app](https://liftcurve.vercel.app)

## O problema

A maioria dos apps de treino para nesse nível: registrar séries, pesos, repetições. LiftCurve
vai além — cada série registrada alimenta uma camada de domínio que transforma número bruto
em decisão de treino:

- **1RM estimado** (Epley + Brzycki) por série, calculado em tempo real.
- **Volume semanal** agregado por grupo muscular, ao longo de toda a janela de treino.
- **Detecção automática de platô** — 3 sessões consecutivas sem novo recorde de 1RM disparam
  um alerta com sugestão de deload. É o diferencial real do projeto: a parte que a maioria
  dos apps de treino de portfólio não tem, porque exige regra de negócio de verdade, não só
  CRUD.

Projeto de portfólio autoral, construído full-stack (Java/Quarkus + React/TypeScript) do
zero, sprint a sprint, com TDD, revisão de código real a cada etapa, e deploy completo em
produção.

## Stack e decisões de arquitetura

| Camada | Escolha |
|---|---|
| Backend | Java 21, Quarkus, Hibernate/Panache (Repository pattern), Flyway |
| Auth | JWT (access token HS256) + refresh token opaco rotacionado, BCrypt |
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Router, Recharts, Tailwind CSS |
| Testes | JUnit 5 (unitário) + Testcontainers/RestAssured (integração) no backend, Vitest + Testing Library + MSW no frontend |
| Infra | Docker Compose (3 serviços), GitHub Actions (lint+test+build+docker-build em paralelo) |
| Deploy | Backend no [Render](https://render.com) (Docker), frontend na [Vercel](https://vercel.com) |

Resumo — pra quem quiser se aprofundar nas decisões e no processo:
- [`docs/superpowers/specs/`](docs/superpowers/specs/) — design original completo do projeto
  e specs detalhadas de sprints individuais.
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — plano de implementação task-a-task
  de cada sprint, incluindo os bugs reais encontrados e corrigidos em cada revisão de código.
- [`docs/DEPLOY.md`](docs/DEPLOY.md) — passo a passo completo de deploy (Render + Vercel).

## Dashboard

![Dashboard do LiftCurve mostrando progressão de 1RM, volume semanal e alertas de platô](docs/screenshots/dashboard.png)

## Conta demo

O projeto inclui uma conta pública com histórico de treino já populado (16 semanas, 48
sessões, 6 exercícios em 5 grupos musculares, incluindo 1 exercício com platô proposital) —
pra testar o app sem precisar cadastrar nada, direto em produção.

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

O dashboard (`/dashboard`, tela inicial após login) mostra esses dados de verdade — gráfico
de progressão de 1RM por exercício, volume semanal por grupo muscular, e a lista de alertas
de platô — construído com Recharts sobre os mesmos endpoints. A aba "Sessões" deixa
registrar um treino novo (com ou sem rotina base), adicionar séries em tempo real e ver o
1RM estimado de cada uma, e finalizar o treino.

## Como rodar localmente

Pré-requisitos: Java 21, Node 20+, Docker Desktop.

```bash
# sobe os 3 serviços (Postgres + backend + frontend)
docker compose up -d --build
```

Abre `http://localhost:8081` — a conta demo já vem populada (`GYMTRACKER_SEED_DEMO=true` é o
default no Compose).

Alternativa em modo dev (hot reload):

```bash
# sobe só o Postgres
docker compose up -d postgres

# backend (http://localhost:8080)
cd backend
./mvnw quarkus:dev      # Windows: mvnw.cmd quarkus:dev

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev      # chamadas de API são encaminhadas pro backend via proxy do Vite (vite.config.ts), não precisa configurar VITE_API_BASE_URL em dev
```

Ligar a seed em modo dev (desligada por padrão nesse caminho — nunca roda sozinha em
dev/test/CI): setar a env var `GYMTRACKER_SEED_DEMO=true` antes de subir a aplicação. Roda
uma única vez no boot (idempotente):

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
cd frontend
npm run lint    # oxlint
npm run test    # Vitest + Testing Library + MSW
npm run build   # tsc -b && vite build
```

## Deploy

Backend no [Render](https://render.com) (Docker), frontend na [Vercel](https://vercel.com).
Passo a passo completo de configuração (variáveis de ambiente, CORS, CSP) em
[`docs/DEPLOY.md`](docs/DEPLOY.md).

- Frontend: https://liftcurve.vercel.app
- Backend (API): https://liftcurve.onrender.com

## Status

- [x] Sprint 0 — Fundações
- [x] Sprint 1 — Schema + Auth
- [x] Sprint 2 — Exercícios + Rotinas
- [x] Sprint 3 — Sessões + Domínio Core (1RM, volume, platô)
- [x] Sprint 4 — API polish + Seed
- [x] Sprint 5a — Frontend Core (Fundação + Auth + Exercícios + Rotinas)
- [x] Sprint 5b — Frontend Sessões + Dashboard (Recharts)
- [x] Sprint 6 — Testes, CI/CD, Deploy
- [ ] Sprint 7 — README final + Polish
```

Note: the Status checklist's last line stays `- [ ]` here — Task 3 (Wrap-up) flips it to
`- [x]` once everything else in the sprint is verified, matching the same pattern used in
every prior sprint's own wrap-up task.

- [ ] **Step 3: Verify the file renders sensibly**

Read the file back and confirm: the image reference (`docs/screenshots/dashboard.png`)
matches the actual file path from Step 1, the CI badge URL's repo path
(`Raphael-Sinelli/liftcurve`) matches the real `git remote -v` output, and no leftover
`gym-progress-tracker`/`GPT`/`Gym Progress Tracker`/"Em desenvolvimento" text remains
anywhere in the file (`grep -in "gym-progress-tracker\|em desenvolvimento" README.md` should
return nothing).

- [ ] **Step 4: Commit**

```bash
git add README.md docs/screenshots/dashboard.png
git commit -m "docs: reescrever README completo (problema, stack, screenshot, deploy)"
```

---

### Task 3: Wrap-up

**Files:**
- Modify: `README.md` (status checklist only)

**Interfaces:** None.

- [ ] **Step 1: Mark Sprint 7 complete in the README status checklist**

In `README.md`, change:

```markdown
- [ ] Sprint 7 — README final + Polish
```

to:

```markdown
- [x] Sprint 7 — README final + Polish
```

- [ ] **Step 2: Final frontend verification**

Run: `cd frontend && npm run lint && npm run test && npm run build`
Expected: all green, same as Task 1's Step 10 (nothing frontend-related changed since then
except README, which doesn't affect these commands).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: marcar Sprint 7 completa"
```

---

## Verification (whole branch)

- `cd frontend && npm run lint && npm run test && npm run build` — green, no new warnings/errors.
- Backend untouched — no need to run `mvn verify` unless something unexpected surfaced during QA.
- `README.md` has no leftover references to the old codename or "Em desenvolvimento".
- `git log --oneline develop..HEAD` shows 3 commits, one per task.
- Two activities happen outside this plan's tasks, done directly by the controller (no diff to review in the traditional sense):
  - **Screenshot capture** (via Playwright, against `https://liftcurve.vercel.app`, logged in as the demo account) — must happen before Task 2 is dispatched, since Task 2 depends on `docs/screenshots/dashboard.png` already existing.
  - **Golden-path QA in production** (via Playwright, against the real Render/Vercel URLs — register with a throwaway test account, login, CRUD exercises/routines, full workout session start-to-finish, dashboard) — validates the already-deployed Sprint 6 state, independent of this branch's changes; can run anytime during this plan's execution, does not block or depend on any of the 3 tasks above.
- After the whole-branch review passes clean and fixes (if any) are applied: the controller delivers a full Sprint 0-7 project retrospective (in Portuguese, as a Markdown Artifact plus a shorter chat summary) before asking for merge approval — this is also outside the 3 tasks above, it's the controller's own final reporting step.
