const KEY = 'justrag:prefs:v1'

export type Prefs = {
  useRag: boolean
}

const DEFAULT: Prefs = { useRag: true }

export function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) {
      return DEFAULT
    }
    const parsed = JSON.parse(raw) as Partial<Prefs>
    return { useRag: typeof parsed.useRag === 'boolean' ? parsed.useRag : DEFAULT.useRag }
  } catch {
    return DEFAULT
  }
}

export function savePrefs(prefs: Prefs): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(prefs))
  } catch {
    // 存储失败时忽略
  }
}
