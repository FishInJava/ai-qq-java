import { memo, useMemo } from 'react'
import type { Citation } from '../api/client'
import { ForwardLink } from '../components/PageTransition'
import { markMissingNumbers } from '../lib/numbers'
import { useChatActions } from './context'
import type { UiMessage } from './types'

type Props = {
  message: UiMessage
  isLast: boolean
  sending: boolean
}

const VERDICTS = [
  ['ok', '有用'],
  ['incomplete', '不完整'],
  ['wrong', '答错了'],
] as const

function splitCitations(citations: Citation[]): { normal: Citation[]; candidates: Citation[] } {
  const normal: Citation[] = []
  const candidates: Citation[] = []
  for (const citation of citations) {
    if (citation.candidate) {
      candidates.push(citation)
    } else {
      normal.push(citation)
    }
  }
  return { normal, candidates }
}

function CitationList({ citations, onOpen }: { citations: Citation[]; onOpen: (citation: Citation) => void }) {
  return (
    <ol className="citations">
      {citations.map((citation) => (
        <li key={`${citation.chunkId}-${citation.index}`}>
          <button className="citation" onClick={() => onOpen(citation)}>
            <span className="citation-index">[{citation.index}]</span>
            <span className="citation-doc">{citation.doc}</span>
            <span className="muted">
              第 {citation.pageFrom}
              {citation.pageTo !== citation.pageFrom ? `-${citation.pageTo}` : ''} 页
            </span>
            {citation.section ? <span className="muted">{citation.section}</span> : null}
            {citation.snippet ? <span className="citation-snippet">{citation.snippet}</span> : null}
          </button>
        </li>
      ))}
    </ol>
  )
}

function CandidateList({ citations, onOpen }: { citations: Citation[]; onOpen: (citation: Citation) => void }) {
  return (
    <ol className="citations">
      {citations.map((citation) => (
        <li key={`${citation.chunkId}-${citation.index}`}>
          <button className="citation" onClick={() => onOpen(citation)}>
            <span className="citation-index">[{citation.index}]</span>
            <span className="citation-doc">{citation.doc}</span>
            <span className="muted">第 {citation.pageFrom} 页</span>
          </button>
        </li>
      ))}
    </ol>
  )
}

export const MessageBubble = memo(function MessageBubble({ message, isLast, sending }: Props) {
  const { openCitation, retryStrict, feedback } = useChatActions()
  const { normal, candidates } = useMemo(() => splitCitations(message.citations ?? []), [message.citations])
  const missing = message.numberCheck?.missing ?? []
  const body = markMissingNumbers(message.content, missing) || (sending && isLast ? '…' : '')

  return (
    <article className={`bubble ${message.role}`}>
      <div className="bubble-text">{body}</div>

      {message.numberCheck && message.numberCheck.total > 0 ? (
        <div className={message.numberCheck.missing.length > 0 ? 'number-bar bad' : 'number-bar ok'}>
          <span>
            数字自检 {message.numberCheck.total} 个
            {message.numberCheck.missing.length > 0
              ? `，原文没有：${message.numberCheck.missing.join('、')}`
              : '，全部能在原文找到'}
          </span>
          {message.numberCheck.missing.length > 0 && isLast ? (
            <button onClick={() => retryStrict(message)} disabled={sending}>
              只用原文数字重答
            </button>
          ) : null}
        </div>
      ) : null}

      {normal.length > 0 ? <CitationList citations={normal} onOpen={openCitation} /> : null}

      {message.refused && candidates.length > 0 ? (
        <div className="candidates">
          <div className="muted">最接近的候选块，可能是问题表述偏了：</div>
          <CandidateList citations={candidates} onOpen={openCitation} />
        </div>
      ) : null}

      {message.latencyMs !== undefined ? (
        <div className="bubble-meta muted">
          {message.refused ? '已拒答' : `首字 ${message.firstTokenMs}ms · 整答 ${message.latencyMs}ms`}
          {message.strict ? ' · 严格数字重答' : ''}
          {message.queryId ? (
            <>
              {' · '}
              <ForwardLink to={`/trace/${message.queryId}`}>链路</ForwardLink>
            </>
          ) : null}
        </div>
      ) : null}

      {message.role === 'assistant' && message.queryId && !sending ? (
        <div className="feedback">
          <span className="muted">这条答案：</span>
          {VERDICTS.map(([verdict, label]) => (
            <button
              key={verdict}
              className={message.feedback === verdict ? 'active' : undefined}
              onClick={() => feedback(message, verdict)}
            >
              {label}
            </button>
          ))}
          {message.feedback ? <span className="muted">已记录</span> : null}
        </div>
      ) : null}
    </article>
  )
})
