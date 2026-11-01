import { createContext, use } from 'react'
import type { ChatActions, ChatMeta, ChatShell, ChatThread } from './types'

export const ThreadContext = createContext<ChatThread | null>(null)
export const ShellContext = createContext<ChatShell | null>(null)
export const ActionsContext = createContext<ChatActions | null>(null)
export const MetaContext = createContext<ChatMeta | null>(null)

function required<T>(value: T | null, name: string): T {
  if (value === null) {
    throw new Error(`${name} 必须在 ChatProvider 内使用`)
  }
  return value
}

export function useChatThread(): ChatThread {
  return required(use(ThreadContext), 'useChatThread')
}

export function useChatShell(): ChatShell {
  return required(use(ShellContext), 'useChatShell')
}

export function useChatActions(): ChatActions {
  return required(use(ActionsContext), 'useChatActions')
}

export function useChatMeta(): ChatMeta {
  return required(use(MetaContext), 'useChatMeta')
}
