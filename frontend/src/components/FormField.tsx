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
