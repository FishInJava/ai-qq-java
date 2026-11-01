import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Job } from '../api/client'

const ACTIVE = new Set(['queued', 'running'])

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(() => {
    api
      .jobs()
      .then((result) => setJobs(result.items))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : String(reason)))
  }, [])

  useEffect(() => {
    reload()
    const timer = window.setInterval(() => {
      reload()
    }, 2000)
    return () => window.clearInterval(timer)
  }, [reload])

  return (
    <section className="page">
      <div className="page-head">
        <h1>任务</h1>
        <span className="muted">每 2 秒刷新</span>
      </div>

      {error && <p className="notice bad">{error}</p>}

      {jobs.length === 0 ? (
        <p className="empty">还没有任务。</p>
      ) : (
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
            {jobs.map((job) => (
              <tr key={job.id}>
                <td>
                  <Link to={`/jobs/${job.id}`}>{job.id}</Link>
                </td>
                <td>{job.type}</td>
                <td className="truncate">{job.sourcePath ?? '-'}</td>
                <td>
                  <span className={`chip ${job.status}`}>{job.status}</span>
                </td>
                <td>{job.stage ?? '-'}</td>
                <td>
                  {job.total > 0 ? `${job.done}/${job.total}` : `${job.progress}%`}
                  {ACTIVE.has(job.status) && <span className="pulse" />}
                </td>
                <td className="error-text">{job.error ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
