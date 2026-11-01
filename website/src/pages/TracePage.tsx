import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Trace } from '../api/client'

const STAGE_ORDER = ['normalize', 'bm25', 'vector', 'fusion', 'fetch', 'rerank', 'generate', 'number_check', 'citation']

export default function TracePage() {
  const { queryId } = useParams()
  const [trace, setTrace] = useState<Trace | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!queryId) {
      return
    }
    api
      .trace(queryId)
      .then(setTrace)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : String(reason)))
  }, [queryId])

  if (error) {
    return (
      <section className="page">
        <p className="notice bad">{error}</p>
      </section>
    )
  }

  if (!trace) {
    return (
      <section className="page">
        <p className="empty">读取中</p>
      </section>
    )
  }

  return (
    <section className="page">
      <div className="page-head">
        <h1>链路 {trace.query.id}</h1>
        <Link to="/chat">回对话</Link>
      </div>

      <dl className="facts">
        <dt>问题</dt>
        <dd>{trace.query.question}</dd>
        <dt>状态</dt>
        <dd>
          <span className={`chip ${trace.query.status}`}>{trace.query.status}</span>
        </dd>
        <dt>整答</dt>
        <dd>{trace.query.latencyMs ?? '-'} ms</dd>
        <dt>时间</dt>
        <dd>{trace.query.createdAt}</dd>
        <dt>错误</dt>
        <dd className="error-text">{trace.query.error ?? '-'}</dd>
      </dl>

      <table className="grid">
        <thead>
          <tr>
            <th>阶段</th>
            <th>耗时</th>
            <th>数据</th>
          </tr>
        </thead>
        <tbody>
          {[...trace.stages]
            .sort((left, right) => STAGE_ORDER.indexOf(left.stage) - STAGE_ORDER.indexOf(right.stage))
            .map((stage) => (
            <tr key={stage.stage}>
              <td>{stage.stage}</td>
              <td>{stage.durationMs ?? 0} ms</td>
              <td className="payload">{stage.payload}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
