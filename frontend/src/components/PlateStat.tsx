interface PlateStatProps {
  value: number | string
  unit: string
}

export function PlateStat({ value, unit }: PlateStatProps) {
  return (
    <span className="inline-flex items-baseline gap-1 rounded-sm border border-line bg-surface px-2 py-1 font-mono text-sm tabular-nums text-ink">
      {value}
      <span className="text-[0.6rem] font-body uppercase tracking-wide text-muted">{unit}</span>
    </span>
  )
}
