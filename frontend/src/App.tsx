import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './routes/AppLayout'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { ExercisesListPage } from './features/exercises/ExercisesListPage'
import { RoutinesListPage } from './features/routines/RoutinesListPage'
import { RoutineBuilderPage } from './features/routines/RoutineBuilderPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { SessionsListPage } from './features/sessions/SessionsListPage'
import { SessionDetailPage } from './features/sessions/SessionDetailPage'
import { useAuth } from './context/AuthContext'

function RedirectRoot() {
  const { user, isLoading } = useAuth()
  if (isLoading) {
    return null
  }
  return <Navigate to={user ? '/dashboard' : '/login'} replace />
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
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <AppLayout>
              <DashboardPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions"
        element={
          <ProtectedRoute>
            <AppLayout>
              <SessionsListPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions/:id"
        element={
          <ProtectedRoute>
            <AppLayout>
              <SessionDetailPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App