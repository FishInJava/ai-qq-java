import { Fragment, type ReactNode } from 'react'

const regexCache = new Map<string, RegExp>()

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function regexOf(pattern: string): RegExp {
  const cached = regexCache.get(pattern)
  if (cached) {
    return cached
  }
  const regex = new RegExp(`(${pattern})`)
  if (regexCache.size >= 100) {
    regexCache.clear()
  }
  regexCache.set(pattern, regex)
  return regex
}

export function markMissingNumbers(text: string, missing: string[]): ReactNode {
  if (missing.length === 0) {
    return text
  }
  const values = new Set(missing)
  const pattern = [...values]
    .sort((left, right) => right.length - left.length)
    .map(escapeRegExp)
    .join('|')
  if (!pattern) {
    return text
  }
  return text.split(regexOf(pattern)).map((part, index) =>
    values.has(part) ? (
      <mark key={index} className="number-missing" title="原文无此数字">
        {part}
      </mark>
    ) : (
      <Fragment key={index}>{part}</Fragment>
    ),
  )
}
