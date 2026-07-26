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
