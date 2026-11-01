import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useApiQuery } from '../api/query'
import { BackLink, DirectionalTransition, ForwardLink } from '../components/PageTransition'
import { QueryState } from '../components/QueryState'
import { preload } from '../preload'

export default function JobDetailPage() {
  const { id } = useParams()
  const job = useApiQuery(id ? `/api/jobs/${id}` : null, () => api.job(id!), { pollMs: 2000 })

  return (
    <DirectionalTransition>
      <section className="page">
        <div className="page-head">
          <h1>任务 {id}</h1>
          <BackLink to="/jobs">回任务列表</BackLink>
        </div>

        <QueryState loading={!job.data && !job.error} error={job.error}>
          {job.data ? (
            <dl className="facts">
              <dt>类型</dt>
              <dd>{job.data.type}</dd>
              <dt>状态</dt>
              <dd>
                <span className={`chip ${job.data.status}`}>{job.data.status}</span>
              </dd>
              <dt>阶段</dt>
              <dd>{job.data.stage ?? '-'}</dd>
              <dt>进度</dt>
              <dd>{job.data.total > 0 ? `${job.data.done}/${job.data.total}` : `${job.data.progress}%`}</dd>
              <dt>来源</dt>
              <dd className="truncate">{job.data.sourcePath ?? '-'}</dd>
              <dt>文档</dt>
              <dd>
                {job.data.documentId ? (
                  <ForwardLink
                    to={`/documents/${job.data.documentId}/pipeline`}
                    preload={preload.pipeline}
                  >
                    {job.data.documentId}
                  </ForwardLink>
                ) : (
                  '-'
                )}
              </dd>
              <dt>开始</dt>
              <dd>{job.data.createdAt}</dd>
              <dt>更新</dt>
              <dd>{job.data.updatedAt}</dd>
              <dt>错误</dt>
              <dd className="error-text">{job.data.error ?? '-'}</dd>
            </dl>
          ) : null}
        </QueryState>
      </section>
    </DirectionalTransition>
  )
}
