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
