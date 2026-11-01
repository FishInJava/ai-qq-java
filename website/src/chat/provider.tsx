import { startTransition, useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, streamChat, type Citation } from '../api/client'
import { useApiQuery } from '../api/query'
import { errorMessage } from '../lib/errors'
import { loadPrefs, savePrefs } from '../lib/prefs'
import { ActionsContext, MetaContext, ShellContext, ThreadContext } from './context'
import type { ChatActions, ChatMeta, ChatShell, ChatThread, Preview, Stage, UiMessage } from './types'

function newMessage(role: 'user' | 'assistant', content: string, question?: string): UiMessage {
  return {
    key: crypto.randomUUID(),
    role,
    content,
    queryId: '',
    createdAt: new Date().toISOString(),
    question,
  }
}

function patchAssistant(previous: UiMessage[], patch: (message: UiMessage) => UiMessage): UiMessage[] {
  const next = [...previous]
  for (let index = next.length - 1; index >= 0; index -= 1) {
    if (next[index].role === 'assistant') {
      next[index] = patch(next[index])
      break
    }
  }
  return next
}

function replaceLastAssistant(previous: UiMessage[], question: string): UiMessage[] {
  const next = [...previous]
  for (let index = next.length - 1; index >= 0; index -= 1) {
    if (next[index].role === 'assistant') {
      next[index] = { ...newMessage('assistant', '', question), strict: true }
      break
    }
  }
  return next
}

