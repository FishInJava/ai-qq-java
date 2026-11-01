import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type BlockNode, type PipelineView } from '../api/client'

type Section = {
  key: string
  blocks: BlockNode[]
}

function groupBySection(blocks: BlockNode[]): Section[] {
  const sections = new Map<string, BlockNode[]>()
  for (const block of blocks) {
    const key = block.sectionPath.length > 0 ? block.sectionPath.join(' / ') : '(未识别章节)'
    const list = sections.get(key) ?? []
    list.push(block)
    sections.set(key, list)
  }
  return [...sections.entries()].map(([key, list]) => ({ key, blocks: list }))
}

export default function PipelinePage() {
  const { id } = useParams()
  const [view, setView] = useState<PipelineView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const reload = useCallback(() => {
    if (!id) {
      return
    }
    api
      .pipeline(id)
      .then(setView)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : String(reason)))
  }, [id])

  useEffect(reload, [reload])

  const reingest = async () => {
    if (!id) {
      return
    }
    try {
      const { jobId } = await api.reingest(id)
      setMessage(`已排队导入任务 ${jobId}，进度见任务页`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  const sections = useMemo(() => groupBySection(view?.blocks ?? []), [view])

  if (error) {
    return (
      <section className="page">
        <p className="notice bad">{error}</p>
      </section>
    )
  }

  if (!view) {
    return (
      <section className="page">
        <p className="empty">读取中</p>
      </section>
    )
  }

  const emptyPages = view.pages.filter((page) => page.isEmpty).length

  return (
    <section className="page">
      <div className="page-head">
        <h1>{view.document.filename}</h1>
        <div className="muted">
          {view.pages.length} 页 · 空页 {emptyPages} · 章节来源 {view.document.structureSource} ·{' '}
          <Link to="/library">回文档库</Link>
        </div>
        <div className="actions">
          <button onClick={() => void reingest()}>重新导入</button>
          <button onClick={reload}>刷新</button>
        </div>
      </div>

      {message && <p className="notice">{message}</p>}

      <div className="split">
        <div className="panel">
          <h2>页级清单</h2>
          <table className="grid compact">
            <thead>
              <tr>
                <th>页</th>
                <th>字符</th>
                <th>空</th>
                <th>旋转</th>
              </tr>
            </thead>
            <tbody>
              {view.pages.map((page) => (
                <tr key={page.pageNo} className={page.isEmpty ? 'dim' : undefined}>
                  <td>{page.pageNo}</td>
                  <td>{page.charCount}</td>
                  <td>{page.isEmpty ? '是' : ''}</td>
                  <td>{page.rotation}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="panel">
          <h2>块树</h2>
          {sections.length === 0 ? (
            <p className="empty">还没有块。</p>
          ) : (
            sections.map((section) => (
              <details key={section.key} open className="section">
                <summary>
                  {section.key} <span className="muted">({section.blocks.length})</span>
                </summary>
                <ul className="blocks">
                  {section.blocks.map((block) => (
                    <li key={block.id}>
                      <span className={`chip ${block.blockType}`}>{block.blockType}</span>
                      <span className="muted">第 {block.pageNo} 页</span>
                      <span className="block-text">{block.text}</span>
                    </li>
                  ))}
                </ul>
              </details>
            ))
          )}
        </div>
      </div>

      <div className="panel">
        <h2>管线运行</h2>
        {view.runs.length === 0 ? (
          <p className="empty">还没有运行记录。</p>
        ) : (
          view.runs.map((run) => (
            <div key={run.id} className="run">
              <div className="run-head">
                第 {run.attempt} 次 · {run.status} · {run.startedAt}
              </div>
              <ol className="events">
                {run.events.map((event) => (
                  <li key={event.node}>
                    <span className="event-node">{event.node}</span>
                    <span className={`chip ${event.status}`}>{event.status}</span>
                    <span className="muted">{event.durationMs ?? 0}ms</span>
                    {event.message && <span className="error-text">{event.message}</span>}
                  </li>
                ))}
              </ol>
            </div>
          ))
        )}
      </div>
    </section>
  )
}
