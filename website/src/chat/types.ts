import type { RefObject } from 'react'
import type { ChatMessage, ChunkBox, Citation, DocumentSummary, NumberCheck, Session } from '../api/client'

export type UiMessage = ChatMessage & {
  key: string
  citations?: Citation[]
  numberCheck?: NumberCheck
  question?: string
  latencyMs?: number
  firstTokenMs?: number
  refused?: boolean
  feedback?: string
  strict?: boolean
}

export type Preview = {
  documentId: number
  page: number
  boxes: ChunkBox[]
}

export type Stage = {
  stage: string
  ms: number
}

export type ChatThread = {
  messages: UiMessage[]
  sending: boolean
}

export type ChatShell = {
  sessions: Session[]
  activeSession: string | null
  documents: DocumentSummary[]
  useRag: boolean
  documentId: number | null
  stages: Stage[]
  error: string | null
  preview: Preview | null
  draftKey: number
}

export type ChatActions = {
  send: (question: string) => void
  retryStrict: (message: UiMessage) => void
  newChat: () => void
  openSession: (id: string) => void
  openCitation: (citation: Citation) => void
  closePreview: () => void
  setPreviewPage: (page: number) => void
  setUseRag: (next: boolean) => void
  setDocumentId: (id: number | null) => void
  feedback: (message: UiMessage, verdict: string) => void
}

export type ChatMeta = {
  bottomRef: RefObject<HTMLDivElement | null>
}
