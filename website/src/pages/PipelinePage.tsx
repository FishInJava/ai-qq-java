import { useMemo, useState, ViewTransition } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { api, type BlockNode } from '../api/client'
import { useApiQuery } from '../api/query'
import { BackLink, DirectionalTransition } from '../components/PageTransition'
import { QueryState } from '../components/QueryState'
import { errorMessage } from '../lib/errors'

type Section = {
  key: string
  blocks: BlockNode[]
}

type Seed = {
  document?: { id: number; filename: string }
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
  const location = useLocation()
  const seed = (location.state as Seed | null)?.document
  const view = useApiQuery(id ? `/api/documents/${id}/pipeline` : null, () => api.pipeline(id!), {
    staleMs: 30_000,
  })
  const [notice, setNotice] = useState<{ text: string; bad: boolean } | null>(null)

  const sections = useMemo(() => groupBySection(view.data?.blocks ?? []), [view.data])
  const pages = view.data?.pages ?? []
  const runs = view.data?.runs ?? []
  const emptyPages = pages.filter((page) => page.isEmpty).length
  const title = view.data?.document.filename ?? seed?.filename ?? '文档'

  const reingest = async () => {
    if (!id) {
      return
    }
    try {
      const { jobId } = await api.reingest(id)
      setNotice({ text: `已排队导入任务 ${jobId}，进度见任务页`, bad: false })
    } catch (reason) {
      setNotice({ text: errorMessage(reason), bad: true })
    }
  }

  return (
    <DirectionalTransition>
      <section className="page">
        <div className="page-head">
          <ViewTransition name={`doc-title-${id}`} share="text-morph" default="none">
            <h1>{title}</h1>
          </ViewTransition>
          <div className="muted">
            {pages.length} 页 · 空页 {emptyPages} · 章节来源 {view.data?.document.structureSource ?? '-'} ·{' '}
            <BackLink to="/library">回文档库</BackLink>
          </div>
          <div className="actions">
            <button onClick={() => void reingest()}>重新导入</button>
            <button onClick={() => void view.refresh()}>刷新</button>
          </div>
        </div>

        {notice ? <p className={notice.bad ? 'notice bad' : 'notice'}>{notice.text}</p> : null}

        <QueryState loading={!view.data && !view.error} error={view.error}>
          {view.data ? (
            <>
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
                      {pages.map((page) => (
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
                {runs.length === 0 ? (
                  <p className="empty">还没有运行记录。</p>
                ) : (
                  runs.map((run) => (
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
                            {event.message ? <span className="error-text">{event.message}</span> : null}
                          </li>
                        ))}
                      </ol>
                    </div>
                  ))
                )}
              </div>
            </>
          ) : null}
        </QueryState>
      </section>
    </DirectionalTransition>
  )
}
