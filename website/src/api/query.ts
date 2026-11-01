import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react'

type Snapshot<T> = {
  data: T | undefined
  error: unknown
  loading: boolean
}

type Entry = {
  snapshot: Snapshot<unknown>
  promise: Promise<void> | null
  updatedAt: number
  listeners: Set<() => void>
}

export type ApiQuery<T> = Snapshot<T> & {
  refresh: () => Promise<void>
}

const entries = new Map<string, Entry>()
const EMPTY: Snapshot<never> = { data: undefined, error: null, loading: false }

function entryOf(key: string): Entry {
  let entry = entries.get(key)
  if (!entry) {
    entry = {
      snapshot: { data: undefined, error: null, loading: true },
      promise: null,
      updatedAt: 0,
      listeners: new Set(),
    }
    entries.set(key, entry)
  }
  return entry
}

function emit(entry: Entry): void {
  for (const listener of entry.listeners) {
    listener()
  }
}

function load<T>(key: string, fetcher: () => Promise<T>): Promise<void> {
  const entry = entryOf(key)
  if (entry.promise) {
    return entry.promise
  }
  const previous = entry.snapshot as Snapshot<T>
  entry.snapshot = { data: previous.data, error: null, loading: previous.data === undefined }
  emit(entry)
  entry.promise = fetcher()
    .then((data) => {
      entry.snapshot = { data, error: null, loading: false }
      entry.updatedAt = Date.now()
    })
    .catch((error: unknown) => {
      entry.snapshot = { data: previous.data, error, loading: false }
    })
    .finally(() => {
      entry.promise = null
      emit(entry)
    })
  return entry.promise
}

export function invalidateQuery(prefix: string): void {
  for (const [key, entry] of entries) {
    if (key.startsWith(prefix)) {
      entry.updatedAt = 0
    }
  }
}

export function useApiQuery<T>(
  key: string | null,
  fetcher: () => Promise<T>,
  options: { staleMs?: number; pollMs?: number } = {},
): ApiQuery<T> {
  const { staleMs = 10_000, pollMs = 0 } = options
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const subscribe = useCallback(
    (onChange: () => void) => {
      if (!key) {
        return () => {}
      }
      const entry = entryOf(key)
      entry.listeners.add(onChange)
      return () => {
        entry.listeners.delete(onChange)
      }
    },
    [key],
  )

  const getSnapshot = useCallback(
    () => (key ? (entryOf(key).snapshot as Snapshot<T>) : (EMPTY as Snapshot<T>)),
    [key],
  )

  const snapshot = useSyncExternalStore(subscribe, getSnapshot)

  useEffect(() => {
    if (!key) {
      return
    }
    const entry = entryOf(key)
    if (!entry.promise && (entry.snapshot.data === undefined || Date.now() - entry.updatedAt > staleMs)) {
      void load(key, () => fetcherRef.current())
    }
  }, [key, staleMs])

  useEffect(() => {
    if (!key || pollMs <= 0) {
      return
    }
    const tick = () => {
      if (document.visibilityState === 'visible') {
        void load(key, () => fetcherRef.current())
      }
    }
    const timer = window.setInterval(tick, pollMs)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [key, pollMs])

  const refresh = useCallback(async () => {
    if (!key) {
      return
    }
    await load(key, () => fetcherRef.current())
  }, [key])

  return { ...snapshot, refresh }
}
