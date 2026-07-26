# Sprint 5a — Frontend Core (Fundação + Auth + Exercícios + Rotinas) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the frontend foundation (routing, API client, design system) plus full Auth, Exercícios and Rotinas UI on top of the already-complete backend (Sprint 0-4, merged in `develop`).

**Architecture:** Vite + React 19 + TypeScript, React Router for navigation, TanStack Query for server state, Axios with request/response interceptors for camelCase↔snake_case conversion and JWT lifecycle (access token in memory, refresh token in `localStorage`, automatic refresh-and-retry on 401). React Hook Form + Zod for forms. Custom Tailwind v4 design system ("Ferro & Giz") with a small set of hand-built primitives (no component library). MSW mocks the backend in tests.

**Tech Stack:** React 19, TypeScript 6 (strict), Vite 8, Tailwind CSS v4, react-router-dom, @tanstack/react-query, axios, react-hook-form, zod, @hookform/resolvers, @radix-ui/react-dialog, @radix-ui/react-select, @fontsource/* (Big Shoulders Display, IBM Plex Sans, IBM Plex Mono), Vitest, @testing-library/react, msw.

## Global Constraints

- Backend base path has NO prefix — routes are `/auth/*`, `/exercises`, `/muscle-groups`, `/routines` directly at root (confirmed by reading `AuthResource`/`ExerciseResource`/`RoutineResource`/`MuscleGroupResource`).
- JSON over the wire is `snake_case`; TypeScript code is `camelCase`. Conversion happens ONLY in the Axios interceptors (`lib/apiClient.ts`) — never call `keysToSnake`/`keysToCamel` manually in a feature file.
- Every API error is `{"error":{"code","message","status","details"}}`. Never render `err.message` from Axios directly — always go through `extractApiError`/`getErrorMessage` (`lib/errorMessages.ts`).
- Access token lives ONLY in memory (`lib/tokenStore.ts`), never in `localStorage`/`sessionStorage`. Refresh token lives in `localStorage` under key `gymtracker.refreshToken`.
- TypeScript strict mode is ON (`tsconfig.app.json`). No `any` without justification.
- Tailwind v4 is zero-config (`@tailwindcss/vite` plugin) — theme customization goes in `frontend/src/index.css` under an `@theme` block, there is no `tailwind.config.js`.
- No backend changes this sprint. Dev CORS is solved via a Vite `server.proxy`, not by touching the Quarkus backend (which already got its final Sprint 4 review) — production CORS is explicitly Sprint 6 scope.
- Run `npm run build`, `npm run lint`, and `npm run test` before every commit that touches `.ts`/`.tsx` — CI will fail the `frontend` job on any of the three failing.
- Package manager is npm (`package-lock.json` already exists, CI uses `npm ci`).
- Commit message convention: `feat(frontend): ...` / `test(frontend): ...` / `docs: ...`, matching the backend's established style.

---

### Task 1: Fundação — dependências, config, roteamento de build/CI

**Files:**
- Modify: `frontend/package.json` (via `npm install`, not hand-edited)
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/tsconfig.app.json`
- Create: `frontend/.env.example`
- Create: `frontend/src/lib/queryClient.ts`
- Create: `frontend/src/test/setup.ts`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Delete: `frontend/src/App.css`
- Modify: `.github/workflows/ci.yml`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces: `queryClient` (default `QueryClient` instance, exported from `frontend/src/lib/queryClient.ts`) — consumed by `main.tsx` (Task 1) and by every feature's React Query hook (Tasks 6-7).
- Produces: Vite path alias `@` → `frontend/src/` and `server.proxy` forwarding `/auth`, `/exercises`, `/muscle-groups`, `/routines`, `/workout-sessions`, `/dashboard`, `/users` to `http://localhost:8080` — consumed implicitly by every later import and by `apiClient.ts`'s relative `baseURL`.

- [ ] **Step 1: Install all app + dev dependencies now (avoids re-touching package.json in every later task)**

Run from `frontend/`:
```bash
npm install react-router-dom @tanstack/react-query axios react-hook-form zod @hookform/resolvers @radix-ui/react-dialog @radix-ui/react-select @fontsource/big-shoulders-display @fontsource/ibm-plex-sans @fontsource/ibm-plex-mono
npm install -D vitest @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom msw
```
Expected: `package.json` `dependencies`/`devDependencies` updated, `package-lock.json` regenerated, no errors.

- [ ] **Step 2: Add `test` script and Vitest config to `vite.config.ts`**

Replace `frontend/vite.config.ts` with:
```ts
import path from 'node:path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/exercises': 'http://localhost:8080',
      '/muscle-groups': 'http://localhost:8080',
      '/routines': 'http://localhost:8080',
      '/workout-sessions': 'http://localhost:8080',
      '/dashboard': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

Add to `frontend/package.json` `scripts`:
```json
"test": "vitest run"
```
(Keep existing `dev`, `build`, `lint`, `preview` scripts unchanged.)

- [ ] **Step 3: Turn on TypeScript strict mode**

In `frontend/tsconfig.app.json`, inside `compilerOptions`, add:
```json
"strict": true,
```

- [ ] **Step 4: Create the test setup file**

Create `frontend/src/test/setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'
```

- [ ] **Step 5: Create `.env.example`**

Create `frontend/.env.example`:
```
# URL base da API em produção (Vercel). Vazio em dev — o proxy do Vite
# (vite.config.ts) encaminha as chamadas relativas pro backend local.
VITE_API_BASE_URL=
```

- [ ] **Step 6: Create the shared React Query client**

Create `frontend/src/lib/queryClient.ts`:
```ts
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
```

- [ ] **Step 7: Write the failing smoke test for `App`**

Delete `frontend/src/App.css` (dead file, never imported).

Create `frontend/src/App.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { queryClient } from './lib/queryClient'

describe('App', () => {
  it('renders the placeholder heading', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>,
    )
    expect(screen.getByRole('heading', { name: 'gym-progress-tracker' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd frontend && npm run test -- App.test.tsx`
Expected: FAIL (`App.tsx` doesn't export a named `App`, only a default export — the import `{ App }` fails).

- [ ] **Step 9: Update `App.tsx` to a named export and update `main.tsx`**

Replace `frontend/src/App.tsx`:
```tsx
export function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-50">
      <h1 className="text-3xl font-semibold tracking-tight">gym-progress-tracker</h1>
    </main>
  )
}

export default App
```

Replace `frontend/src/main.tsx`:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import { App } from './App'
import { queryClient } from './lib/queryClient'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd frontend && npm run test -- App.test.tsx`
Expected: PASS (1 test).

- [ ] **Step 11: Verify build and lint still pass**

Run: `cd frontend && npm run build && npm run lint`
Expected: both succeed with no errors (strict mode must not break the trivial `App.tsx`/`main.tsx`).

- [ ] **Step 12: Wire lint + test into CI**

In `.github/workflows/ci.yml`, in the `frontend` job, insert `npm run lint` and `npm run test` steps between the existing `npm ci` and `npm run build` steps:
```yaml
    - run: npm ci
    - run: npm run lint
    - run: npm run test
    - run: npm run build
```

- [ ] **Step 13: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/tsconfig.app.json frontend/.env.example frontend/src/lib/queryClient.ts frontend/src/test/setup.ts frontend/src/main.tsx frontend/src/App.tsx frontend/src/App.test.tsx .github/workflows/ci.yml
git rm frontend/src/App.css
git commit -m "feat(frontend): fundação — deps, Vitest, alias, proxy, strict mode, CI"
```

---

### Task 2: Sistema de design — tokens, fontes, primitivos de UI

**Files:**
- Modify: `frontend/src/index.css`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/test/setup.ts`
- Create: `frontend/src/components/Button.tsx` + `Button.test.tsx`
- Create: `frontend/src/components/Input.tsx` + `Input.test.tsx`
- Create: `frontend/src/components/FormField.tsx` + `FormField.test.tsx`
- Create: `frontend/src/components/Select.tsx` + `Select.test.tsx`
- Create: `frontend/src/components/Modal.tsx` + `Modal.test.tsx`
- Create: `frontend/src/components/Toast.tsx` + `Toast.test.tsx`
- Create: `frontend/src/components/PlateStat.tsx` + `PlateStat.test.tsx`

**Interfaces:**
- Consumes: nothing from Task 1 beyond the Vitest/build setup.
- Produces (consumed by Tasks 5-7):
  - `Button({variant?: 'primary'|'secondary'|'ghost'|'destructive', ...ButtonHTMLAttributes}, ref)`
  - `Input({hasError?: boolean, ...InputHTMLAttributes}, ref)`
  - `FormField({label: string, htmlFor: string, error?: string, children: ReactNode})`
  - `Select({value: string|undefined, onValueChange: (v:string)=>void, options: {value:string,label:string}[], placeholder?: string, id?: string, hasError?: boolean})`
  - `Modal({open: boolean, onOpenChange: (open:boolean)=>void, title: string, children: ReactNode})`
  - `ToastProvider({children})`, `useToast(): {showToast: (text: string, variant?: 'success'|'error') => void}`
  - `PlateStat({value: number|string, unit: string})`

- [ ] **Step 1: Design tokens — Tailwind v4 theme + base styles**

Replace `frontend/src/index.css`:
```css
@import "tailwindcss";

@theme {
  --color-bg: #1a1815;
  --color-surface: #232019;
  --color-ink: #f2ede1;
  --color-muted: #96907e;
  --color-accent: #c1442b;
  --color-line: #38352c;

  --font-display: "Big Shoulders Display", sans-serif;
  --font-body: "IBM Plex Sans", sans-serif;
  --font-mono: "IBM Plex Mono", monospace;
}

@layer base {
  body {
    @apply bg-bg text-ink font-body;
  }

  :focus-visible {
    outline: 2px solid var(--color-accent);
    outline-offset: 2px;
  }
}
```

- [ ] **Step 2: Self-host fonts + Radix jsdom polyfills**

Replace `frontend/src/main.tsx`:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import '@fontsource/big-shoulders-display/700.css'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './index.css'
import { App } from './App'
import { queryClient } from './lib/queryClient'
import { ToastProvider } from './components/Toast'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <App />
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)
```

Append to `frontend/src/test/setup.ts` (jsdom doesn't implement these, and Radix's Select/Dialog call them internally — without the polyfills, every Radix-based component test throws):
```ts
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
```
Full resulting `frontend/src/test/setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'

if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
```

- [ ] **Step 3: Button — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/Button.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('renders children text', () => {
    render(<Button>Salvar</Button>)
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Salvar</Button>)
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('does not call onClick when disabled', async () => {
    const onClick = vi.fn()
    render(
      <Button onClick={onClick} disabled>
        Salvar
      </Button>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(onClick).not.toHaveBeenCalled()
  })
})
```

Run: `cd frontend && npm run test -- Button.test.tsx` — expected FAIL (`./Button` doesn't exist).

Create `frontend/src/components/Button.tsx`:
```tsx
import { forwardRef, type ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'destructive'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-ink hover:bg-accent/90',
  secondary: 'bg-surface text-ink border border-line hover:border-accent',
  ghost: 'bg-transparent text-muted hover:text-ink',
  destructive: 'bg-transparent text-accent border border-accent hover:bg-accent hover:text-ink',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', className = '', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      className={`rounded-sm px-4 py-2 font-body text-sm font-semibold uppercase tracking-wide transition-colors motion-reduce:transition-none disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${className}`}
      {...rest}
    />
  )
})
```

Run: `cd frontend && npm run test -- Button.test.tsx` — expected PASS (3 tests).

- [ ] **Step 4: Input — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/Input.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Input } from './Input'

describe('Input', () => {
  it('renders and accepts typed text', async () => {
    render(<Input aria-label="Nome" />)
    const input = screen.getByLabelText('Nome')
    await userEvent.type(input, 'Supino Reto')
    expect(input).toHaveValue('Supino Reto')
  })

  it('applies the error border class when hasError is true', () => {
    render(<Input aria-label="Nome" hasError />)
    expect(screen.getByLabelText('Nome')).toHaveClass('border-accent')
  })

  it('applies the default border class when hasError is false', () => {
    render(<Input aria-label="Nome" />)
    expect(screen.getByLabelText('Nome')).toHaveClass('border-line')
  })
})
```

Run: `cd frontend && npm run test -- Input.test.tsx` — expected FAIL.

Create `frontend/src/components/Input.tsx`:
```tsx
import { forwardRef, type InputHTMLAttributes } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  hasError?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { hasError = false, className = '', ...rest },
  ref,
) {
  return (
    <input
      ref={ref}
      className={`w-full rounded-sm border bg-surface px-3 py-2 font-body text-ink placeholder:text-muted focus-visible:outline-none ${
        hasError ? 'border-accent' : 'border-line'
      } ${className}`}
      {...rest}
    />
  )
})
```

Run: `cd frontend && npm run test -- Input.test.tsx` — expected PASS (3 tests).

- [ ] **Step 5: FormField — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/FormField.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FormField } from './FormField'

describe('FormField', () => {
  it('renders the label and children', () => {
    render(
      <FormField label="Nome" htmlFor="name">
        <input id="name" />
      </FormField>,
    )
    expect(screen.getByText('Nome')).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('renders an alert with the error message when error is set', () => {
    render(
      <FormField label="Nome" htmlFor="name" error="Informe o nome.">
        <input id="name" />
      </FormField>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Informe o nome.')
  })

  it('renders no alert when error is absent', () => {
    render(
      <FormField label="Nome" htmlFor="name">
        <input id="name" />
      </FormField>,
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- FormField.test.tsx` — expected FAIL.

Create `frontend/src/components/FormField.tsx`:
```tsx
import type { ReactNode } from 'react'

interface FormFieldProps {
  label: string
  htmlFor: string
  error?: string
  children: ReactNode
}

export function FormField({ label, htmlFor, error, children }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={htmlFor} className="font-body text-xs font-semibold uppercase tracking-wide text-muted">
        {label}
      </label>
      {children}
      {error && (
        <p role="alert" className="font-body text-xs text-accent">
          {error}
        </p>
      )}
    </div>
  )
}
```

Run: `cd frontend && npm run test -- FormField.test.tsx` — expected PASS (3 tests).

- [ ] **Step 6: Select (Radix) — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/Select.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const OPTIONS = [
  { value: 'chest', label: 'Peito' },
  { value: 'back', label: 'Costas' },
]

describe('Select', () => {
  it('shows the placeholder when no value is selected', () => {
    render(<Select value={undefined} onValueChange={vi.fn()} options={OPTIONS} placeholder="Selecione" />)
    expect(screen.getByText('Selecione')).toBeInTheDocument()
  })

  it('calls onValueChange with the chosen option value', async () => {
    const onValueChange = vi.fn()
    render(<Select value={undefined} onValueChange={onValueChange} options={OPTIONS} />)
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByText('Costas'))
    expect(onValueChange).toHaveBeenCalledWith('back')
  })
})
```

Run: `cd frontend && npm run test -- Select.test.tsx` — expected FAIL.

Create `frontend/src/components/Select.tsx`:
```tsx
import * as RadixSelect from '@radix-ui/react-select'

interface SelectOption {
  value: string
  label: string
}

interface SelectProps {
  value: string | undefined
  onValueChange: (value: string) => void
  options: SelectOption[]
  placeholder?: string
  id?: string
  hasError?: boolean
}

export function Select({ value, onValueChange, options, placeholder = 'Selecione', id, hasError = false }: SelectProps) {
  return (
    <RadixSelect.Root value={value} onValueChange={onValueChange}>
      <RadixSelect.Trigger
        id={id}
        className={`flex w-full items-center justify-between rounded-sm border bg-surface px-3 py-2 font-body text-ink focus-visible:outline-none ${
          hasError ? 'border-accent' : 'border-line'
        }`}
      >
        <RadixSelect.Value placeholder={placeholder} />
        <RadixSelect.Icon>▾</RadixSelect.Icon>
      </RadixSelect.Trigger>
      <RadixSelect.Portal>
        <RadixSelect.Content className="overflow-hidden rounded-sm border border-line bg-surface font-body text-ink">
          <RadixSelect.Viewport>
            {options.map((option) => (
              <RadixSelect.Item
                key={option.value}
                value={option.value}
                className="cursor-pointer px-3 py-2 outline-none data-[highlighted]:bg-accent data-[highlighted]:text-ink"
              >
                <RadixSelect.ItemText>{option.label}</RadixSelect.ItemText>
              </RadixSelect.Item>
            ))}
          </RadixSelect.Viewport>
        </RadixSelect.Content>
      </RadixSelect.Portal>
    </RadixSelect.Root>
  )
}
```

Run: `cd frontend && npm run test -- Select.test.tsx` — expected PASS (2 tests).

- [ ] **Step 7: Modal (Radix Dialog) — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/Modal.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Modal'

describe('Modal', () => {
  it('renders the title and content when open', () => {
    render(
      <Modal open onOpenChange={vi.fn()} title="Novo exercício">
        <p>Conteúdo</p>
      </Modal>,
    )
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Novo exercício')).toBeInTheDocument()
    expect(screen.getByText('Conteúdo')).toBeInTheDocument()
  })

  it('renders nothing when closed', () => {
    render(
      <Modal open={false} onOpenChange={vi.fn()} title="Novo exercício">
        <p>Conteúdo</p>
      </Modal>,
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onOpenChange(false) when the close button is clicked', async () => {
    const onOpenChange = vi.fn()
    render(
      <Modal open onOpenChange={onOpenChange} title="Novo exercício">
        <p>Conteúdo</p>
      </Modal>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })
})
```

Run: `cd frontend && npm run test -- Modal.test.tsx` — expected FAIL.

Create `frontend/src/components/Modal.tsx`:
```tsx
import * as Dialog from '@radix-ui/react-dialog'
import type { ReactNode } from 'react'

interface ModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  children: ReactNode
}

export function Modal({ open, onOpenChange, title, children }: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-bg/80" />
        <Dialog.Content className="fixed left-1/2 top-1/2 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-sm border border-line bg-surface p-6">
          <Dialog.Title className="font-display text-xl font-bold text-ink">{title}</Dialog.Title>
          <div className="mt-4">{children}</div>
          <Dialog.Close asChild>
            <button type="button" aria-label="Fechar" className="absolute right-4 top-4 text-muted hover:text-ink">
              ✕
            </button>
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
```

Run: `cd frontend && npm run test -- Modal.test.tsx` — expected PASS (3 tests).

- [ ] **Step 8: Toast — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/Toast.test.tsx`:
```tsx
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from './Toast'

function ToastTrigger() {
  const { showToast } = useToast()
  return (
    <button type="button" onClick={() => showToast('Exercício criado.')}>
      Disparar
    </button>
  )
}

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the message after showToast is called', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'Disparar' }))
    expect(screen.getByRole('status')).toHaveTextContent('Exercício criado.')
  })

  it('removes the message after 4 seconds', async () => {
    const user = userEvent.setup({ delay: null })
    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'Disparar' }))
    expect(screen.getByRole('status')).toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(4000)
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- Toast.test.tsx` — expected FAIL.

Create `frontend/src/components/Toast.tsx`:
```tsx
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type ToastVariant = 'success' | 'error'

interface ToastMessage {
  id: number
  text: string
  variant: ToastVariant
}

interface ToastContextValue {
  showToast: (text: string, variant?: ToastVariant) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

let nextId = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastMessage[]>([])

  const showToast = useCallback((text: string, variant: ToastVariant = 'success') => {
    const id = nextId++
    setToasts((current) => [...current, { id, text, variant }])
    setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, 4000)
  }, [])

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={`rounded-sm border px-4 py-2 font-body text-sm ${
              toast.variant === 'error' ? 'border-accent text-accent bg-surface' : 'border-line text-ink bg-surface'
            }`}
          >
            {toast.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast deve ser usado dentro de ToastProvider')
  }
  return context
}
```

Run: `cd frontend && npm run test -- Toast.test.tsx` — expected PASS (2 tests).

- [ ] **Step 9: PlateStat — write tests, verify fail, implement, verify pass**

Create `frontend/src/components/PlateStat.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PlateStat } from './PlateStat'

describe('PlateStat', () => {
  it('renders the value and unit', () => {
    render(<PlateStat value={80} unit="kg" />)
    expect(screen.getByText('80')).toBeInTheDocument()
    expect(screen.getByText('kg')).toBeInTheDocument()
  })

  it('renders a string value as-is (e.g. a placeholder dash)', () => {
    render(<PlateStat value="-" unit="kg" />)
    expect(screen.getByText('-')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- PlateStat.test.tsx` — expected FAIL.

Create `frontend/src/components/PlateStat.tsx`:
```tsx
interface PlateStatProps {
  value: number | string
  unit: string
}

export function PlateStat({ value, unit }: PlateStatProps) {
  return (
    <span className="inline-flex items-baseline gap-1 rounded-sm border border-line bg-surface px-2 py-1 font-mono text-sm tabular-nums text-ink">
      {value}
      <span className="text-[0.6rem] font-body uppercase tracking-wide text-muted">{unit}</span>
    </span>
  )
}
```

Run: `cd frontend && npm run test -- PlateStat.test.tsx` — expected PASS (2 tests).

- [ ] **Step 10: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/index.css frontend/src/main.tsx frontend/src/test/setup.ts frontend/src/components frontend/package.json frontend/package-lock.json
git commit -m "feat(frontend): identidade visual Ferro & Giz + primitivos de UI (Button, Input, FormField, Select, Modal, Toast, PlateStat)"
```

---

### Task 3: Utilitários puros — conversão de case + mapa de erros

**Files:**
- Create: `frontend/src/lib/caseConversion.ts` + `caseConversion.test.ts`
- Create: `frontend/src/lib/errorMessages.ts` + `errorMessages.test.ts`

**Interfaces:**
- Produces (consumed by Task 4's `apiClient.ts` and every feature's error handling):
  - `toCamelCase<T>(value: T): T`
  - `toSnakeCase<T>(value: T): T`
  - `interface ApiFieldError { field: string; message: string }`
  - `interface ApiErrorBody { code: string; message: string; status: number; details: ApiFieldError[] }`
  - `getErrorMessage(code: string): string`
  - `extractApiError(err: unknown): ApiErrorBody | null`

- [ ] **Step 1: caseConversion — write tests, verify fail, implement, verify pass**

Create `frontend/src/lib/caseConversion.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { toCamelCase, toSnakeCase } from './caseConversion'

describe('toSnakeCase', () => {
  it('converts flat camelCase keys to snake_case', () => {
    expect(toSnakeCase({ weightKg: 80, plannedSets: 3 })).toEqual({ weight_kg: 80, planned_sets: 3 })
  })

  it('converts nested objects and arrays', () => {
    expect(
      toSnakeCase({
        name: 'Treino A',
        exercises: [{ exerciseId: 'x', plannedLoadKg: 60 }],
      }),
    ).toEqual({
      name: 'Treino A',
      exercises: [{ exercise_id: 'x', planned_load_kg: 60 }],
    })
  })

  it('leaves already-snake_case keys unchanged', () => {
    expect(toSnakeCase({ id: 'x', name: 'y' })).toEqual({ id: 'x', name: 'y' })
  })

  it('passes through null and primitives', () => {
    expect(toSnakeCase(null)).toBeNull()
    expect(toSnakeCase(42)).toBe(42)
    expect(toSnakeCase('text')).toBe('text')
  })

  it('does not mangle a Date instance', () => {
    const date = new Date('2026-01-01T00:00:00.000Z')
    const result = toSnakeCase({ createdAt: date }) as { createdAt: Date }
    expect(result.createdAt).toBeInstanceOf(Date)
    expect(result.createdAt.toISOString()).toBe('2026-01-01T00:00:00.000Z')
  })
})

describe('toCamelCase', () => {
  it('converts flat snake_case keys to camelCase', () => {
    expect(toCamelCase({ weight_kg: 80, planned_sets: 3 })).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('converts nested objects and arrays', () => {
    expect(
      toCamelCase({
        exercises: [{ exercise_id: 'x', planned_load_kg: 60 }],
      }),
    ).toEqual({
      exercises: [{ exerciseId: 'x', plannedLoadKg: 60 }],
    })
  })
})

describe('round trip', () => {
  it('camelCase -> snake_case -> camelCase returns the original shape', () => {
    const original = { weightKg: 80, plannedSets: 3, exercises: [{ exerciseId: 'x' }] }
    expect(toCamelCase(toSnakeCase(original))).toEqual(original)
  })
})
```

Run: `cd frontend && npm run test -- caseConversion.test.ts` — expected FAIL.

Create `frontend/src/lib/caseConversion.ts`:
```ts
function snakeToCamelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase())
}

function camelToSnakeKey(key: string): string {
  return key.replace(/[A-Z]/g, (char) => `_${char.toLowerCase()}`)
}

function convertKeys<T>(value: T, convertKey: (key: string) => string): T {
  if (Array.isArray(value)) {
    return value.map((item) => convertKeys(item, convertKey)) as unknown as T
  }
  if (value !== null && typeof value === 'object' && !(value instanceof Date)) {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, val]) => [
        convertKey(key),
        convertKeys(val, convertKey),
      ]),
    ) as T
  }
  return value
}

export function toCamelCase<T>(value: T): T {
  return convertKeys(value, snakeToCamelKey)
}

export function toSnakeCase<T>(value: T): T {
  return convertKeys(value, camelToSnakeKey)
}
```

Run: `cd frontend && npm run test -- caseConversion.test.ts` — expected PASS (9 tests).

- [ ] **Step 2: errorMessages — write tests, verify fail, implement, verify pass**

Create `frontend/src/lib/errorMessages.test.ts`:
```ts
import type { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { extractApiError, getErrorMessage } from './errorMessages'

describe('getErrorMessage', () => {
  it('returns the mapped Portuguese message for a known code', () => {
    expect(getErrorMessage('EXERCISE_IN_USE')).toBe(
      'Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.',
    )
  })

  it('returns the fallback message for an unknown code', () => {
    expect(getErrorMessage('SOME_UNMAPPED_CODE')).toBe('Algo deu errado. Tente novamente.')
  })
})

describe('extractApiError', () => {
  it('extracts the error body from an Axios-error-shaped object', () => {
    const err = {
      response: {
        data: {
          error: { code: 'VALIDATION_ERROR', message: 'Dados inválidos.', status: 400, details: [{ field: 'email', message: 'must not be blank' }] },
        },
      },
    } as unknown as AxiosError<{ error: { code: string; message: string; status: number; details: { field: string; message: string }[] } }>

    expect(extractApiError(err)).toEqual({
      code: 'VALIDATION_ERROR',
      message: 'Dados inválidos.',
      status: 400,
      details: [{ field: 'email', message: 'must not be blank' }],
    })
  })

  it('defaults details to an empty array when absent', () => {
    const err = {
      response: { data: { error: { code: 'EXERCISE_NOT_FOUND', message: 'Não encontrado.', status: 404 } } },
    }
    expect(extractApiError(err)).toEqual({ code: 'EXERCISE_NOT_FOUND', message: 'Não encontrado.', status: 404, details: [] })
  })

  it('returns null when the error has no response body', () => {
    expect(extractApiError(new Error('network error'))).toBeNull()
  })

  it('returns null when the response body has no error.code', () => {
    expect(extractApiError({ response: { data: {} } })).toBeNull()
  })
})
```

Run: `cd frontend && npm run test -- errorMessages.test.ts` — expected FAIL.

Create `frontend/src/lib/errorMessages.ts`:
```ts
import type { AxiosError } from 'axios'

export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  status: number
  details: ApiFieldError[]
}

const ERROR_MESSAGES: Record<string, string> = {
  EMAIL_ALREADY_REGISTERED: 'Este email já está cadastrado.',
  INVALID_CREDENTIALS: 'Email ou senha inválidos.',
  INVALID_REFRESH_TOKEN: 'Sua sessão expirou. Faça login novamente.',
  UNAUTHORIZED: 'Você precisa entrar para continuar.',
  INVALID_TOKEN: 'Sua sessão expirou. Faça login novamente.',
  MUSCLE_GROUP_NOT_FOUND: 'Grupo muscular não encontrado.',
  EXERCISE_NOT_FOUND: 'Exercício não encontrado.',
  NOT_EXERCISE_OWNER: 'Este exercício é do catálogo global e não pode ser editado.',
  EXERCISE_IN_USE: 'Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.',
  INVALID_EXERCISE_REFERENCE: 'Um dos exercícios selecionados não é válido.',
  ROUTINE_NOT_FOUND: 'Rotina não encontrada.',
  VALIDATION_ERROR: 'Verifique os campos preenchidos.',
}

const FALLBACK_MESSAGE = 'Algo deu errado. Tente novamente.'

export function getErrorMessage(code: string): string {
  return ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE
}

export function extractApiError(err: unknown): ApiErrorBody | null {
  const axiosErr = err as AxiosError<{ error?: ApiErrorBody }> | undefined
  const body = axiosErr?.response?.data?.error
  if (!body || typeof body.code !== 'string') {
    return null
  }
  return {
    code: body.code,
    message: body.message,
    status: body.status,
    details: body.details ?? [],
  }
}
```

Run: `cd frontend && npm run test -- errorMessages.test.ts` — expected PASS (6 tests).

- [ ] **Step 3: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/lib/caseConversion.ts frontend/src/lib/caseConversion.test.ts frontend/src/lib/errorMessages.ts frontend/src/lib/errorMessages.test.ts
git commit -m "feat(frontend): utilitarios de conversao de case e mapeamento de erro"
```

---

### Task 4: Cliente API + ciclo de vida de token (AuthContext incluso)

This is the riskiest logic in the frontend (Decisão 6/7 do design) — heaviest TDD of the sprint, uses MSW to exercise the real Axios interceptor pipeline instead of mocking Axios itself.

**Files:**
- Create: `frontend/src/lib/tokenStore.ts` + `tokenStore.test.ts`
- Create: `frontend/src/test/mocks/handlers.ts`
- Create: `frontend/src/test/mocks/server.ts`
- Modify: `frontend/src/test/setup.ts`
- Create: `frontend/src/lib/apiClient.ts` + `apiClient.test.ts`
- Create: `frontend/src/api/authApi.ts`
- Create: `frontend/src/test/renderWithProviders.tsx`
- Create: `frontend/src/context/AuthContext.tsx` + `AuthContext.test.tsx`

**Interfaces:**
- Consumes: `toCamelCase`/`toSnakeCase` (Task 3), `extractApiError` (Task 3), `ToastProvider` (Task 2).
- Produces (consumed by Task 5-7):
  - `tokenStore.ts`: `getAccessToken()`, `setAccessToken(token: string|null)`, `subscribeAccessToken(listener): () => void`, `getStoredRefreshToken()`, `setStoredRefreshToken(token: string)`, `clearStoredRefreshToken()`
  - `apiClient.ts`: `apiClient` (configured Axios instance — ALL feature `api/*.ts` files use this, never plain `axios`), `refreshAccessToken(): Promise<string>`
  - `authApi.ts`: `interface AuthTokens {accessToken, refreshToken, expiresInSeconds}`, `interface User {id, email, name}`, `register(email, password, name): Promise<AuthTokens>`, `login(email, password): Promise<AuthTokens>`, `logout(refreshToken): Promise<void>`, `getMe(): Promise<User>`
  - `AuthContext.tsx`: `AuthProvider`, `useAuth(): {user: User|null, accessToken: string|null, isLoading: boolean, login, loginAsDemo, register, logout}`, `DEMO_EMAIL`, `DEMO_PASSWORD`
  - `test/renderWithProviders.tsx`: `renderWithProviders(ui, {route?}): RenderResult` — wraps in `QueryClientProvider` + `AuthProvider` + `ToastProvider` + `MemoryRouter`. Used by every component test from here on.

- [ ] **Step 1: tokenStore — write tests, verify fail, implement, verify pass**

Create `frontend/src/lib/tokenStore.test.ts`:
```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearStoredRefreshToken,
  getAccessToken,
  getStoredRefreshToken,
  setAccessToken,
  setStoredRefreshToken,
  subscribeAccessToken,
} from './tokenStore'

describe('access token (in-memory)', () => {
  beforeEach(() => {
    setAccessToken(null)
  })

  it('starts as null', () => {
    expect(getAccessToken()).toBeNull()
  })

  it('setAccessToken updates the stored value', () => {
    setAccessToken('token-123')
    expect(getAccessToken()).toBe('token-123')
  })

  it('notifies subscribers when the token changes', () => {
    const listener = vi.fn()
    subscribeAccessToken(listener)
    setAccessToken('token-456')
    expect(listener).toHaveBeenCalledWith('token-456')
  })

  it('stops notifying after unsubscribe', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeAccessToken(listener)
    unsubscribe()
    setAccessToken('token-789')
    expect(listener).not.toHaveBeenCalled()
  })
})

describe('refresh token (localStorage)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns null when nothing is stored', () => {
    expect(getStoredRefreshToken()).toBeNull()
  })

  it('round-trips a stored value', () => {
    setStoredRefreshToken('refresh-abc')
    expect(getStoredRefreshToken()).toBe('refresh-abc')
  })

  it('clearStoredRefreshToken removes it', () => {
    setStoredRefreshToken('refresh-abc')
    clearStoredRefreshToken()
    expect(getStoredRefreshToken()).toBeNull()
  })
})
```

Run: `cd frontend && npm run test -- tokenStore.test.ts` — expected FAIL.

Create `frontend/src/lib/tokenStore.ts`:
```ts
const REFRESH_TOKEN_STORAGE_KEY = 'gymtracker.refreshToken'

type AccessTokenListener = (token: string | null) => void

let accessToken: string | null = null
const listeners = new Set<AccessTokenListener>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
  listeners.forEach((listener) => listener(token))
}

export function subscribeAccessToken(listener: AccessTokenListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)
}

export function setStoredRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token)
}

export function clearStoredRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY)
}
```

Run: `cd frontend && npm run test -- tokenStore.test.ts` — expected PASS (7 tests).

- [ ] **Step 2: MSW mock server + auth handlers + wire into test setup**

Create `frontend/src/test/mocks/handlers.ts`:
```ts
import { http, HttpResponse } from 'msw'

export const DEMO_EMAIL = 'demo@gymtracker.app'
export const DEMO_PASSWORD = 'DemoGymTracker2026!'
export const VALID_ACCESS_TOKEN = 'valid-access-token'
export const VALID_REFRESH_TOKEN = 'valid-refresh-token'
export const ROTATED_ACCESS_TOKEN = 'rotated-access-token'
export const ROTATED_REFRESH_TOKEN = 'rotated-refresh-token'

interface LoginBody {
  email: string
  password: string
}

interface RegisterBody extends LoginBody {
  name: string
}

interface RefreshBody {
  refresh_token: string
}

export const handlers = [
  http.post('/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginBody
    if (body.email === DEMO_EMAIL && body.password === DEMO_PASSWORD) {
      return HttpResponse.json({
        access_token: VALID_ACCESS_TOKEN,
        refresh_token: VALID_REFRESH_TOKEN,
        expires_in_seconds: 900,
      })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_CREDENTIALS', message: 'Email ou senha inválidos.', status: 401, details: [] } },
      { status: 401 },
    )
  }),

  http.post('/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterBody
    if (body.email === 'taken@gymtracker.app') {
      return HttpResponse.json(
        { error: { code: 'EMAIL_ALREADY_REGISTERED', message: 'Este email já está cadastrado.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    return HttpResponse.json(
      { access_token: VALID_ACCESS_TOKEN, refresh_token: VALID_REFRESH_TOKEN, expires_in_seconds: 900 },
      { status: 201 },
    )
  }),

  http.post('/auth/refresh', async ({ request }) => {
    const body = (await request.json()) as RefreshBody
    if (body.refresh_token === VALID_REFRESH_TOKEN) {
      return HttpResponse.json({
        access_token: ROTATED_ACCESS_TOKEN,
        refresh_token: ROTATED_REFRESH_TOKEN,
        expires_in_seconds: 900,
      })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_REFRESH_TOKEN', message: 'Sua sessão expirou. Faça login novamente.', status: 401, details: [] } },
      { status: 401 },
    )
  }),

  http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })),

  http.get('/users/me', ({ request }) => {
    const auth = request.headers.get('authorization')
    if (auth === `Bearer ${VALID_ACCESS_TOKEN}` || auth === `Bearer ${ROTATED_ACCESS_TOKEN}`) {
      return HttpResponse.json({ id: 'demo-user-id', email: DEMO_EMAIL, name: 'Conta Demo' })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou. Faça login novamente.', status: 401, details: [] } },
      { status: 401 },
    )
  }),
]
```

Create `frontend/src/test/mocks/server.ts`:
```ts
import { setupServer } from 'msw/node'
import { handlers } from './handlers'

export const server = setupServer(...handlers)
```

Replace `frontend/src/test/setup.ts` (adds MSW lifecycle hooks alongside the jest-dom import and Radix polyfills from Task 2):
```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './mocks/server'

if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  localStorage.clear()
})
afterAll(() => server.close())
```

- [ ] **Step 3: apiClient — write tests, verify fail, implement, verify pass**

Create `frontend/src/lib/apiClient.test.ts`:
```ts
import { http, HttpResponse, delay } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '../test/mocks/server'
import { ROTATED_ACCESS_TOKEN, VALID_ACCESS_TOKEN, VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { apiClient, refreshAccessToken } from './apiClient'
import { getAccessToken, getStoredRefreshToken, setAccessToken, setStoredRefreshToken } from './tokenStore'

describe('apiClient', () => {
  beforeEach(() => {
    setAccessToken(null)
    localStorage.clear()
  })

  it('converts the request body to snake_case', async () => {
    server.use(
      http.post('/echo', async ({ request }) => HttpResponse.json(await request.json())),
    )
    const response = await apiClient.post('/echo', { weightKg: 80, plannedSets: 3 })
    expect(response.data).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('converts the response body to camelCase', async () => {
    server.use(http.get('/echo-snake', () => HttpResponse.json({ weight_kg: 80, planned_sets: 3 })))
    const response = await apiClient.get('/echo-snake')
    expect(response.data).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('attaches the Authorization header when an access token is set', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    const response = await apiClient.get('/users/me')
    expect(response.data).toEqual({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
  })

  it('on a 401 INVALID_TOKEN from a protected route, refreshes and retries once', async () => {
    setAccessToken('expired-token')
    setStoredRefreshToken(VALID_REFRESH_TOKEN)

    let call = 0
    server.use(
      http.get('/users/me', ({ request }) => {
        call += 1
        if (call === 1) {
          return HttpResponse.json(
            { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou.', status: 401, details: [] } },
            { status: 401 },
          )
        }
        expect(request.headers.get('authorization')).toBe(`Bearer ${ROTATED_ACCESS_TOKEN}`)
        return HttpResponse.json({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
      }),
    )

    const response = await apiClient.get('/users/me')
    expect(response.data.name).toBe('Conta Demo')
    expect(call).toBe(2)
    expect(getAccessToken()).toBe(ROTATED_ACCESS_TOKEN)
  })

  it('deduplicates concurrent refreshes triggered by simultaneous 401s', async () => {
    setAccessToken('expired-token')
    setStoredRefreshToken(VALID_REFRESH_TOKEN)

    let usersMeCalls = 0
    let refreshCalls = 0
    server.use(
      http.get('/users/me', async () => {
        usersMeCalls += 1
        if (usersMeCalls <= 2) {
          return HttpResponse.json(
            { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou.', status: 401, details: [] } },
            { status: 401 },
          )
        }
        return HttpResponse.json({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
      }),
      http.post('/auth/refresh', async () => {
        refreshCalls += 1
        await delay(20)
        return HttpResponse.json({
          access_token: ROTATED_ACCESS_TOKEN,
          refresh_token: 'rotated-refresh-token',
          expires_in_seconds: 900,
        })
      }),
    )

    const [first, second] = await Promise.all([apiClient.get('/users/me'), apiClient.get('/users/me')])
    expect(first.data.name).toBe('Conta Demo')
    expect(second.data.name).toBe('Conta Demo')
    expect(refreshCalls).toBe(1)
  })

  it('clears tokens when the refresh call itself fails', async () => {
    setStoredRefreshToken('an-invalid-refresh-token')
    await expect(refreshAccessToken()).rejects.toBeTruthy()
    expect(getAccessToken()).toBeNull()
    expect(getStoredRefreshToken()).toBeNull()
  })

  it('does not attempt a refresh for a 401 coming from /auth/* routes', async () => {
    await expect(apiClient.post('/auth/login', { email: 'x@x.com', password: 'wrong' })).rejects.toMatchObject({
      response: { status: 401 },
    })
  })
})
```

Run: `cd frontend && npm run test -- apiClient.test.ts` — expected FAIL (`./apiClient` doesn't exist).

Create `frontend/src/lib/apiClient.ts`:
```ts
import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { toCamelCase, toSnakeCase } from './caseConversion'
import { extractApiError } from './errorMessages'
import {
  clearStoredRefreshToken,
  getAccessToken,
  getStoredRefreshToken,
  setAccessToken,
  setStoredRefreshToken,
} from './tokenStore'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

export const apiClient = axios.create({ baseURL })

apiClient.interceptors.request.use((config) => {
  if (config.data) {
    config.data = toSnakeCase(config.data)
  }
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    if (response.data) {
      response.data = toCamelCase(response.data)
    }
    return response
  },
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const apiError = extractApiError(error)
    const isAuthRoute = original?.url?.startsWith('/auth')

    if (
      error.response?.status === 401 &&
      apiError?.code === 'INVALID_TOKEN' &&
      original &&
      !original._retry &&
      !isAuthRoute
    ) {
      original._retry = true
      const newToken = await refreshAccessToken()
      original.headers.set('Authorization', `Bearer ${newToken}`)
      return apiClient(original)
    }

    return Promise.reject(error)
  },
)

let refreshInFlight: Promise<string> | null = null

// Uses a bare `axios` call, not `apiClient`, so refreshing never re-enters
// this same interceptor and doesn't depend on authApi.ts — which itself
// depends on apiClient — avoiding a circular import between the two modules.
export async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) {
    return refreshInFlight
  }

  refreshInFlight = (async () => {
    const refreshToken = getStoredRefreshToken()
    if (!refreshToken) {
      setAccessToken(null)
      throw new Error('Nenhum refresh token salvo.')
    }

    try {
      const response = await axios.post(`${baseURL}/auth/refresh`, toSnakeCase({ refreshToken }))
      const body = toCamelCase(response.data) as {
        accessToken: string
        refreshToken: string
        expiresInSeconds: number
      }
      setAccessToken(body.accessToken)
      setStoredRefreshToken(body.refreshToken)
      return body.accessToken
    } catch (err) {
      setAccessToken(null)
      clearStoredRefreshToken()
      throw err
    }
  })()

  try {
    return await refreshInFlight
  } finally {
    refreshInFlight = null
  }
}
```

Run: `cd frontend && npm run test -- apiClient.test.ts` — expected PASS (7 tests).

- [ ] **Step 4: authApi — implement (no dedicated test file; exercised end-to-end via AuthContext tests below)**

Create `frontend/src/api/authApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface User {
  id: string
  email: string
  name: string
}

export async function register(email: string, password: string, name: string): Promise<AuthTokens> {
  const response = await apiClient.post<AuthTokens>('/auth/register', { email, password, name })
  return response.data
}

export async function login(email: string, password: string): Promise<AuthTokens> {
  const response = await apiClient.post<AuthTokens>('/auth/login', { email, password })
  return response.data
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}

export async function getMe(): Promise<User> {
  const response = await apiClient.get<User>('/users/me')
  return response.data
}
```

- [ ] **Step 5: AuthContext — write tests, verify fail, implement, verify pass**

Create `frontend/src/context/AuthContext.test.tsx`:
```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { DEMO_EMAIL, DEMO_PASSWORD, VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { getAccessToken, setStoredRefreshToken } from '../lib/tokenStore'
import { AuthProvider, useAuth } from './AuthContext'

function Probe() {
  const { user, isLoading, login, loginAsDemo, logout } = useAuth()
  return (
    <div>
      <p data-testid="loading">{String(isLoading)}</p>
      <p data-testid="user">{user?.name ?? 'none'}</p>
      <button onClick={() => void loginAsDemo()}>demo-login</button>
      <button onClick={() => void login('wrong@x.com', 'wrong')}>bad-login</button>
      <button onClick={() => void logout()}>logout</button>
    </div>
  )
}

describe('AuthProvider', () => {
  it('finishes loading with no user when there is no stored refresh token', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })

  it('silently restores the session on boot when a valid refresh token is stored', async () => {
    setStoredRefreshToken(VALID_REFRESH_TOKEN)
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('loginAsDemo populates the user', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('logout clears the user and the stored refresh token', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))

    await userEvent.click(screen.getByText('logout'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(getAccessToken()).toBeNull()
  })
})
```

Run: `cd frontend && npm run test -- AuthContext.test.tsx` — expected FAIL.

Create `frontend/src/context/AuthContext.tsx`:
```tsx
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { getMe, login as loginRequest, logout as logoutRequest, register as registerRequest, type User } from '../api/authApi'
import { refreshAccessToken } from '../lib/apiClient'
import {
  clearStoredRefreshToken,
  getAccessToken,
  getStoredRefreshToken,
  setAccessToken,
  setStoredRefreshToken,
  subscribeAccessToken,
} from '../lib/tokenStore'

export const DEMO_EMAIL = 'demo@gymtracker.app'
export const DEMO_PASSWORD = 'DemoGymTracker2026!'

interface AuthContextValue {
  user: User | null
  accessToken: string | null
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  loginAsDemo: () => Promise<void>
  register: (email: string, password: string, name: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(getAccessToken())
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    return subscribeAccessToken((newToken) => {
      setToken(newToken)
      if (!newToken) {
        setUser(null)
      }
    })
  }, [])

  useEffect(() => {
    async function bootSession() {
      const storedRefreshToken = getStoredRefreshToken()
      if (!storedRefreshToken) {
        setIsLoading(false)
        return
      }
      try {
        await refreshAccessToken()
        const profile = await getMe()
        setUser(profile)
      } catch {
        // refreshAccessToken já limpa os tokens em caso de falha
      } finally {
        setIsLoading(false)
      }
    }
    void bootSession()
  }, [])

  const applySession = useCallback(async (tokens: { accessToken: string; refreshToken: string }) => {
    setAccessToken(tokens.accessToken)
    setStoredRefreshToken(tokens.refreshToken)
    const profile = await getMe()
    setUser(profile)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await loginRequest(email, password)
      await applySession(tokens)
    },
    [applySession],
  )

  const loginAsDemo = useCallback(() => login(DEMO_EMAIL, DEMO_PASSWORD), [login])

  const register = useCallback(
    async (email: string, password: string, name: string) => {
      const tokens = await registerRequest(email, password, name)
      await applySession(tokens)
    },
    [applySession],
  )

  const logout = useCallback(async () => {
    const refreshToken = getStoredRefreshToken()
    if (refreshToken) {
      try {
        await logoutRequest(refreshToken)
      } catch {
        // logout no backend é idempotente/best-effort — sessão local é limpa de qualquer forma
      }
    }
    setAccessToken(null)
    clearStoredRefreshToken()
  }, [])

  return (
    <AuthContext.Provider value={{ user, accessToken: token, isLoading, login, loginAsDemo, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider')
  }
  return context
}
```

Run: `cd frontend && npm run test -- AuthContext.test.tsx` — expected PASS (4 tests).

- [ ] **Step 6: Shared test render helper (used from Task 5 onward)**

Create `frontend/src/test/renderWithProviders.tsx`:
```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { ToastProvider } from '../components/Toast'

function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string
}

export function renderWithProviders(ui: ReactElement, { route = '/', ...options }: RenderWithProvidersOptions = {}) {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <ToastProvider>
            <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Wrapper, ...options })
}
```

This file has no dedicated test — it's test infrastructure, exercised transitively by every test that uses it from Task 5 onward.

- [ ] **Step 7: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/lib/tokenStore.ts frontend/src/lib/tokenStore.test.ts frontend/src/lib/apiClient.ts frontend/src/lib/apiClient.test.ts frontend/src/api/authApi.ts frontend/src/context/AuthContext.tsx frontend/src/context/AuthContext.test.tsx frontend/src/test/mocks frontend/src/test/setup.ts frontend/src/test/renderWithProviders.tsx
git commit -m "feat(frontend): cliente API com refresh automatico de token + AuthContext"
```

---

### Task 5: Autenticação — telas + proteção de rota + shell da aplicação

**Files:**
- Create: `frontend/src/routes/ProtectedRoute.tsx` + `ProtectedRoute.test.tsx`
- Create: `frontend/src/routes/AppLayout.tsx`
- Create: `frontend/src/features/auth/LoginPage.tsx` + `LoginPage.test.tsx`
- Create: `frontend/src/features/auth/RegisterPage.tsx` + `RegisterPage.test.tsx`
- Create: `frontend/src/features/exercises/ExercisesListPage.tsx` (placeholder — full version in Task 6)
- Create: `frontend/src/features/routines/RoutinesListPage.tsx` (placeholder — full version in Task 7)
- Modify: `frontend/src/App.tsx` + `App.test.tsx`
- Modify: `frontend/src/main.tsx`

**Interfaces:**
- Consumes: `useAuth` (Task 4), `Button`/`Input`/`FormField` (Task 2), `extractApiError`/`getErrorMessage` (Task 3), `renderWithProviders` (Task 4).
- Produces (consumed by Tasks 6-7): `ProtectedRoute`, `AppLayout`, routes `/login`, `/register`, `/exercises`, `/routines` wired in `App.tsx`.

- [ ] **Step 1: ProtectedRoute — write tests, verify fail, implement, verify pass**

Create `frontend/src/routes/ProtectedRoute.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { setStoredRefreshToken } from '../lib/tokenStore'
import { renderWithProviders } from '../test/renderWithProviders'
import { ProtectedRoute } from './ProtectedRoute'

function Guarded() {
  return (
    <Routes>
      <Route path="/login" element={<p>Tela de login</p>} />
      <Route
        path="/exercises"
        element={
          <ProtectedRoute>
            <p>Conteúdo protegido</p>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

describe('ProtectedRoute', () => {
  it('redirects to /login when there is no session', async () => {
    renderWithProviders(<Guarded />, { route: '/exercises' })
    await waitFor(() => expect(screen.getByText('Tela de login')).toBeInTheDocument())
  })

  it('renders the protected content when a session is restored', async () => {
    setStoredRefreshToken(VALID_REFRESH_TOKEN)
    renderWithProviders(<Guarded />, { route: '/exercises' })
    await waitFor(() => expect(screen.getByText('Conteúdo protegido')).toBeInTheDocument())
  })
})
```

Run: `cd frontend && npm run test -- ProtectedRoute.test.tsx` — expected FAIL.

Create `frontend/src/routes/ProtectedRoute.tsx`:
```tsx
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center bg-bg font-body text-muted">Carregando...</div>
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
```

Run: `cd frontend && npm run test -- ProtectedRoute.test.tsx` — expected PASS (2 tests).

- [ ] **Step 2: AppLayout — implement (nav shell, no dedicated test — exercised via App integration tests below)**

Create `frontend/src/routes/AppLayout.tsx`:
```tsx
import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Button } from '../components/Button'
import { useAuth } from '../context/AuthContext'

const NAV_ITEMS = [
  { to: '/exercises', label: 'Exercícios' },
  { to: '/routines', label: 'Rotinas' },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()

  return (
    <div className="flex min-h-screen bg-bg">
      <nav className="flex w-48 flex-col justify-between border-r border-line bg-surface p-4">
        <div>
          <p className="font-display text-lg font-bold text-ink">GPT</p>
          <ul className="mt-8 flex flex-col gap-1">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    `block border-l-2 px-3 py-2 font-body text-sm font-semibold uppercase tracking-wide ${
                      isActive ? 'border-accent text-ink' : 'border-transparent text-muted hover:text-ink'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <p className="font-body text-xs text-muted">{user?.name}</p>
          <Button variant="ghost" className="mt-2 w-full" onClick={() => void logout()}>
            Sair
          </Button>
        </div>
      </nav>
      <main className="flex-1 p-8">{children}</main>
    </div>
  )
}
```

- [ ] **Step 3: LoginPage — write tests, verify fail, implement, verify pass**

Create `frontend/src/features/auth/LoginPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { DEMO_EMAIL, DEMO_PASSWORD } from '../../test/mocks/handlers'
import { LoginPage } from './LoginPage'

function LoginUnderTest() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/exercises" element={<p>Página de exercícios</p>} />
      <Route path="/register" element={<p>Página de registro</p>} />
    </Routes>
  )
}

describe('LoginPage', () => {
  it('shows a validation message when submitting empty fields', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Informe o email.')).toBeInTheDocument()
  })

  it('shows a friendly message on invalid credentials', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), 'wrong@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Email ou senha inválidos.')).toBeInTheDocument()
  })

  it('navigates to /exercises after a successful login', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), DEMO_EMAIL)
    await userEvent.type(screen.getByLabelText('Senha'), DEMO_PASSWORD)
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() => expect(screen.getByText('Página de exercícios')).toBeInTheDocument())
  })

  it('the demo-login button logs in with the documented demo credentials', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.click(screen.getByRole('button', { name: 'Entrar como visitante (conta demo)' }))
    await waitFor(() => expect(screen.getByText('Página de exercícios')).toBeInTheDocument())
  })

  it('links to the register page', () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    expect(screen.getByRole('link', { name: 'Cadastre-se' })).toHaveAttribute('href', '/register')
  })
})
```

Run: `cd frontend && npm run test -- LoginPage.test.tsx` — expected FAIL.

Create `frontend/src/features/auth/LoginPage.tsx`:
```tsx
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { useAuth } from '../../context/AuthContext'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'

const loginSchema = z.object({
  email: z.string().min(1, 'Informe o email.'),
  password: z.string().min(1, 'Informe a senha.'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const { login, loginAsDemo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/exercises'

  async function attemptLogin(action: () => Promise<void>) {
    setFormError(null)
    setIsSubmitting(true)
    try {
      await action()
      navigate(redirectTo, { replace: true })
    } catch (err) {
      const apiError = extractApiError(err)
      setFormError(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function onSubmit(values: LoginFormValues) {
    await attemptLogin(() => login(values.email, values.password))
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm rounded-sm border border-line bg-surface p-8">
        <h1 className="font-display text-3xl font-bold text-ink">Gym Progress Tracker</h1>
        <p className="mt-1 font-body text-sm text-muted">Entre para ver sua progressão.</p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <FormField label="Email" htmlFor="email" error={errors.email?.message}>
            <Input id="email" type="email" hasError={!!errors.email} {...register('email')} />
          </FormField>
          <FormField label="Senha" htmlFor="password" error={errors.password?.message}>
            <Input id="password" type="password" hasError={!!errors.password} {...register('password')} />
          </FormField>

          {formError && (
            <p role="alert" className="font-body text-sm text-accent">
              {formError}
            </p>
          )}

          <Button type="submit" disabled={isSubmitting}>
            Entrar
          </Button>
        </form>

        <Button
          type="button"
          variant="secondary"
          className="mt-3 w-full"
          onClick={() => void attemptLogin(loginAsDemo)}
          disabled={isSubmitting}
        >
          Entrar como visitante (conta demo)
        </Button>

        <p className="mt-6 font-body text-sm text-muted">
          Não tem conta?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Cadastre-se
          </Link>
        </p>
      </div>
    </main>
  )
}
```

Run: `cd frontend && npm run test -- LoginPage.test.tsx` — expected PASS (5 tests).

- [ ] **Step 4: RegisterPage — write tests, verify fail, implement, verify pass**

Create `frontend/src/features/auth/RegisterPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { RegisterPage } from './RegisterPage'

function RegisterUnderTest() {
  return (
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/exercises" element={<p>Página de exercícios</p>} />
    </Routes>
  )
}

describe('RegisterPage', () => {
  it('shows a validation message for a short password', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'raphael@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    expect(await screen.findByText('A senha precisa ter pelo menos 8 caracteres.')).toBeInTheDocument()
  })

  it('shows a friendly message when the email is already registered', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'taken@gymtracker.app')
    await userEvent.type(screen.getByLabelText('Senha'), 'longenoughpassword')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    expect(await screen.findByText('Este email já está cadastrado.')).toBeInTheDocument()
  })

  it('navigates to /exercises after a successful registration (auto-login)', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'new@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'longenoughpassword')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    await waitFor(() => expect(screen.getByText('Página de exercícios')).toBeInTheDocument())
  })
})
```

Run: `cd frontend && npm run test -- RegisterPage.test.tsx` — expected FAIL.

Create `frontend/src/features/auth/RegisterPage.tsx`:
```tsx
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { useAuth } from '../../context/AuthContext'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'

const registerSchema = z.object({
  name: z.string().min(1, 'Informe seu nome.').max(120, 'Nome muito longo.'),
  email: z.string().email('Email inválido.'),
  password: z.string().min(8, 'A senha precisa ter pelo menos 8 caracteres.').max(72, 'Senha muito longa.'),
})

type RegisterFormValues = z.infer<typeof registerSchema>

export function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null)
    try {
      await registerUser(values.email, values.password, values.name)
      navigate('/exercises', { replace: true })
    } catch (err) {
      const apiError = extractApiError(err)
      setFormError(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'))
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm rounded-sm border border-line bg-surface p-8">
        <h1 className="font-display text-3xl font-bold text-ink">Criar conta</h1>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
            <Input id="name" hasError={!!errors.name} {...register('name')} />
          </FormField>
          <FormField label="Email" htmlFor="email" error={errors.email?.message}>
            <Input id="email" type="email" hasError={!!errors.email} {...register('email')} />
          </FormField>
          <FormField label="Senha" htmlFor="password" error={errors.password?.message}>
            <Input id="password" type="password" hasError={!!errors.password} {...register('password')} />
          </FormField>

          {formError && (
            <p role="alert" className="font-body text-sm text-accent">
              {formError}
            </p>
          )}

          <Button type="submit" disabled={isSubmitting}>
            Cadastrar
          </Button>
        </form>
      </div>
    </main>
  )
}
```

Run: `cd frontend && npm run test -- RegisterPage.test.tsx` — expected PASS (3 tests).

- [ ] **Step 5: Placeholder feature pages (fully rewritten in Tasks 6-7)**

Create `frontend/src/features/exercises/ExercisesListPage.tsx`:
```tsx
export function ExercisesListPage() {
  return <h1 className="font-display text-2xl font-bold text-ink">Exercícios</h1>
}
```

Create `frontend/src/features/routines/RoutinesListPage.tsx`:
```tsx
export function RoutinesListPage() {
  return <h1 className="font-display text-2xl font-bold text-ink">Rotinas</h1>
}
```

- [ ] **Step 6: Wire full routing — update App.tsx and main.tsx**

Replace `frontend/src/App.tsx`:
```tsx
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './routes/AppLayout'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { ExercisesListPage } from './features/exercises/ExercisesListPage'
import { RoutinesListPage } from './features/routines/RoutinesListPage'
import { useAuth } from './context/AuthContext'

function RedirectRoot() {
  const { user, isLoading } = useAuth()
  if (isLoading) {
    return null
  }
  return <Navigate to={user ? '/exercises' : '/login'} replace />
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<RedirectRoot />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/exercises"
        element={
          <ProtectedRoute>
            <AppLayout>
              <ExercisesListPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/routines"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RoutinesListPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
```

Replace `frontend/src/App.test.tsx` (the Task 1 smoke test asserted a static heading that no longer exists — now `App` renders real routes, so the test verifies routing behavior instead):
```tsx
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from './test/renderWithProviders'
import { App } from './App'

describe('App', () => {
  it('redirects an unauthenticated visitor at "/" to /login', async () => {
    renderWithProviders(<App />, { route: '/' })
    await waitFor(() => expect(screen.getByText('Gym Progress Tracker')).toBeInTheDocument())
  })
})
```

Replace `frontend/src/main.tsx`:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource/big-shoulders-display/700.css'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './index.css'
import { App } from './App'
import { queryClient } from './lib/queryClient'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './components/Toast'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
```

Run: `cd frontend && npm run test -- App.test.tsx` — expected PASS (1 test).

- [ ] **Step 7: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/routes frontend/src/features/auth frontend/src/features/exercises/ExercisesListPage.tsx frontend/src/features/routines/RoutinesListPage.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/main.tsx
git commit -m "feat(frontend): telas de login/registro, protecao de rota e shell da aplicacao"
```

---

### Task 6: Exercícios — CRUD completo

**Files:**
- Modify: `frontend/src/test/mocks/handlers.ts` (add `/exercises`, `/muscle-groups` handlers)
- Create: `frontend/src/api/exercisesApi.ts`
- Create: `frontend/src/api/muscleGroupsApi.ts`
- Create: `frontend/src/features/exercises/useExercisesQueries.ts`
- Create: `frontend/src/features/exercises/ExerciseFormModal.tsx`
- Modify: `frontend/src/features/exercises/ExercisesListPage.tsx` (full rewrite, replaces Task 5 stub) + `ExercisesListPage.test.tsx`

**Interfaces:**
- Consumes: `apiClient` (Task 4), `Button`/`Modal`/`Select`/`FormField`/`Input` (Task 2), `useToast` (Task 2), `extractApiError`/`getErrorMessage` (Task 3), `renderWithProviders` (Task 4).
- Produces (consumed by Task 7's `RoutineBuilderPage`): `useExercises()` (React Query hook returning `Exercise[]`), `interface Exercise {id, name, muscleGroupId, muscleGroupName, ownerId: string|null, createdAt}`.

- [ ] **Step 1: Extend MSW handlers with `/exercises` and `/muscle-groups`**

In `frontend/src/test/mocks/handlers.ts`, add these entries to the exported `handlers` array (keep the existing auth handlers), and add the fixture exports above it:
```ts
export const MUSCLE_GROUPS = [
  { id: 'mg-chest', name: 'Peito' },
  { id: 'mg-back', name: 'Costas' },
]

let exercisesFixture = [
  { id: 'ex-global-1', name: 'Supino Reto', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: null, created_at: '2026-01-01T00:00:00Z' },
  { id: 'ex-custom-1', name: 'Supino Inclinado Halteres', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: 'demo-user-id', created_at: '2026-01-02T00:00:00Z' },
]

export function resetExercisesFixture() {
  exercisesFixture = [
    { id: 'ex-global-1', name: 'Supino Reto', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: null, created_at: '2026-01-01T00:00:00Z' },
    { id: 'ex-custom-1', name: 'Supino Inclinado Halteres', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: 'demo-user-id', created_at: '2026-01-02T00:00:00Z' },
  ]
}
```
Append to the `handlers` array:
```ts
  http.get('/muscle-groups', () => HttpResponse.json(MUSCLE_GROUPS)),

  http.get('/exercises', () => HttpResponse.json(exercisesFixture)),

  http.post('/exercises', async ({ request }) => {
    const body = (await request.json()) as { name: string; muscle_group_id: string }
    const created = {
      id: `ex-new-${exercisesFixture.length + 1}`,
      name: body.name,
      muscle_group_id: body.muscle_group_id,
      muscle_group_name: MUSCLE_GROUPS.find((g) => g.id === body.muscle_group_id)?.name ?? 'Desconhecido',
      owner_id: 'demo-user-id',
      created_at: '2026-07-25T00:00:00Z',
    }
    exercisesFixture = [...exercisesFixture, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/exercises/:id', async ({ params, request }) => {
    const body = (await request.json()) as { name: string; muscle_group_id: string }
    const existing = exercisesFixture.find((e) => e.id === params.id)
    if (!existing) {
      return HttpResponse.json({ error: { code: 'EXERCISE_NOT_FOUND', message: 'Exercício não encontrado.', status: 404, details: [] } }, { status: 404 })
    }
    const updated = { ...existing, name: body.name, muscle_group_id: body.muscle_group_id }
    exercisesFixture = exercisesFixture.map((e) => (e.id === params.id ? updated : e))
    return HttpResponse.json(updated)
  }),

  http.delete('/exercises/:id', ({ params }) => {
    if (params.id === 'ex-in-use') {
      return HttpResponse.json(
        { error: { code: 'EXERCISE_IN_USE', message: 'Este exercício está em uso e não pode ser excluído.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    exercisesFixture = exercisesFixture.filter((e) => e.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
```
Also add `resetExercisesFixture()` to `frontend/src/test/setup.ts`'s `afterEach` block so tests don't leak fixture mutations into each other:
```ts
afterEach(() => {
  server.resetHandlers()
  localStorage.clear()
  resetExercisesFixture()
})
```
(add the import `import { resetExercisesFixture } from './mocks/handlers'` at the top of `setup.ts`)

- [ ] **Step 2: exercisesApi + muscleGroupsApi — implement (no dedicated test file, exercised via ExercisesListPage tests)**

Create `frontend/src/api/exercisesApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface Exercise {
  id: string
  name: string
  muscleGroupId: string
  muscleGroupName: string
  ownerId: string | null
  createdAt: string
}

export interface ExerciseInput {
  name: string
  muscleGroupId: string
}

export async function listExercises(): Promise<Exercise[]> {
  const response = await apiClient.get<Exercise[]>('/exercises')
  return response.data
}

export async function createExercise(input: ExerciseInput): Promise<Exercise> {
  const response = await apiClient.post<Exercise>('/exercises', input)
  return response.data
}

export async function updateExercise(id: string, input: ExerciseInput): Promise<Exercise> {
  const response = await apiClient.put<Exercise>(`/exercises/${id}`, input)
  return response.data
}

export async function deleteExercise(id: string): Promise<void> {
  await apiClient.delete(`/exercises/${id}`)
}
```

Create `frontend/src/api/muscleGroupsApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface MuscleGroup {
  id: string
  name: string
}

export async function listMuscleGroups(): Promise<MuscleGroup[]> {
  const response = await apiClient.get<MuscleGroup[]>('/muscle-groups')
  return response.data
}
```

- [ ] **Step 3: React Query hooks — implement**

Create `frontend/src/features/exercises/useExercisesQueries.ts`:
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createExercise,
  deleteExercise,
  listExercises,
  updateExercise,
  type Exercise,
  type ExerciseInput,
} from '../../api/exercisesApi'
import { listMuscleGroups } from '../../api/muscleGroupsApi'

const EXERCISES_KEY = ['exercises'] as const
const MUSCLE_GROUPS_KEY = ['muscle-groups'] as const

export function useExercises() {
  return useQuery({ queryKey: EXERCISES_KEY, queryFn: listExercises })
}

export function useMuscleGroups() {
  return useQuery({ queryKey: MUSCLE_GROUPS_KEY, queryFn: listMuscleGroups, staleTime: Infinity })
}

export function useCreateExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createExercise,
    onSuccess: (created) => {
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) => [...(current ?? []), created])
    },
  })
}

export function useUpdateExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: ExerciseInput }) => updateExercise(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) =>
        (current ?? []).map((exercise) => (exercise.id === updated.id ? updated : exercise)),
      )
    },
  })
}

export function useDeleteExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteExercise,
    onMutate: async (id: string) => {
      await queryClient.cancelQueries({ queryKey: EXERCISES_KEY })
      const previous = queryClient.getQueryData<Exercise[]>(EXERCISES_KEY)
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) => (current ?? []).filter((exercise) => exercise.id !== id))
      return { previous }
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(EXERCISES_KEY, context.previous)
      }
    },
  })
}
```
(`useDeleteExercise` uses true optimistic-with-rollback per the global `FRONTEND.md` "Optimistic Delete with Rollback" example — trivial here since we already have the real ID. `useCreateExercise`/`useUpdateExercise` patch the cache `onSuccess` instead of full optimistic-with-temp-ID: still zero flash/refetch, without the complexity of reconciling a fabricated ID against the modal-driven create form.)

- [ ] **Step 4: ExerciseFormModal — implement**

Create `frontend/src/features/exercises/ExerciseFormModal.tsx`:
```tsx
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { Modal } from '../../components/Modal'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import type { Exercise } from '../../api/exercisesApi'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useCreateExercise, useMuscleGroups, useUpdateExercise } from './useExercisesQueries'

const exerciseSchema = z.object({
  name: z.string().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  muscleGroupId: z.string().min(1, 'Selecione um grupo muscular.'),
})

type ExerciseFormValues = z.infer<typeof exerciseSchema>

interface ExerciseFormModalProps {
  initialExercise: Exercise | null
  onClose: () => void
}

export function ExerciseFormModal({ initialExercise, onClose }: ExerciseFormModalProps) {
  const { data: muscleGroups } = useMuscleGroups()
  const createExercise = useCreateExercise()
  const updateExercise = useUpdateExercise()
  const { showToast } = useToast()

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ExerciseFormValues>({
    resolver: zodResolver(exerciseSchema),
    defaultValues: {
      name: initialExercise?.name ?? '',
      muscleGroupId: initialExercise?.muscleGroupId ?? '',
    },
  })

  async function onSubmit(values: ExerciseFormValues) {
    try {
      if (initialExercise) {
        await updateExercise.mutateAsync({ id: initialExercise.id, input: values })
        showToast('Exercício atualizado.')
      } else {
        await createExercise.mutateAsync(values)
        showToast('Exercício criado.')
      }
      onClose()
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  return (
    <Modal open onOpenChange={(open) => !open && onClose()} title={initialExercise ? 'Editar exercício' : 'Novo exercício'}>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
          <Input id="name" hasError={!!errors.name} {...register('name')} />
        </FormField>
        <FormField label="Grupo muscular" htmlFor="muscleGroupId" error={errors.muscleGroupId?.message}>
          <Select
            id="muscleGroupId"
            value={watch('muscleGroupId')}
            onValueChange={(value) => setValue('muscleGroupId', value, { shouldValidate: true })}
            options={(muscleGroups ?? []).map((group) => ({ value: group.id, label: group.name }))}
            hasError={!!errors.muscleGroupId}
          />
        </FormField>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            Salvar
          </Button>
        </div>
      </form>
    </Modal>
  )
}
```

- [ ] **Step 5: ExercisesListPage — write tests, verify fail, implement (full rewrite), verify pass**

Create `frontend/src/features/exercises/ExercisesListPage.test.tsx`:
```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { ExercisesListPage } from './ExercisesListPage'

describe('ExercisesListPage', () => {
  it('lists global and custom exercises, marking the global one as catalog-only', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ExercisesListPage />)
    expect(await screen.findByText('Supino Reto')).toBeInTheDocument()
    expect(screen.getByText('Supino Inclinado Halteres')).toBeInTheDocument()
    const globalRow = screen.getByText('Supino Reto').closest('li') as HTMLElement
    expect(within(globalRow).queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument()
    const customRow = screen.getByText('Supino Inclinado Halteres').closest('li') as HTMLElement
    expect(within(customRow).getByRole('button', { name: 'Excluir' })).toBeInTheDocument()
  })

  it('creates a new exercise and shows it in the list without a page reload', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ExercisesListPage />)
    await screen.findByText('Supino Reto')

    await userEvent.click(screen.getByRole('button', { name: 'Novo exercício' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Rosca Direta')
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByText('Peito'))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.getByText('Rosca Direta')).toBeInTheDocument())
  })

  it('shows the EXERCISE_IN_USE message when deletion is blocked', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/exercises', () =>
        HttpResponse.json([
          { id: 'ex-in-use', name: 'Agachamento Livre', muscle_group_id: 'mg-legs', muscle_group_name: 'Pernas', owner_id: 'demo-user-id', created_at: '2026-01-01T00:00:00Z' },
        ]),
      ),
    )
    renderWithProviders(<ExercisesListPage />)
    await screen.findByText('Agachamento Livre')

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))
    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByText('Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- ExercisesListPage.test.tsx` — expected FAIL (stub page has no interactive elements).

Replace `frontend/src/features/exercises/ExercisesListPage.tsx`:
```tsx
import { useMemo, useState } from 'react'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import type { Exercise } from '../../api/exercisesApi'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { ExerciseFormModal } from './ExerciseFormModal'
import { useDeleteExercise, useExercises } from './useExercisesQueries'

export function ExercisesListPage() {
  const { data: exercises, isLoading } = useExercises()
  const deleteExercise = useDeleteExercise()
  const { showToast } = useToast()
  const [modalState, setModalState] = useState<{ mode: 'create' } | { mode: 'edit'; exercise: Exercise } | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  const sorted = useMemo(() => [...(exercises ?? [])].sort((a, b) => a.name.localeCompare(b.name)), [exercises])

  async function handleConfirmDelete() {
    if (!pendingDeleteId) return
    try {
      await deleteExercise.mutateAsync(pendingDeleteId)
      showToast('Exercício excluído.')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setPendingDeleteId(null)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando exercícios...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Exercícios</h1>
        <Button onClick={() => setModalState({ mode: 'create' })}>Novo exercício</Button>
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {sorted.map((exercise) => (
          <li key={exercise.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{exercise.name}</p>
              <p className="font-body text-xs text-muted">
                {exercise.muscleGroupName}
                {exercise.ownerId === null && ' · Catálogo'}
              </p>
            </div>
            {exercise.ownerId !== null && (
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => setModalState({ mode: 'edit', exercise })}>
                  Editar
                </Button>
                <Button variant="destructive" onClick={() => setPendingDeleteId(exercise.id)}>
                  Excluir
                </Button>
              </div>
            )}
          </li>
        ))}
      </ul>

      {modalState && (
        <ExerciseFormModal
          initialExercise={modalState.mode === 'edit' ? modalState.exercise : null}
          onClose={() => setModalState(null)}
        />
      )}

      <Modal open={pendingDeleteId !== null} onOpenChange={(open) => !open && setPendingDeleteId(null)} title="Excluir exercício">
        <p className="font-body text-sm text-ink">Tem certeza que quer excluir este exercício?</p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDeleteId(null)}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={() => void handleConfirmDelete()}>
            Excluir
          </Button>
        </div>
      </Modal>
    </div>
  )
}
```

Run: `cd frontend && npm run test -- ExercisesListPage.test.tsx` — expected PASS (3 tests).

- [ ] **Step 6: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/test/mocks/handlers.ts frontend/src/test/setup.ts frontend/src/api/exercisesApi.ts frontend/src/api/muscleGroupsApi.ts frontend/src/features/exercises
git commit -m "feat(frontend): CRUD de exercicios com update otimista e tratamento de EXERCISE_IN_USE"
```

---

### Task 7: Rotinas — CRUD + construtor

**Files:**
- Modify: `frontend/src/test/mocks/handlers.ts` (add `/routines` handlers)
- Create: `frontend/src/api/routinesApi.ts`
- Create: `frontend/src/features/routines/useRoutinesQueries.ts`
- Modify: `frontend/src/features/routines/RoutinesListPage.tsx` (full rewrite, replaces Task 5 stub) + `RoutinesListPage.test.tsx`
- Create: `frontend/src/features/routines/RoutineBuilderPage.tsx` + `RoutineBuilderPage.test.tsx`
- Modify: `frontend/src/App.tsx` (add `/routines/new`, `/routines/:id`)

**Interfaces:**
- Consumes: `apiClient` (Task 4), `useExercises` (Task 6), `Button`/`Modal`/`Select`/`FormField`/`Input`/`PlateStat` (Task 2), `useToast` (Task 2).
- Produces: nothing consumed by later tasks (Rotinas is the last domain feature this sprint).

- [ ] **Step 1: Extend MSW handlers with `/routines`**

In `frontend/src/test/mocks/handlers.ts`, add fixtures and handlers:
```ts
let routinesFixture = [
  {
    id: 'routine-1',
    name: 'Treino A',
    description: 'Peito e tríceps',
    created_at: '2026-01-01T00:00:00Z',
    exercises: [
      { exercise_id: 'ex-global-1', exercise_name: 'Supino Reto', order_index: 0, planned_sets: 3, planned_reps: 10, planned_load_kg: 60 },
    ],
  },
]

export function resetRoutinesFixture() {
  routinesFixture = [
    {
      id: 'routine-1',
      name: 'Treino A',
      description: 'Peito e tríceps',
      created_at: '2026-01-01T00:00:00Z',
      exercises: [
        { exercise_id: 'ex-global-1', exercise_name: 'Supino Reto', order_index: 0, planned_sets: 3, planned_reps: 10, planned_load_kg: 60 },
      ],
    },
  ]
}

function toSummary(routine: (typeof routinesFixture)[number]) {
  return {
    id: routine.id,
    name: routine.name,
    description: routine.description,
    created_at: routine.created_at,
    exercise_count: routine.exercises.length,
  }
}
```
Append to the `handlers` array:
```ts
  http.get('/routines', () => HttpResponse.json(routinesFixture.map(toSummary))),

  http.get('/routines/:id', ({ params }) => {
    const routine = routinesFixture.find((r) => r.id === params.id)
    if (!routine) {
      return HttpResponse.json({ error: { code: 'ROUTINE_NOT_FOUND', message: 'Rotina não encontrada.', status: 404, details: [] } }, { status: 404 })
    }
    return HttpResponse.json(routine)
  }),

  http.post('/routines', async ({ request }) => {
    const body = (await request.json()) as {
      name: string
      description: string | null
      exercises: { exercise_id: string; planned_sets: number; planned_reps: number; planned_load_kg: number | null }[]
    }
    if (body.exercises.some((e) => e.exercise_id === 'ex-invalid')) {
      return HttpResponse.json(
        { error: { code: 'INVALID_EXERCISE_REFERENCE', message: 'Um dos exercícios selecionados não é válido.', status: 400, details: [] } },
        { status: 400 },
      )
    }
    const created = {
      id: `routine-new-${routinesFixture.length + 1}`,
      name: body.name,
      description: body.description,
      created_at: '2026-07-25T00:00:00Z',
      exercises: body.exercises.map((e, index) => ({
        exercise_id: e.exercise_id,
        exercise_name: 'Exercício',
        order_index: index,
        planned_sets: e.planned_sets,
        planned_reps: e.planned_reps,
        planned_load_kg: e.planned_load_kg,
      })),
    }
    routinesFixture = [...routinesFixture, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/routines/:id', async ({ params, request }) => {
    const body = (await request.json()) as {
      name: string
      description: string | null
      exercises: { exercise_id: string; planned_sets: number; planned_reps: number; planned_load_kg: number | null }[]
    }
    const existing = routinesFixture.find((r) => r.id === params.id)
    if (!existing) {
      return HttpResponse.json({ error: { code: 'ROUTINE_NOT_FOUND', message: 'Rotina não encontrada.', status: 404, details: [] } }, { status: 404 })
    }
    const updated = {
      ...existing,
      name: body.name,
      description: body.description,
      exercises: body.exercises.map((e, index) => ({
        exercise_id: e.exercise_id,
        exercise_name: 'Exercício',
        order_index: index,
        planned_sets: e.planned_sets,
        planned_reps: e.planned_reps,
        planned_load_kg: e.planned_load_kg,
      })),
    }
    routinesFixture = routinesFixture.map((r) => (r.id === params.id ? updated : r))
    return HttpResponse.json(updated)
  }),

  http.delete('/routines/:id', ({ params }) => {
    routinesFixture = routinesFixture.filter((r) => r.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
```
Add `resetRoutinesFixture()` to `frontend/src/test/setup.ts`'s `afterEach` (alongside `resetExercisesFixture()`):
```ts
afterEach(() => {
  server.resetHandlers()
  localStorage.clear()
  resetExercisesFixture()
  resetRoutinesFixture()
})
```
(update the import line to `import { resetExercisesFixture, resetRoutinesFixture } from './mocks/handlers'`)

- [ ] **Step 2: routinesApi — implement**

Create `frontend/src/api/routinesApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface RoutineSummary {
  id: string
  name: string
  description: string | null
  createdAt: string
  exerciseCount: number
}

export interface RoutineExerciseEntry {
  exerciseId: string
  exerciseName: string
  orderIndex: number
  plannedSets: number
  plannedReps: number
  plannedLoadKg: number | null
}

export interface Routine {
  id: string
  name: string
  description: string | null
  createdAt: string
  exercises: RoutineExerciseEntry[]
}

export interface RoutineExerciseInput {
  exerciseId: string
  plannedSets: number
  plannedReps: number
  plannedLoadKg: number | null
}

export interface RoutineInput {
  name: string
  description: string | null
  exercises: RoutineExerciseInput[]
}

export async function listRoutines(): Promise<RoutineSummary[]> {
  const response = await apiClient.get<RoutineSummary[]>('/routines')
  return response.data
}

export async function getRoutine(id: string): Promise<Routine> {
  const response = await apiClient.get<Routine>(`/routines/${id}`)
  return response.data
}

export async function createRoutine(input: RoutineInput): Promise<Routine> {
  const response = await apiClient.post<Routine>('/routines', input)
  return response.data
}

export async function updateRoutine(id: string, input: RoutineInput): Promise<Routine> {
  const response = await apiClient.put<Routine>(`/routines/${id}`, input)
  return response.data
}

export async function deleteRoutine(id: string): Promise<void> {
  await apiClient.delete(`/routines/${id}`)
}
```

- [ ] **Step 3: React Query hooks — implement**

Create `frontend/src/features/routines/useRoutinesQueries.ts`:
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoutine,
  deleteRoutine,
  getRoutine,
  listRoutines,
  updateRoutine,
  type Routine,
  type RoutineInput,
  type RoutineSummary,
} from '../../api/routinesApi'

const ROUTINES_KEY = ['routines'] as const
const routineKey = (id: string) => ['routines', id] as const

function toSummary(routine: Routine): RoutineSummary {
  return {
    id: routine.id,
    name: routine.name,
    description: routine.description,
    createdAt: routine.createdAt,
    exerciseCount: routine.exercises.length,
  }
}

export function useRoutines() {
  return useQuery({ queryKey: ROUTINES_KEY, queryFn: listRoutines })
}

export function useRoutine(id: string | undefined) {
  return useQuery({
    queryKey: routineKey(id ?? ''),
    queryFn: () => getRoutine(id as string),
    enabled: Boolean(id),
  })
}

export function useCreateRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RoutineInput) => createRoutine(input),
    onSuccess: (created) => {
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) => [...(current ?? []), toSummary(created)])
      queryClient.setQueryData(routineKey(created.id), created)
    },
  })
}

export function useUpdateRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: RoutineInput }) => updateRoutine(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) =>
        (current ?? []).map((routine) => (routine.id === updated.id ? toSummary(updated) : routine)),
      )
      queryClient.setQueryData(routineKey(updated.id), updated)
    },
  })
}

export function useDeleteRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteRoutine,
    onMutate: async (id: string) => {
      await queryClient.cancelQueries({ queryKey: ROUTINES_KEY })
      const previous = queryClient.getQueryData<RoutineSummary[]>(ROUTINES_KEY)
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) => (current ?? []).filter((routine) => routine.id !== id))
      return { previous }
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(ROUTINES_KEY, context.previous)
      }
    },
  })
}
```

- [ ] **Step 4: RoutinesListPage — write tests, verify fail, implement (full rewrite), verify pass**

Create `frontend/src/features/routines/RoutinesListPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { RoutinesListPage } from './RoutinesListPage'

function RoutinesUnderTest() {
  return (
    <Routes>
      <Route path="/routines" element={<RoutinesListPage />} />
      <Route path="/routines/new" element={<p>Construtor de rotina</p>} />
      <Route path="/routines/:id" element={<p>Editar rotina</p>} />
    </Routes>
  )
}

describe('RoutinesListPage', () => {
  it('lists routines with their exercise count', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    expect(await screen.findByText('Treino A')).toBeInTheDocument()
    expect(screen.getByText('1 exercícios')).toBeInTheDocument()
  })

  it('navigates to the builder when "Nova rotina" is clicked', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    await screen.findByText('Treino A')
    await userEvent.click(screen.getByRole('link', { name: 'Nova rotina' }))
    await waitFor(() => expect(screen.getByText('Construtor de rotina')).toBeInTheDocument())
  })

  it('deletes a routine after confirmation, without a page reload', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    await screen.findByText('Treino A')

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))
    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))

    await waitFor(() => expect(screen.queryByText('Treino A')).not.toBeInTheDocument())
  })
})
```

Run: `cd frontend && npm run test -- RoutinesListPage.test.tsx` — expected FAIL (stub page has no interactive elements).

Replace `frontend/src/features/routines/RoutinesListPage.tsx`:
```tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useDeleteRoutine, useRoutines } from './useRoutinesQueries'

export function RoutinesListPage() {
  const { data: routines, isLoading } = useRoutines()
  const deleteRoutine = useDeleteRoutine()
  const { showToast } = useToast()
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  async function handleConfirmDelete() {
    if (!pendingDeleteId) return
    try {
      await deleteRoutine.mutateAsync(pendingDeleteId)
      showToast('Rotina excluída.')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setPendingDeleteId(null)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando rotinas...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Rotinas</h1>
        <Link to="/routines/new">
          <Button>Nova rotina</Button>
        </Link>
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {(routines ?? []).map((routine) => (
          <li key={routine.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{routine.name}</p>
              <p className="font-body text-xs text-muted">{routine.exerciseCount} exercícios</p>
            </div>
            <div className="flex gap-2">
              <Link to={`/routines/${routine.id}`}>
                <Button variant="secondary">Editar</Button>
              </Link>
              <Button variant="destructive" onClick={() => setPendingDeleteId(routine.id)}>
                Excluir
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <Modal open={pendingDeleteId !== null} onOpenChange={(open) => !open && setPendingDeleteId(null)} title="Excluir rotina">
        <p className="font-body text-sm text-ink">Tem certeza que quer excluir esta rotina?</p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDeleteId(null)}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={() => void handleConfirmDelete()}>
            Excluir
          </Button>
        </div>
      </Modal>
    </div>
  )
}
```

Run: `cd frontend && npm run test -- RoutinesListPage.test.tsx` — expected PASS (3 tests).

- [ ] **Step 5: RoutineBuilderPage — write tests, verify fail, implement, verify pass**

The `plannedLoadKg` field is kept as a raw string in the form schema and converted to `number | null` by hand at submit time — `z.coerce.number()` on an empty string is not NaN (`Number('')` is `0`), which would make an *optional* numeric field impossible to express cleanly with a live Zod resolver; hand-converting at submit sidesteps that entirely.

Create `frontend/src/features/routines/RoutineBuilderPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { RoutineBuilderPage } from './RoutineBuilderPage'

function BuilderUnderTest() {
  return (
    <Routes>
      <Route path="/routines/new" element={<RoutineBuilderPage />} />
      <Route path="/routines/:id" element={<RoutineBuilderPage />} />
      <Route path="/routines" element={<p>Lista de rotinas</p>} />
    </Routes>
  )
}

describe('RoutineBuilderPage', () => {
  it('blocks submit with no exercise rows removed down to zero', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/new' })
    await screen.findByText('Nova rotina')

    await userEvent.type(screen.getByLabelText('Nome'), 'Treino C')
    await userEvent.click(screen.getByRole('button', { name: 'Remover' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar rotina' }))

    expect(await screen.findByText('Adicione pelo menos 1 exercício.')).toBeInTheDocument()
  })

  it('adds a row, fills it out, and creates the routine', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/new' })
    await screen.findByText('Nova rotina')

    await userEvent.type(screen.getByLabelText('Nome'), 'Treino C')
    await userEvent.click(screen.getAllByRole('combobox')[0])
    await userEvent.click(await screen.findByText('Supino Reto'))

    await userEvent.click(screen.getByRole('button', { name: 'Salvar rotina' }))
    await waitFor(() => expect(screen.getByText('Lista de rotinas')).toBeInTheDocument())
  })

  it('loads an existing routine into the form for editing', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/routine-1' })
    await waitFor(() => expect(screen.getByDisplayValue('Treino A')).toBeInTheDocument())
    expect(await screen.findByText('Supino Reto')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- RoutineBuilderPage.test.tsx` — expected FAIL.

Create `frontend/src/features/routines/RoutineBuilderPage.tsx`:
```tsx
import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { PlateStat } from '../../components/PlateStat'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useExercises } from '../exercises/useExercisesQueries'
import { useCreateRoutine, useRoutine, useUpdateRoutine } from './useRoutinesQueries'

const routineExerciseSchema = z.object({
  exerciseId: z.string().min(1, 'Selecione um exercício.'),
  plannedSets: z.coerce.number().int().min(1, 'Mínimo 1.'),
  plannedReps: z.coerce.number().int().min(1, 'Mínimo 1.'),
  plannedLoadKg: z.string(),
})

const routineSchema = z.object({
  name: z.string().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  description: z.string().max(2000, 'Descrição muito longa.').nullable(),
  exercises: z.array(routineExerciseSchema).min(1, 'Adicione pelo menos 1 exercício.'),
})

type RoutineFormValues = z.infer<typeof routineSchema>

const EMPTY_ROW: RoutineFormValues['exercises'][number] = {
  exerciseId: '',
  plannedSets: 3,
  plannedReps: 10,
  plannedLoadKg: '',
}

function toNullableLoad(raw: string): number | null {
  const trimmed = raw.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function RoutineBuilderPage() {
  const { id } = useParams<{ id: string }>()
  const isEditing = Boolean(id)
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: exercises } = useExercises()
  const { data: existingRoutine, isLoading: isLoadingRoutine } = useRoutine(id)
  const createRoutine = useCreateRoutine()
  const updateRoutine = useUpdateRoutine()

  const {
    register,
    control,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RoutineFormValues>({
    resolver: zodResolver(routineSchema),
    defaultValues: { name: '', description: null, exercises: [EMPTY_ROW] },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'exercises' })

  useEffect(() => {
    if (existingRoutine) {
      reset({
        name: existingRoutine.name,
        description: existingRoutine.description,
        exercises: existingRoutine.exercises
          .slice()
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map((entry) => ({
            exerciseId: entry.exerciseId,
            plannedSets: entry.plannedSets,
            plannedReps: entry.plannedReps,
            plannedLoadKg: entry.plannedLoadKg === null ? '' : String(entry.plannedLoadKg),
          })),
      })
    }
  }, [existingRoutine, reset])

  async function onSubmit(values: RoutineFormValues) {
    const payload = {
      name: values.name,
      description: values.description,
      exercises: values.exercises.map((row) => ({
        exerciseId: row.exerciseId,
        plannedSets: row.plannedSets,
        plannedReps: row.plannedReps,
        plannedLoadKg: toNullableLoad(row.plannedLoadKg),
      })),
    }
    try {
      if (isEditing && id) {
        await updateRoutine.mutateAsync({ id, input: payload })
        showToast('Rotina atualizada.')
      } else {
        await createRoutine.mutateAsync(payload)
        showToast('Rotina criada.')
      }
      navigate('/routines')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  if (isEditing && isLoadingRoutine) {
    return <p className="font-body text-muted">Carregando rotina...</p>
  }

  const exerciseOptions = (exercises ?? []).map((exercise) => ({ value: exercise.id, label: exercise.name }))

  return (
    <div>
      <h1 className="font-display text-2xl font-bold text-ink">{isEditing ? 'Editar rotina' : 'Nova rotina'}</h1>

      <form className="mt-6 flex flex-col gap-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
          <Input id="name" hasError={!!errors.name} {...register('name')} />
        </FormField>
        <FormField label="Descrição" htmlFor="description" error={errors.description?.message}>
          <Input id="description" hasError={!!errors.description} {...register('description')} />
        </FormField>

        <div>
          <div className="flex items-center justify-between">
            <p className="font-body text-xs font-semibold uppercase tracking-wide text-muted">Exercícios</p>
            <Button type="button" variant="secondary" onClick={() => append(EMPTY_ROW)}>
              Adicionar exercício
            </Button>
          </div>
          {errors.exercises?.root && (
            <p role="alert" className="mt-2 font-body text-xs text-accent">
              {errors.exercises.root.message}
            </p>
          )}
          {typeof errors.exercises?.message === 'string' && (
            <p role="alert" className="mt-2 font-body text-xs text-accent">
              {errors.exercises.message}
            </p>
          )}

          <ul className="mt-3 flex flex-col gap-3">
            {fields.map((field, index) => (
              <li key={field.id} className="rounded-sm border border-line bg-surface p-4">
                <div className="flex items-start gap-3">
                  <div className="flex-1">
                    <FormField
                      label="Exercício"
                      htmlFor={`exercises.${index}.exerciseId`}
                      error={errors.exercises?.[index]?.exerciseId?.message}
                    >
                      <Select
                        id={`exercises.${index}.exerciseId`}
                        value={watch(`exercises.${index}.exerciseId`)}
                        onValueChange={(value) => setValue(`exercises.${index}.exerciseId`, value, { shouldValidate: true })}
                        options={exerciseOptions}
                        hasError={!!errors.exercises?.[index]?.exerciseId}
                      />
                    </FormField>
                  </div>
                  <Button type="button" variant="destructive" onClick={() => remove(index)}>
                    Remover
                  </Button>
                </div>

                <div className="mt-3 flex gap-3">
                  <FormField
                    label="Séries"
                    htmlFor={`exercises.${index}.plannedSets`}
                    error={errors.exercises?.[index]?.plannedSets?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedSets`}
                      type="number"
                      hasError={!!errors.exercises?.[index]?.plannedSets}
                      {...register(`exercises.${index}.plannedSets`)}
                    />
                  </FormField>
                  <FormField
                    label="Reps"
                    htmlFor={`exercises.${index}.plannedReps`}
                    error={errors.exercises?.[index]?.plannedReps?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedReps`}
                      type="number"
                      hasError={!!errors.exercises?.[index]?.plannedReps}
                      {...register(`exercises.${index}.plannedReps`)}
                    />
                  </FormField>
                  <FormField
                    label="Carga (kg)"
                    htmlFor={`exercises.${index}.plannedLoadKg`}
                    error={errors.exercises?.[index]?.plannedLoadKg?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedLoadKg`}
                      type="number"
                      step="0.01"
                      hasError={!!errors.exercises?.[index]?.plannedLoadKg}
                      {...register(`exercises.${index}.plannedLoadKg`)}
                    />
                  </FormField>
                  <div className="flex items-end pb-2">
                    <PlateStat value={watch(`exercises.${index}.plannedLoadKg`) || '-'} unit="kg" />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => navigate('/routines')}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            Salvar rotina
          </Button>
        </div>
      </form>
    </div>
  )
}
```

Run: `cd frontend && npm run test -- RoutineBuilderPage.test.tsx` — expected PASS (3 tests).

- [ ] **Step 6: Wire the builder routes into App.tsx**

In `frontend/src/App.tsx`, add the import `import { RoutineBuilderPage } from './features/routines/RoutineBuilderPage'` and insert two more protected routes after the `/routines` route:
```tsx
      <Route
        path="/routines/new"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RoutineBuilderPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/routines/:id"
        element={
          <ProtectedRoute>
            <AppLayout>
              <RoutineBuilderPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 7: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/test/mocks/handlers.ts frontend/src/test/setup.ts frontend/src/api/routinesApi.ts frontend/src/features/routines frontend/src/App.tsx
git commit -m "feat(frontend): CRUD de rotinas com construtor dinamico de exercicios"
```

---

### Task 8: Wrap-up

**Files:**
- Modify: `README.md`

**Interfaces:** none — documentation and verification only.

- [ ] **Step 1: Full automated verification**

Run from `frontend/`:
```bash
npm run test && npm run lint && npm run build
```
Expected: all three green, zero warnings treated as errors by `oxlint`.

Run backend + frontend together for the manual smoke test:
```bash
docker compose up -d
cd backend && GYMTRACKER_SEED_DEMO=true JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw quarkus:dev
```
(in a second terminal) 
```bash
cd frontend && npm run dev
```

- [ ] **Step 2: Manual smoke test (golden path + edge cases) — record results, don't just eyeball it**

Open `http://localhost:5173` and walk through, noting pass/fail for each:
1. `/` redirects to `/login` when logged out.
2. Click "Entrar como visitante (conta demo)" → lands on `/exercises`, nav shows "Conta Demo" + Sair.
3. `/exercises` lists the 6 seeded global exercises (badge "Catálogo", no edit/excluir buttons).
4. Create a custom exercise ("Rosca Scott", grupo Bíceps) → appears in the list immediately, has edit/excluir buttons.
5. Edit that custom exercise's name → list updates immediately, no flash.
6. Try to delete a seeded exercise that's used by a seeded routine/session → toast shows the `EXERCISE_IN_USE` message, item stays in the list.
7. Delete the custom exercise created in step 4 → disappears immediately.
8. `/routines` lists the 2 seeded routines ("Treino A"/"Treino B") with correct exercise counts.
9. Click "Nova rotina" → builder loads, add 2 exercise rows, fill sets/reps/carga, submit → redirects to `/routines`, new routine appears in the list.
10. Click "Editar" on the routine just created → form is pre-filled correctly, remove one row, save → list reflects the updated count.
11. Log out (Sair) → redirected to `/login`, refresh the page → still logged out (no stale session).
12. Log back in with the demo button, then hard-refresh the browser (F5) on `/exercises` → session survives (silent refresh on boot), still logged in.
13. Register a brand-new account (unique email) → auto-logs in, lands on `/exercises` with an empty custom list (only global catalog visible).
14. Open `/q/swagger-ui` on the backend (`http://localhost:8080/q/swagger-ui`) — unrelated to this sprint's frontend work, just confirm Sprint 4's OpenAPI docs still render (regression check, zero backend files were touched this sprint).

If any step fails, fix the root cause before proceeding — do not mark the task done with a known-broken smoke test step.

- [ ] **Step 3: Update README**

In `README.md`, replace the "Como rodar localmente" frontend block to mention the dev proxy (no manual `VITE_API_BASE_URL` needed in dev):
```markdown
# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev      # chamadas de API são encaminhadas pro backend via proxy do Vite (vite.config.ts), não precisa configurar VITE_API_BASE_URL em dev
```

Add a new subsection right after the existing "Testes" section:
```markdown
### Frontend

```bash
cd frontend
npm run lint    # oxlint
npm run test    # Vitest + Testing Library + MSW
npm run build   # tsc -b && vite build
```
```

Update the Status checklist:
```markdown
- [x] Sprint 5a — Frontend Core (Fundação + Auth + Exercícios + Rotinas)
- [ ] Sprint 5b — Frontend Sessões + Dashboard (Recharts)
```
(replace the old single unchecked `- [ ] Sprint 5 — Frontend Core` line with these two)

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: documentar setup do frontend e marcar Sprint 5a completa"
```

---

## Verification (whole branch)

- `cd backend && ./mvnw test && ./mvnw verify` — still green, unchanged from the `develop` baseline (no backend files touched this sprint).
- `cd frontend && npm run test && npm run lint && npm run build` — all green.
- Manual smoke test (Task 8, Step 2) — all 14 steps pass.
- `git log --oneline a4d61af..HEAD` (or the branch's actual base commit) shows 8 commits, one per task, each with a working, independently-testable state.

