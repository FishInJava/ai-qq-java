import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Job } from '../api/client'

export default function JobDetailPage() {
  const { id } = useParams()
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(() => {
    if (!id) {
      return
    }
    api
      .job(id)
      .then(setJob)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : String(reason)))
  }, [id])

  useEffect(() => {
    reload()
    const timer = window.setInterval(reload, 2000)
    return () => window.clearInterval(timer)
  }, [reload])

  if (error) {
    return (
      <section className="page">
        <p className="notice bad">{error}</p>
      </section>
    )
  }

  if (!job) {
    return (
      <section className="page">
        <p className="empty">读取中</p>
      </section>
    )
  }

  return (
    <section className="page">
      <div className="page-head">
        <h1>任务 {job.id}</h1>
        <Link to="/jobs">回任务列表</Link>
      </div>

      <dl className="facts">
        <dt>类型</dt>
        <dd>{job.type}</dd>
        <dt>状态</dt>
        <dd>
          <span className={`chip ${job.status}`}>{job.status}</span>
        </dd>
        <dt>阶段</dt>
        <dd>{job.stage ?? '-'}</dd>
        <dt>进度</dt>
        <dd>
          {job.total > 0 ? `${job.done}/${job.total}` : `${job.progress}%`}
        </dd>
        <dt>来源</dt>
        <dd className="truncate">{job.sourcePath ?? '-'}</dd>
        <dt>文档</dt>
        <dd>{job.documentId ? <Link to={`/documents/${job.documentId}/pipeline`}>{job.documentId}</Link> : '-'}</dd>
        <dt>开始</dt>
        <dd>{job.createdAt}</dd>
        <dt>更新</dt>
        <dd>{job.updatedAt}</dd>
        <dt>错误</dt>
        <dd className="error-text">{job.error ?? '-'}</dd>
      </dl>
    </section>
  )
}
