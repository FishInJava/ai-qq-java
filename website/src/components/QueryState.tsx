import type { ReactNode } from 'react'
import { errorMessage } from '../lib/errors'

type Props = {
  loading: boolean
  error: unknown
  empty?: boolean
  emptyText?: ReactNode
  children: ReactNode
}

export function QueryState({ loading, error, empty = false, emptyText, children }: Props) {
  if (error) {
    return <p className="notice bad">{errorMessage(error)}</p>
  }
  if (loading) {
    return <p className="empty">读取中</p>
  }
  if (empty) {
    return <p className="empty">{emptyText}</p>
  }
  return <>{children}</>
}
