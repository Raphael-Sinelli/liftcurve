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
