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
