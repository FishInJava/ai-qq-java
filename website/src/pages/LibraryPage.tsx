import { useRef, useState, ViewTransition } from 'react'
import { api, type DocumentSummary } from '../api/client'
import { invalidateQuery, useApiQuery } from '../api/query'
import { DirectionalTransition, ForwardLink } from '../components/PageTransition'
import { QueryState } from '../components/QueryState'
import { errorMessage } from '../lib/errors'
import { preload } from '../preload'

export default function LibraryPage() {
  const documents = useApiQuery('/api/documents', api.documents)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const items = documents.data?.items ?? []

  const reload = async () => {
    invalidateQuery('/api/documents')
    await documents.refresh()
  }

  const scan = async () => {
    setBusy(true)
    setMessage(null)
    try {
      const { jobId } = await api.scan()
      setMessage(`扫描任务 ${jobId} 已排队`)
      await reload()
    } catch (error) {
      setMessage(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (document: DocumentSummary) => {
    if (!window.confirm(`删除 ${document.filename}？检索会立即看不到它。`)) {
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await api.deleteDocument(document.id)
      await reload()
    } catch (error) {
      setMessage(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  const upload = async (file: File) => {
    setBusy(true)
    setMessage(null)
    try {
      const { jobId, documentId } = await api.upload(file)
      setMessage(`上传任务 ${jobId}：文档 ${documentId}`)
      await reload()
    } catch (error) {
      setMessage(errorMessage(error))
    } finally {
      setBusy(false)
      if (fileInput.current) {
        fileInput.current.value = ''
      }
    }
  }

  return (
    <DirectionalTransition>
      <section className="page">
        <div className="page-head">
          <h1>文档库</h1>
          <div className="actions">
            <button onClick={() => void scan()} disabled={busy}>
              扫描目录
            </button>
            <button onClick={() => fileInput.current?.click()} disabled={busy}>
              上传 PDF
            </button>
            <input
              ref={fileInput}
              type="file"
              accept="application/pdf"
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) {
                  void upload(file)
                }
              }}
            />
          </div>
        </div>

        {message ? <p className="notice">{message}</p> : null}

        <QueryState
          loading={!documents.data && !documents.error}
          error={documents.error}
          empty={items.length === 0}
          emptyText="还没有文档。点「扫描目录」导入 F:\研报。"
        >
          <table className="grid">
            <thead>
              <tr>
                <th>文件</th>
                <th>类别</th>
                <th>页数</th>
                <th>状态</th>
                <th>管线版本</th>
                <th>错误</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((document) => (
                <tr key={document.id}>
                  <td>
                    <ViewTransition name={`doc-title-${document.id}`} share="text-morph" default="none">
                      <ForwardLink
                        to={`/documents/${document.id}/pipeline`}
                        state={{ document: { id: document.id, filename: document.filename } }}
                        preload={preload.pipeline}
                      >
                        {document.filename}
                      </ForwardLink>
                    </ViewTransition>
                  </td>
                  <td>{document.category}</td>
                  <td>{document.pages}</td>
                  <td>
                    <span className={`chip ${document.status}`}>{document.status}</span>
                  </td>
                  <td>{document.pipelineVersion ?? '-'}</td>
                  <td className="error-text">{document.error ?? ''}</td>
                  <td>
                    <button onClick={() => void remove(document)} disabled={busy}>
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </QueryState>
      </section>
    </DirectionalTransition>
  )
}
