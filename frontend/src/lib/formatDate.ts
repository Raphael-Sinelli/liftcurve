export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatShortDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short' }).format(new Date(iso))
}