export function ChatProvider({ children }: { children: ReactNode }) {
  const sessionsQuery = useApiQuery('/api/sessions', api.sessions)
  const documentsQuery = useApiQuery('/api/documents', api.documents)
  const refreshSessions = sessionsQuery.refresh

  const [activeSession, setActiveSession] = useState<string | null>(null)
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stages, setStages] = useState<Stage[]>([])
  const [preview, setPreview] = useState<Preview | null>(null)
  const [useRag, setUseRagState] = useState(() => loadPrefs().useRag)
  const [documentId, setDocumentId] = useState<number | null>(null)
  const [draftKey, setDraftKey] = useState(0)
  const bottomRef = useRef<HTMLDivElement>(null)

  const latest = useRef({ activeSession, useRag, documentId, sending })
  latest.current = { activeSession, useRag, documentId, sending }

  const sessions = useMemo(() => sessionsQuery.data?.items ?? [], [sessionsQuery.data])
  const documents = useMemo(
    () => (documentsQuery.data?.items ?? []).filter((item) => item.status === 'ready'),
    [documentsQuery.data],
  )

  const runChat = useCallback(
    async (question: string, strict: boolean) => {
      const current = latest.current
      setError(null)
      setStages([])
      setSending(true)

      startTransition(() => {
        setMessages((previous) =>
          strict
            ? replaceLastAssistant(previous, question)
            : [...previous, newMessage('user', question), newMessage('assistant', '', question)],
        )
      })

      try {
        await streamChat(
          {
            question,
            sessionId: current.activeSession,
            useRag: strict || current.useRag,
            strictNumbers: strict,
            documentId: current.documentId,
          },
          {
            start: (data) => {
              setActiveSession(data.sessionId)
              setMessages((previous) =>
                patchAssistant(previous, (message) => ({ ...message, queryId: data.queryId })),
              )
              void refreshSessions()
            },
            stage: (data) => setStages((previous) => [...previous, data]),
            citation: (data) =>
              setMessages((previous) =>
                patchAssistant(previous, (message) => ({
                  ...message,
                  citations: [...(message.citations ?? []), data],
                })),
              ),
            delta: (data) =>
              setMessages((previous) =>
                patchAssistant(previous, (message) => ({
                  ...message,
                  content: message.content + data.text,
                })),
              ),
            number_check: (data) =>
              setMessages((previous) => patchAssistant(previous, (message) => ({ ...message, numberCheck: data }))),
            done: (data) =>
              setMessages((previous) =>
                patchAssistant(previous, (message) => ({
                  ...message,
                  latencyMs: data.latencyMs,
                  firstTokenMs: data.firstTokenMs,
                  refused: data.refused,
                })),
              ),
            error: (data) => setError(data.message),
          },
        )
        void refreshSessions()
      } catch (reason) {
        setError(errorMessage(reason))
      } finally {
        setSending(false)
      }
    },
    [refreshSessions],
  )

  const send = useCallback(
    (question: string) => {
      const trimmed = question.trim()
      if (!trimmed || latest.current.sending) {
        return
      }
      void runChat(trimmed, false)
    },
    [runChat],
  )

  const retryStrict = useCallback(
    (message: UiMessage) => {
      if (!message.question || latest.current.sending) {
        return
      }
      void runChat(message.question, true)
    },
    [runChat],
  )

  const newChat = useCallback(() => {
    setActiveSession(null)
    setMessages([])
    setStages([])
    setError(null)
    setPreview(null)
    setDraftKey((current) => current + 1)
  }, [])

  const openSession = useCallback((id: string) => {
    setActiveSession(id)
    setStages([])
    setError(null)
    setPreview(null)
    void (async () => {
      try {
        const result = await api.messages(id)
        startTransition(() => {
          setMessages(
            result.items.map((item) => ({
              ...item,
              key: crypto.randomUUID(),
              latencyMs: item.latencyMs ?? undefined,
              firstTokenMs: item.firstTokenMs ?? undefined,
              numberCheck: item.numberCheck ?? undefined,
            })),
          )
        })
      } catch (reason) {
        setError(errorMessage(reason))
      }
    })()
  }, [])

  const openCitation = useCallback((citation: Citation) => {
    void (async () => {
      try {
        const result = await api.chunkBoxes(citation.chunkId)
        startTransition(() => {
          setPreview({ documentId: result.documentId, page: citation.pageFrom, boxes: result.boxes })
        })
      } catch (reason) {
        setError(errorMessage(reason))
      }
    })()
  }, [])

  const closePreview = useCallback(() => {
    startTransition(() => {
      setPreview(null)
    })
  }, [])

  const setPreviewPage = useCallback((page: number) => {
    setPreview((current) => (current ? { ...current, page } : current))
  }, [])

  const setUseRag = useCallback((next: boolean) => {
    setUseRagState(next)
    savePrefs({ useRag: next })
  }, [])

  const feedback = useCallback((message: UiMessage, verdict: string) => {
    if (!message.queryId) {
      return
    }
    void (async () => {
      try {
        await api.feedback(message.queryId, verdict)
        setMessages((previous) =>
          previous.map((item) => (item.queryId === message.queryId ? { ...item, feedback: verdict } : item)),
        )
      } catch (reason) {
        setError(errorMessage(reason))
      }
    })()
  }, [])

  const actions = useMemo<ChatActions>(
    () => ({
      send,
      retryStrict,
      newChat,
      openSession,
      openCitation,
      closePreview,
      setPreviewPage,
      setUseRag,
      setDocumentId,
      feedback,
    }),
    [send, retryStrict, newChat, openSession, openCitation, closePreview, setPreviewPage, setUseRag, feedback],
  )

  const thread = useMemo<ChatThread>(() => ({ messages, sending }), [messages, sending])

  const shell = useMemo<ChatShell>(
    () => ({ sessions, activeSession, documents, useRag, documentId, stages, error, preview, draftKey }),
    [sessions, activeSession, documents, useRag, documentId, stages, error, preview, draftKey],
  )

  const meta = useMemo<ChatMeta>(() => ({ bottomRef }), [])

  return (
    <ActionsContext value={actions}>
      <ShellContext value={shell}>
        <ThreadContext value={thread}>
          <MetaContext value={meta}>{children}</MetaContext>
        </ThreadContext>
      </ShellContext>
    </ActionsContext>
  )
}
