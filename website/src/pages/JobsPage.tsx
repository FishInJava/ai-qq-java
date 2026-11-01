import { api } from '../api/client'
import { useApiQuery } from '../api/query'
import { DirectionalTransition, ForwardLink } from '../components/PageTransition'
import { QueryState } from '../components/QueryState'
import { preload } from '../preload'

const ACTIVE = new Set(['queued', 'running'])

export default function JobsPage() {
  const jobs = useApiQuery('/api/jobs', api.jobs, { pollMs: 2000 })
  const items = jobs.data?.items ?? []

  return (
    <DirectionalTransition>
      <section className="page">
        <div className="page-head">
          <h1>任务</h1>
          <span className="muted">每 2 秒刷新</span>
        </div>

        <QueryState
          loading={!jobs.data && !jobs.error}
          error={jobs.error}
          empty={items.length === 0}
          emptyText="还没有任务。"
        >
          <table className="grid">
            <thead>
              <tr>
                <th>ID</th>
                <th>类型</th>
                <th>来源</th>
                <th>状态</th>
                <th>阶段</th>
                <th>进度</th>
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              {items.map((job) => (
                <tr key={job.id}>
                  <td>
                    <ForwardLink to={`/jobs/${job.id}`} preload={preload.job}>
                      {job.id}
                    </ForwardLink>
                  </td>
                  <td>{job.type}</td>
                  <td className="truncate">{job.sourcePath ?? '-'}</td>
                  <td>
                    <span className={`chip ${job.status}`}>{job.status}</span>
                  </td>
                  <td>{job.stage ?? '-'}</td>
                  <td>
                    {job.total > 0 ? `${job.done}/${job.total}` : `${job.progress}%`}
                    {ACTIVE.has(job.status) ? <span className="pulse" /> : null}
                  </td>
                  <td className="error-text">{job.error ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </QueryState>
      </section>
    </DirectionalTransition>
  )
}
