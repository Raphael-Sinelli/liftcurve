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
