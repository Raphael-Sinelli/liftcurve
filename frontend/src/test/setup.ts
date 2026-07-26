import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setAccessToken } from '../lib/tokenStore'
import { server } from './mocks/server'
import { resetExercisesFixture, resetRoutinesFixture, resetSessionsFixture } from './mocks/handlers'

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
  cleanup()
  server.resetHandlers()
  localStorage.clear()
  setAccessToken(null)
  resetExercisesFixture()
  resetRoutinesFixture()
  resetSessionsFixture()
})
afterAll(() => server.close())
