import { lazy, Suspense, useDeferredValue, useEffect, useState, ViewTransition, type ReactNode } from 'react'
import { prefersReducedMotion } from '../lib/motion'
import { useChatActions, useChatMeta, useChatShell, useChatThread } from './context'
import { MessageBubble } from './MessageBubble'

const PdfPreview = lazy(() => import('../components/PdfPreview'))

export function ChatFrame({ children }: { children: ReactNode }) {
  const { preview } = useChatShell()
  return <section className={preview ? 'chat with-preview' : 'chat'}>{children}</section>
}

export function ChatSessions() {
  const { sessions, activeSession } = useChatShell()
  const { newChat, openSession } = useChatActions()
  return (
    <aside className="sessions">
      <button className="new-chat" onClick={newChat}>
        新对话
      </button>
      <ul>
        {sessions.map((session) => (
          <li key={session.id} className={session.id === activeSession ? 'active' : undefined}>
            <button onClick={() => openSession(session.id)}>{session.title || '未命名'}</button>
          </li>
        ))}
      </ul>
    </aside>
  )
}

export function ChatConversation({ children }: { children: ReactNode }) {
  return <div className="conversation">{children}</div>
}

export function ChatToolbar() {
  const { useRag, documentId, documents, stages } = useChatShell()
  const { setUseRag, setDocumentId } = useChatActions()
  const deferredStages = useDeferredValue(stages)

  return (
    <div className="chat-head">
      <label className="switch">
        <input type="checkbox" checked={useRag} onChange={(event) => setUseRag(event.target.checked)} />
        <span>{useRag ? '接入研报库' : '纯聊天'}</span>
      </label>
      {useRag ? (
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
      ) : null}
      {deferredStages.length > 0 ? (
        <span className="muted">
          {deferredStages.map((stage) => `${stage.stage} ${stage.ms}ms`).join(' · ')}
        </span>
      ) : null}
    </div>
  )
}

export function ChatMessages() {
  const { messages, sending } = useChatThread()
  const { bottomRef } = useChatMeta()
  const lastLength = messages.length > 0 ? messages[messages.length - 1].content.length : 0

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }, [messages.length, lastLength, sending, bottomRef])

  return (
    <div className="messages">
      {messages.length === 0 ? (
        <p className="empty">直接提问即可。打开「接入研报库」后，回答只依据检索到的原文，并在句末标出 [n]。</p>
      ) : null}
      {messages.map((message, index) => (
        <ViewTransition key={message.key} enter="fade-in" default="none">
          <MessageBubble message={message} isLast={index === messages.length - 1} sending={sending} />
        </ViewTransition>
      ))}
      <div ref={bottomRef} />
    </div>
  )
}

export function ChatError() {
  const { error } = useChatShell()
  return error ? <p className="notice bad">{error}</p> : null
}

function ComposerInput() {
  const { sending } = useChatThread()
  const { useRag } = useChatShell()
  const { send } = useChatActions()
  const [input, setInput] = useState('')

  const submit = () => {
    const question = input.trim()
    if (!question || sending) {
      return
    }
    setInput('')
    send(question)
  }

  return (
    <div className="composer">
      <textarea
        value={input}
        placeholder={useRag ? '问研报里的内容，例如：新涨跌幅因子怎么构造？' : '随便聊点什么'}
        onChange={(event) => setInput(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            submit()
          }
        }}
        rows={3}
      />
      <button onClick={submit} disabled={sending || input.trim().length === 0}>
        {sending ? '生成中' : '发送'}
      </button>
    </div>
  )
}

export function ChatComposer() {
  const { draftKey } = useChatShell()
  return <ComposerInput key={draftKey} />
}

export function ChatPreview() {
  const { preview } = useChatShell()
  const { closePreview, setPreviewPage } = useChatActions()

  if (!preview) {
    return null
  }

  return (
    <ViewTransition enter="slide-up" exit="slide-down" default="none">
      <aside className="preview-pane">
        <div className="preview-head">
          <span className="muted">原文预览</span>
          <button onClick={closePreview}>关闭</button>
        </div>
        <Suspense fallback={<p className="empty">加载预览</p>}>
          <PdfPreview
            documentId={preview.documentId}
            page={preview.page}
            boxes={preview.boxes}
            onPageChange={setPreviewPage}
          />
        </Suspense>
      </aside>
    </ViewTransition>
  )
}

