# Deploy — gym-progress-tracker

Backend no Render (Docker), frontend na Vercel. Passo a passo completo — sem CLI, só os
painéis web dos dois serviços.

## 1. Banco de dados (Render Postgres)

1. No painel do Render (render.com), clique em **New +** → **PostgreSQL**.
2. Nome: `gym-progress-tracker-db` (ou o que preferir). Região: a mesma que vai usar pro
   backend (latência menor). Plano: Free.
3. Depois de criado, abra a instância e copie os valores da seção **Connections**:
   - **Hostname**
   - **Port** (normalmente `5432`)
   - **Database**
   - **Username**
   - **Password**

   Você vai usar esses 4 valores no passo 2 (não a "Connection String" combinada — o app
   espera host/porta/banco separados do usuário/senha).

## 2. Backend (Render Web Service, Docker)

1. No painel do Render, **New +** → **Web Service**.
2. Conecte o repositório GitHub `Raphael-Sinelli/liftcurve` (autorize o Render a acessar
   sua conta GitHub se ainda não tiver feito isso).
3. **Root Directory**: `backend`
4. **Runtime**: Docker (Render detecta o `Dockerfile` automaticamente dentro de `backend/`).
5. **Instance Type**: Free (ou o plano que preferir).
6. Em **Environment Variables**, adicione (uma de cada vez, "Add Environment Variable"):

   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<Hostname do passo 1>:<Port do passo 1>/<Database do passo 1>` |
   | `DB_USERNAME` | `<Username do passo 1>` |
   | `DB_PASSWORD` | `<Password do passo 1>` |
   | `JWT_SECRET` | gere um valor novo com `openssl rand -base64 32` no seu terminal — **nunca reuse o valor que já está no `application.properties`, esse já está público no histórico do git** |
   | `CORS_ALLOWED_ORIGINS` | deixe em branco por enquanto — volte aqui depois do passo 3 (frontend) com a URL real da Vercel |
   | `GYMTRACKER_SEED_DEMO` | `true` (só precisa rodar uma vez — pode deixar `true` permanentemente, é idempotente) |

7. Clique em **Create Web Service**. O Render vai puxar o repo, buildar a imagem Docker
   (usando `backend/Dockerfile`), e subir o container. Acompanhe os logs na aba **Logs** —
   procure pela linha do Flyway confirmando as migrations e, se `GYMTRACKER_SEED_DEMO=true`,
   a linha do `DemoSeeder`.
8. Depois que o deploy terminar, o Render mostra a URL pública do serviço (algo como
   `https://gym-progress-tracker-backend.onrender.com`). **Anote essa URL** — vai precisar
   dela nos passos 3 e 4.
9. Teste rápido: abra `<URL do passo 8>/muscle-groups` no browser — deve devolver uma lista
   JSON de grupos musculares (200 OK, sem autenticação).

## 3. Frontend (Vercel)

1. No painel da Vercel (vercel.com), **Add New** → **Project**.
2. Importe o repositório GitHub `Raphael-Sinelli/liftcurve`.
3. **Root Directory**: `frontend` (clique em "Edit" ao lado de Root Directory pra mudar).
4. Framework Preset: Vercel deve detectar **Vite** automaticamente. Se não detectar:
   - Build Command: `npm run build`
   - Output Directory: `dist`
   - Install Command: `npm install`
5. Em **Environment Variables**, adicione:

   | Key | Value | Environment |
   |---|---|---|
   | `VITE_API_BASE_URL` | `<URL do backend, passo 2.8>` | Production |

   **Importante**: essa variável só faz efeito no momento do *build* (Vite grava isso no
   bundle estático) — se você mudar depois, precisa fazer um novo deploy (redeploy), não
   basta salvar a variável.
6. Clique em **Deploy**. Depois que terminar, a Vercel mostra a URL pública (algo como
   `https://liftcurve.vercel.app`, ou um domínio customizado se você configurar um depois).
   **Anote essa URL.**

## 4. Fechar o laço: CORS + CSP com as URLs reais

Agora que as duas URLs existem de verdade:

1. **Volta no Render** (backend → Environment): edite `CORS_ALLOWED_ORIGINS` pra ser
   exatamente a URL da Vercel do passo 3.6 (ex.: `https://liftcurve.vercel.app`, sem barra
   no final). Salve — o Render vai fazer redeploy automático com a variável nova.
2. **No repositório**, edite `frontend/vercel.json` — troque o placeholder
   `https://SUBSTITUA-PELA-URL-DO-BACKEND-NO-RENDER` no `Content-Security-Policy` pela URL
   real do backend (passo 2.8). Commite e dê push — a Vercel redesploya automaticamente a
   partir do repositório.

## 5. Verificação final

- Abra a URL da Vercel, faça login com a conta demo (`demo@gymtracker.app` /
  `DemoGymTracker2026!`), confirme que o dashboard carrega com dado de verdade (prova que
  CORS + `VITE_API_BASE_URL` estão certos).
- Abra o DevTools do browser → aba Network → confirme que as chamadas de API vão pra URL do
  Render, não `localhost`.
- Confirme que não há erro de CORS no console do browser.
- Abra a URL da Vercel direto numa rota interna (ex.: `<url>/dashboard`) — não a raiz — e
  confirme que carrega normalmente (não um 404), provando que o fallback de SPA está
  funcionando em produção.

## Variáveis de ambiente — referência rápida

| Serviço | Variável | Obrigatória? | Observação |
|---|---|---|---|
| Backend (Render) | `DB_URL` | Sim | `jdbc:postgresql://<host>:<porta>/<banco>` |
| Backend (Render) | `DB_USERNAME` | Sim | |
| Backend (Render) | `DB_PASSWORD` | Sim | |
| Backend (Render) | `JWT_SECRET` | Sim | Gerar novo com `openssl rand -base64 32` — nunca reusar o valor de dev commitado |
| Backend (Render) | `CORS_ALLOWED_ORIGINS` | Sim | URL exata do frontend na Vercel, sem wildcard |
| Backend (Render) | `GYMTRACKER_SEED_DEMO` | Opcional | `true` popula a conta demo pública uma vez (idempotente) |
| Backend (Render) | `PORT` | Não | Render injeta automaticamente na maioria dos planos |
| Frontend (Vercel) | `VITE_API_BASE_URL` | Sim | URL pública do backend — só tem efeito em build time |

### Nota sobre segurança: refresh token no frontend

O refresh token fica armazenado em `localStorage` no frontend (não em um httpOnly cookie) — risco aceito e documentado em `docs/superpowers/notes/security-review-sprint6.md`. Não é uma tarefa de deploy, mas uma referência de rastreamento pra não se perder em futuras sprints.
