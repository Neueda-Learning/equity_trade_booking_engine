export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 6,
  }).format(value)
}

export function formatNullableMoney(value: number | null) {
  return value === null ? 'Unavailable' : formatMoney(value)
}

export function formatDecimal(value: number) {
  return new Intl.NumberFormat(undefined, {
    maximumFractionDigits: 6,
  }).format(value)
}

export function formatSignedDecimal(value: number) {
  return `${value > 0 ? '+' : value < 0 ? '−' : '±'}${formatDecimal(
    Math.abs(value),
  )}`
}

export function formatSignedMoney(value: number | null) {
  if (value === null) return 'Unavailable'
  const prefix = value > 0 ? '+' : value < 0 ? '−' : '±'
  return `${prefix}${formatMoney(Math.abs(value))} ${toneLabel(value)}`
}

export function formatSignedPercent(value: number | null) {
  if (value === null) return 'Unavailable'
  const prefix = value > 0 ? '+' : value < 0 ? '−' : '±'
  return `${prefix}${Math.abs(value).toFixed(2)}% ${toneLabel(value)}`
}

function toneLabel(value: number) {
  return value > 0 ? 'Gain' : value < 0 ? 'Loss' : 'Flat'
}
