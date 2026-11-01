import { useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useApiQuery } from '../api/query'
import { BackLink, DirectionalTransition } from '../components/PageTransition'
import { QueryState } from '../components/QueryState'

const STAGE_ORDER = [
  'normalize',
  'bm25',
  'vector',
  'fusion',
  'fetch',
  'rerank',
  'generate',
  'number_check',
  'citation',
]

export default function TracePage() {
  const { queryId } = useParams()
  const trace = useApiQuery(queryId ? `/api/trace/${queryId}` : null, () => api.trace(queryId!))

  const stages = useMemo(
    () =>
      (trace.data?.stages ?? []).toSorted(
        (left, right) => STAGE_ORDER.indexOf(left.stage) - STAGE_ORDER.indexOf(right.stage),
      ),
    [trace.data],
  )

  return (
    <DirectionalTransition>
      <section className="page">
        <div className="page-head">
          <h1>链路 {queryId}</h1>
          <BackLink to="/chat">回对话</BackLink>
        </div>

        <QueryState loading={!trace.data && !trace.error} error={trace.error}>
          {trace.data ? (
            <>
              <dl className="facts">
                <dt>问题</dt>
                <dd>{trace.data.query.question}</dd>
                <dt>状态</dt>
                <dd>
                  <span className={`chip ${trace.data.query.status}`}>{trace.data.query.status}</span>
                </dd>
                <dt>整答</dt>
                <dd>{trace.data.query.latencyMs ?? '-'} ms</dd>
                <dt>时间</dt>
                <dd>{trace.data.query.createdAt}</dd>
                <dt>错误</dt>
                <dd className="error-text">{trace.data.query.error ?? '-'}</dd>
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
                  {stages.map((stage) => (
                    <tr key={stage.stage}>
                      <td>{stage.stage}</td>
                      <td>{stage.durationMs ?? 0} ms</td>
                      <td className="payload">{stage.payload}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          ) : null}
        </QueryState>
      </section>
    </DirectionalTransition>
  )
}
