import { Fragment, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  streamChat,
  type ChatMessage,
  type ChunkBox,
  type Citation,
  type DocumentSummary,
  type NumberCheck,
  type Session,
} from '../api/client'
import PdfPreview from '../components/PdfPreview'

type UiMessage = ChatMessage & {
  citations?: Citation[]
  numberCheck?: NumberCheck
  question?: string
  latencyMs?: number
  firstTokenMs?: number
  refused?: boolean
  feedback?: string
  strict?: boolean
}

type Preview = {
  documentId: number
  page: number
  boxes: ChunkBox[]
}

const RAG_KEY = 'justrag.useRag'

function markNumbers(text: string, missing: string[]): ReactNode {
  if (missing.length === 0) {
    return text
  }
  const pattern = missing
    .map((value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .sort((left, right) => right.length - left.length)
    .join('|')
  const parts = text.split(new RegExp(`(${pattern})`, 'g'))
  return parts.map((part, index) =>
    missing.includes(part) ? (
      <mark key={index} className="number-missing" title="原文无此数字">
        {part}
      </mark>
    ) : (
      <Fragment key={index}>{part}</Fragment>
    ),
  )
}

export default function ChatPage() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [activeSession, setActiveSession] = useState<string | null>(null)
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [input, setInput] = useState('')
  const [useRag, setUseRag] = useState(() => localStorage.getItem(RAG_KEY) !== 'false')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stages, setStages] = useState<Array<{ stage: string; ms: number }>>([])
  const [preview, setPreview] = useState<Preview | null>(null)
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [documentId, setDocumentId] = useState<number | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  const refreshSessions = useCallback(() => {
    api
      .sessions()
      .then((result) => setSessions(result.items))
      .catch(() => setSessions([]))
  }, [])

  useEffect(refreshSessions, [refreshSessions])

  useEffect(() => {
    api
      .documents()
      .then((result) => setDocuments(result.items.filter((item) => item.status === 'ready')))
      .catch(() => setDocuments([]))
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  const toggleRag = (next: boolean) => {
    setUseRag(next)
    localStorage.setItem(RAG_KEY, String(next))
  }

  const openSession = async (id: string) => {
    setActiveSession(id)
    setStages([])
    setError(null)
    setPreview(null)
    try {
      const result = await api.messages(id)
      setMessages(result.items.map((item) => ({ ...item })))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  const newChat = () => {
    setActiveSession(null)
    setMessages([])
    setStages([])
    setError(null)
    setPreview(null)
    setInput('')
  }

  const openCitation = async (citation: Citation) => {
    try {
      const result = await api.chunkBoxes(citation.chunkId)
      setPreview({ documentId: result.documentId, page: citation.pageFrom, boxes: result.boxes })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  const patchAssistant = (patch: (message: UiMessage) => UiMessage) => {
    setMessages((previous) => {
      const next = [...previous]
      for (let index = next.length - 1; index >= 0; index--) {
        if (next[index].role === 'assistant') {
          next[index] = patch(next[index])
          break
        }
      }
      return next
    })
  }

  const runChat = async (question: string, strict: boolean) => {
    setError(null)
    setStages([])
    setSending(true)

    setMessages((previous) => {
      if (strict) {
        const next = [...previous]
        for (let index = next.length - 1; index >= 0; index--) {
          if (next[index].role === 'assistant') {
            next[index] = {
              role: 'assistant',
              content: '',
              queryId: '',
              createdAt: new Date().toISOString(),
              citations: [],
              question,
              strict: true,
            }
            break
          }
        }
        return next
      }
      return [
        ...previous,
        { role: 'user', content: question, queryId: '', createdAt: new Date().toISOString() },
        {
          role: 'assistant',
          content: '',
          queryId: '',
          createdAt: new Date().toISOString(),
          citations: [],
          question,
        },
      ]
    })

    try {
      await streamChat(
        {
          question,
          sessionId: activeSession,
          useRag: strict || useRag,
          strictNumbers: strict,
          documentId,
        },
        {
        start: (data) => {
          setActiveSession(data.sessionId)
          patchAssistant((message) => ({ ...message, queryId: data.queryId }))
          refreshSessions()
        },
        stage: (data) => setStages((previous) => [...previous, data]),
        citation: (data) =>
          patchAssistant((message) => ({ ...message, citations: [...(message.citations ?? []), data] })),
        delta: (data) => patchAssistant((message) => ({ ...message, content: message.content + data.text })),
        number_check: (data) => patchAssistant((message) => ({ ...message, numberCheck: data })),
        done: (data) =>
          patchAssistant((message) => ({
            ...message,
            latencyMs: data.latencyMs,
            firstTokenMs: data.firstTokenMs,
            refused: data.refused,
          })),
        error: (data) => setError(data.message),
      })
      refreshSessions()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    } finally {
      setSending(false)
    }
  }

  const send = () => {
    const question = input.trim()
    if (!question || sending) {
      return
    }
    setInput('')
    void runChat(question, false)
  }

  const retryStrict = (message: UiMessage) => {
    if (!message.question || sending) {
      return
    }
    void runChat(message.question, true)
  }

  const sendFeedback = async (message: UiMessage, verdict: string) => {
    if (!message.queryId) {
      return
    }
    try {
      await api.feedback(message.queryId, verdict)
      setMessages((previous) =>
        previous.map((item) => (item.queryId === message.queryId ? { ...item, feedback: verdict } : item)),
      )
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  const normalCitations = (message: UiMessage) => (message.citations ?? []).filter((item) => !item.candidate)
  const candidateCitations = (message: UiMessage) => (message.citations ?? []).filter((item) => item.candidate)

  return (
    <section className={preview ? 'chat with-preview' : 'chat'}>
      <aside className="sessions">
        <button className="new-chat" onClick={newChat}>
          新对话
        </button>
        <ul>
          {sessions.map((session) => (
            <li key={session.id} className={session.id === activeSession ? 'active' : undefined}>
              <button onClick={() => void openSession(session.id)}>{session.title || '未命名'}</button>
            </li>
          ))}
        </ul>
      </aside>

      <div className="conversation">
        <div className="chat-head">
          <label className="switch">
            <input type="checkbox" checked={useRag} onChange={(event) => toggleRag(event.target.checked)} />
            <span>{useRag ? '接入研报库' : '纯聊天'}</span>
          </label>
          {useRag && (
            <select
              value={documentId ?? ''}
              onChange={(event) => setDocumentId(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">全部文档</option>
              {documents.map((document) => (
                <option key={document.id} value={document.id}>
                  {document.filename}
                </option>
              ))}
            </select>
          )}
          {stages.length > 0 && (
            <span className="muted">
              {stages.map((stage) => `${stage.stage} ${stage.ms}ms`).join(' · ')}
            </span>
          )}
        </div>

        <div className="messages">
          {messages.length === 0 && (
            <p className="empty">
              直接提问即可。打开「接入研报库」后，回答只依据检索到的原文，并在句末标出 [n]。
            </p>
          )}
          {messages.map((message, index) => (
            <article key={`${message.createdAt}-${index}`} className={`bubble ${message.role}`}>
              <div className="bubble-text">
                {markNumbers(message.content, message.numberCheck?.missing ?? []) ||
                  (sending && index === messages.length - 1 ? '…' : '')}
              </div>

              {message.numberCheck && message.numberCheck.total > 0 && (
                <div className={message.numberCheck.missing.length > 0 ? 'number-bar bad' : 'number-bar ok'}>
                  <span>
                    数字自检 {message.numberCheck.total} 个
                    {message.numberCheck.missing.length > 0
                      ? `，原文没有：${message.numberCheck.missing.join('、')}`
                      : '，全部能在原文找到'}
                  </span>
                  {message.numberCheck.missing.length > 0 && index === messages.length - 1 && (
                    <button onClick={() => retryStrict(message)} disabled={sending}>
                      只用原文数字重答
                    </button>
                  )}
                </div>
              )}

              {normalCitations(message).length > 0 && (
                <ol className="citations">
                  {normalCitations(message).map((citation) => (
                    <li key={`${citation.chunkId}-${citation.index}`}>
                      <button className="citation" onClick={() => void openCitation(citation)}>
                        <span className="citation-index">[{citation.index}]</span>
                        <span className="citation-doc">{citation.doc}</span>
                        <span className="muted">
                          第 {citation.pageFrom}
                          {citation.pageTo !== citation.pageFrom ? `-${citation.pageTo}` : ''} 页
                        </span>
                        {citation.section && <span className="muted">{citation.section}</span>}
                        {citation.snippet && <span className="citation-snippet">{citation.snippet}</span>}
                      </button>
                    </li>
                  ))}
                </ol>
              )}

              {message.refused && candidateCitations(message).length > 0 && (
                <div className="candidates">
                  <div className="muted">最接近的候选块，可能是问题表述偏了：</div>
                  <ol className="citations">
                    {candidateCitations(message).map((citation) => (
                      <li key={`${citation.chunkId}-${citation.index}`}>
                        <button className="citation" onClick={() => void openCitation(citation)}>
                          <span className="citation-index">[{citation.index}]</span>
                          <span className="citation-doc">{citation.doc}</span>
                          <span className="muted">第 {citation.pageFrom} 页</span>
                        </button>
                      </li>
                    ))}
                  </ol>
                </div>
              )}

              {message.latencyMs !== undefined && (
                <div className="bubble-meta muted">
                  {message.refused ? '已拒答' : `首字 ${message.firstTokenMs}ms · 整答 ${message.latencyMs}ms`}
                  {message.strict && ' · 严格数字重答'}
                  {message.queryId && (
                    <>
                      {' · '}
                      <Link to={`/trace/${message.queryId}`}>链路</Link>
                    </>
                  )}
                </div>
              )}

              {message.role === 'assistant' && message.queryId && !sending && (
                <div className="feedback">
                  <span className="muted">这条答案：</span>
                  {[
                    ['ok', '有用'],
                    ['incomplete', '不完整'],
                    ['wrong', '答错了'],
                  ].map(([verdict, label]) => (
                    <button
                      key={verdict}
                      className={message.feedback === verdict ? 'active' : undefined}
                      onClick={() => void sendFeedback(message, verdict)}
                    >
                      {label}
                    </button>
                  ))}
                  {message.feedback && <span className="muted">已记录</span>}
                </div>
              )}
            </article>
          ))}
          <div ref={bottomRef} />
        </div>

        {error && <p className="notice bad">{error}</p>}

        <div className="composer">
          <textarea
            value={input}
            placeholder={useRag ? '问研报里的内容，例如：新涨跌幅因子怎么构造？' : '随便聊点什么'}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                send()
              }
            }}
            rows={3}
          />
          <button onClick={send} disabled={sending || input.trim().length === 0}>
            {sending ? '生成中' : '发送'}
          </button>
        </div>
      </div>

      {preview && (
        <aside className="preview-pane">
          <div className="preview-head">
            <span className="muted">原文预览</span>
            <button onClick={() => setPreview(null)}>关闭</button>
          </div>
          <PdfPreview
            documentId={preview.documentId}
            page={preview.page}
            boxes={preview.boxes}
            onPageChange={(page) => setPreview((current) => (current ? { ...current, page } : current))}
          />
        </aside>
      )}
    </section>
  )
}
