import { addTransitionType, startTransition, ViewTransition, type MouseEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

const PAGE_TYPES = {
  'nav-forward': 'nav-forward',
  'nav-back': 'nav-back',
  default: 'none',
}

export function DirectionalTransition({ children }: { children: ReactNode }) {
  return (
    <ViewTransition enter={PAGE_TYPES} exit={PAGE_TYPES} default="none">
      {children}
    </ViewTransition>
  )
}

type DirectionalLinkProps = {
  to: string
  state?: unknown
  className?: string
  title?: string
  preload?: () => Promise<unknown>
  children: ReactNode
}

function isPlainClick(event: MouseEvent<HTMLAnchorElement>): boolean {
  return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey
}

export function ForwardLink({ to, state, className, title, preload, children }: DirectionalLinkProps) {
  const navigate = useNavigate()
  return (
    <a
      href={to}
      className={className}
      title={title}
      onMouseEnter={preload ? () => void preload() : undefined}
      onFocus={preload ? () => void preload() : undefined}
      onClick={(event) => {
        if (!isPlainClick(event)) {
          return
        }
        event.preventDefault()
        startTransition(() => {
          addTransitionType('nav-forward')
          navigate(to, { state })
        })
      }}
    >
      {children}
    </a>
  )
}

export function BackLink({ to, className, children }: DirectionalLinkProps) {
  const navigate = useNavigate()
  return (
    <a
      href={to}
      className={className}
      onClick={(event) => {
        if (!isPlainClick(event)) {
          return
        }
        event.preventDefault()
        startTransition(() => {
          addTransitionType('nav-back')
          navigate(to)
        })
      }}
    >
      {children}
    </a>
  )
}
