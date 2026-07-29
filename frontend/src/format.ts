export function formatDateTime(value: string, locale?: string) {
  return new Intl.DateTimeFormat(activeLocale(locale), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatDate(value: string, locale?: string) {
  return new Intl.DateTimeFormat(activeLocale(locale), {
    dateStyle: 'medium',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}

export function formatMoney(value: number, locale?: string) {
  return new Intl.NumberFormat(activeLocale(locale), {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

export function formatNullableMoney(value: number | null, locale?: string) {
  return value === null
    ? localizedFormatLabel('unavailable', locale)
    : formatMoney(value, locale)
}

export function formatDecimal(value: number, locale?: string) {
  return new Intl.NumberFormat(activeLocale(locale), {
    maximumFractionDigits: 2,
  }).format(value)
}

export function formatSignedDecimal(value: number, locale?: string) {
  return `${value > 0 ? '+' : value < 0 ? '−' : '±'}${formatDecimal(
    Math.abs(value),
    locale,
  )}`
}

export function formatSignedMoney(value: number | null, locale?: string) {
  if (value === null) return localizedFormatLabel('unavailable', locale)
  const prefix = value > 0 ? '+' : value < 0 ? '−' : '±'
  return `${prefix}${formatMoney(Math.abs(value), locale)} ${toneLabel(
    value,
    locale,
  )}`
}

export function formatSignedPercent(value: number | null, locale?: string) {
  if (value === null) return localizedFormatLabel('unavailable', locale)
  const prefix = value > 0 ? '+' : value < 0 ? '−' : '±'
  const percent = new Intl.NumberFormat(activeLocale(locale), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value))
  return `${prefix}${percent}% ${toneLabel(value, locale)}`
}

function toneLabel(value: number, locale?: string) {
  return value > 0
    ? localizedFormatLabel('gain', locale)
    : value < 0
      ? localizedFormatLabel('loss', locale)
      : localizedFormatLabel('flat', locale)
}

function activeLocale(locale?: string) {
  if (locale) return locale
  const language = document.documentElement.lang
  if (language === 'zh-CN') return 'zh-CN'
  if (language === 'pt-BR') return 'pt-BR'
  return 'en-US'
}

function localizedFormatLabel(
  key: 'unavailable' | 'gain' | 'loss' | 'flat',
  locale?: string,
) {
  const labels = {
    en: {
      unavailable: 'Unavailable',
      gain: 'Gain',
      loss: 'Loss',
      flat: 'Flat',
    },
    'zh-CN': {
      unavailable: '不可用',
      gain: '盈利',
      loss: '亏损',
      flat: '持平',
    },
    'pt-BR': {
      unavailable: 'Indisponível',
      gain: 'Ganho',
      loss: 'Perda',
      flat: 'Estável',
    },
  } as const
  const language = locale ?? document.documentElement.lang
  const labelLocale =
    language === 'zh-CN' || language === 'pt-BR' ? language : 'en'
  return labels[labelLocale][key]
}
