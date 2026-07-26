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
