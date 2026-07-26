import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Button } from '../components/Button'
import { useAuth } from '../context/AuthContext'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/sessions', label: 'Sessões' },
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
