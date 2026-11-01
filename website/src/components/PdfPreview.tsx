import { useEffect, useRef, useState } from 'react'
import { GlobalWorkerOptions, getDocument, type PDFDocumentProxy } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'
import type { ChunkBox } from '../api/client'

GlobalWorkerOptions.workerSrc = workerUrl

type Props = {
  documentId: number
  page: number
  boxes: ChunkBox[]
  onPageChange: (page: number) => void
}

type Highlight = {
  left: number
  top: number
  width: number
  height: number
}

const SCALE = 1.25

export default function PdfPreview({ documentId, page, boxes, onPageChange }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })
  const [highlights, setHighlights] = useState<Highlight[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setPdf(null)
    setError(null)
    const task = getDocument({ url: `/api/documents/${documentId}/file` })
    task.promise
      .then((document) => {
        if (!cancelled) {
          setPdf(document)
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setError(reason instanceof Error ? reason.message : String(reason))
        }
      })
    return () => {
      cancelled = true
      void task.destroy()
    }
  }, [documentId])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!pdf || !canvas) {
      return
    }
    let cancelled = false
    let task: ReturnType<Awaited<ReturnType<PDFDocumentProxy['getPage']>>['render']> | null = null
    const target = Math.min(Math.max(page, 1), pdf.numPages)
    pdf
      .getPage(target)
      .then(async (pdfPage) => {
        if (cancelled) {
          return
        }
        const ratio = window.devicePixelRatio || 1
        const viewport = pdfPage.getViewport({ scale: SCALE * ratio })
        canvas.width = Math.floor(viewport.width)
        canvas.height = Math.floor(viewport.height)
        canvas.style.width = `${viewport.width / ratio}px`
        canvas.style.height = `${viewport.height / ratio}px`
        task = pdfPage.render({ canvas, viewport })
        await task.promise
        if (cancelled) {
          return
        }
        const cssViewport = pdfPage.getViewport({ scale: SCALE })
        setSize({ width: cssViewport.width, height: cssViewport.height })
        setHighlights(
          boxes
            .filter((box) => box.pageNo === target)
            .map((box) => {
              const [x0, y0] = cssViewport.convertToViewportPoint(box.x0, box.y0)
              const [x1, y1] = cssViewport.convertToViewportPoint(box.x1, box.y1)
              return {
                left: Math.min(x0, x1),
                top: Math.min(y0, y1),
                width: Math.abs(x1 - x0),
                height: Math.abs(y1 - y0),
              }
            }),
        )
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setError(reason instanceof Error ? reason.message : String(reason))
        }
      })
    return () => {
      cancelled = true
      task?.cancel()
    }
  }, [pdf, page, boxes])

  if (error) {
    return <p className="notice bad">{error}</p>
  }

  return (
    <div className="pdf-preview">
      <div className="pdf-controls">
        <button onClick={() => onPageChange(Math.max(1, page - 1))} disabled={page <= 1}>
          上一页
        </button>
        <span className="muted">
          第 {page} 页 / 共 {pdf?.numPages ?? '?'} 页
        </span>
        <button onClick={() => onPageChange(page + 1)} disabled={!!pdf && page >= pdf.numPages}>
          下一页
        </button>
      </div>
      <div className="pdf-stage" style={{ width: size.width || undefined, height: size.height || undefined }}>
        <canvas ref={canvasRef} />
        {highlights.map((highlight, index) => (
          <div
            key={`${highlight.left}-${highlight.top}-${index}`}
            className="pdf-highlight"
            style={{
              left: highlight.left,
              top: highlight.top,
              width: highlight.width,
              height: highlight.height,
            }}
          />
        ))}
      </div>
    </div>
  )
}
