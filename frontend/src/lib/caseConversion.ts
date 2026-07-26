function snakeToCamelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase())
}

function camelToSnakeKey(key: string): string {
  return key.replace(/[A-Z]/g, (char) => `_${char.toLowerCase()}`)
}

function convertKeys<T>(value: T, convertKey: (key: string) => string): T {
  if (Array.isArray(value)) {
    return value.map((item) => convertKeys(item, convertKey)) as unknown as T
  }
  if (value !== null && typeof value === 'object' && !(value instanceof Date)) {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, val]) => [
        convertKey(key),
        convertKeys(val, convertKey),
      ]),
    ) as T
  }
  return value
}

export function toCamelCase<T>(value: T): T {
  return convertKeys(value, snakeToCamelKey)
}

export function toSnakeCase<T>(value: T): T {
  return convertKeys(value, camelToSnakeKey)
}
