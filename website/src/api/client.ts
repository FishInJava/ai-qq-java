export type Health = {
  status: string
  db: boolean
  postgres: string | null
  pgvector: string | null
  tables: number
  latencyMs: number
  time: string
}

export type DocumentSummary = {
  id: number
  filename: string
  title: string | null
  category: string
  pages: number
  bytes: number
  language: string | null
  status: string
  structureSource: string
  pipelineVersion: number | null
  error: string | null
  createdAt: string
}

export type Job = {
  id: number
  type: string
  sourcePath: string | null
  documentId: number | null
  status: string
  stage: string | null
  progress: number
  total: number
  done: number
  error: string | null
  createdAt: string
  updatedAt: string
}

export type PageRow = {
  pageNo: number
  width: number
  height: number
  rotation: number
  charCount: number
  isEmpty: boolean
}

export type BlockNode = {
  id: number
  pageNo: number
  blockType: string
  text: string
  orderNo: number
  sectionPath: string[]
  charCount: number
}

export type PipelineEvent = {
  node: string
  status: string
  durationMs: number | null
  detail: Record<string, unknown>
  message: string | null
}

export type PipelineRun = {
  id: number
  attempt: number
  status: string
  startedAt: string
  finishedAt: string | null
  stats: Record<string, unknown>
  events: PipelineEvent[]
}

export type PipelineView = {
  document: DocumentSummary
  pages: PageRow[]
  runs: PipelineRun[]
  blocks: BlockNode[]
}

export type Session = {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
  queryId: string
  createdAt: string
}

export type Citation = {
  index: number
  doc: string
  documentId: number
  pageFrom: number
  pageTo: number
  section: string
  chunkId: number
  snippet: string
  candidate: boolean
}

export type ChunkBox = {
  pageNo: number
  x0: number
  y0: number
  x1: number
  y1: number
}

export type ChunkBoxes = {
  documentId: number
  boxes: ChunkBox[]
}

export type NumberCheck = {
  total: number
  missing: string[]
}

export type Trace = {
  query: {
    id: string
    sessionId: string
    question: string
    status: string
    latencyMs: number | null
    error: string | null
    createdAt: string
  }
  stages: Array<{ stage: string; durationMs: number | null; payload: string }>
}

export type ChatHandlers = {
  start?: (data: { queryId: string; sessionId: string }) => void
  stage?: (data: { stage: string; ms: number }) => void
  citation?: (data: Citation) => void
  delta?: (data: { text: string }) => void
  number_check?: (data: NumberCheck) => void
  done?: (data: {
    queryId: string
    latencyMs: number
    firstTokenMs: number
    tokensIn: number
    tokensOut: number
    refused: boolean
    passages: number
    invalidCitations: number
  }) => void
  error?: (data: { code: string; message: string; retryable: boolean }) => void
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    throw new Error(`${path} 返回 ${response.status}`)
  }
  return (await response.json()) as T
}

export async function streamChat(
  body: {
    question: string
    sessionId: string | null
    useRag: boolean
    documentId?: number | null
    strictNumbers?: boolean
  },
  handlers: ChatHandlers,
): Promise<void> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, requestId: crypto.randomUUID() }),
  })
  if (!response.ok || !response.body) {
    throw new Error(`对话失败：${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const raw = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      let event = 'message'
      let data = ''
      for (const line of raw.split('\n')) {
        if (line.startsWith('event:')) {
          event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          data += line.slice(5).trim()
        }
      }
      if (data) {
        const handler = handlers[event as keyof ChatHandlers] as ((payload: unknown) => void) | undefined
        handler?.(JSON.parse(data))
      }
      index = buffer.indexOf('\n\n')
    }
  }
}

export const api = {
  health: () => getJson<Health>('/api/health'),
  documents: () => getJson<{ items: DocumentSummary[] }>('/api/documents'),
  document: (id: string) => getJson<DocumentSummary>(`/api/documents/${id}`),
  pipeline: (id: string) => getJson<PipelineView>(`/api/documents/${id}/pipeline`),
  jobs: () => getJson<{ items: Job[] }>('/api/jobs'),
  job: (id: string) => getJson<Job>(`/api/jobs/${id}`),
  sessions: () => getJson<{ items: Session[] }>('/api/sessions'),
  messages: (id: string) => getJson<{ items: ChatMessage[] }>(`/api/sessions/${id}/messages`),
  trace: (queryId: string) => getJson<Trace>(`/api/trace/${queryId}`),
  chunkBoxes: (chunkId: number) => getJson<ChunkBoxes>(`/api/chunks/${chunkId}/boxes`),
  feedback: async (queryId: string, verdict: string, note?: string): Promise<void> => {
    const response = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ queryId, verdict, note: note ?? null }),
    })
    if (!response.ok) {
      throw new Error(`标注失败：${response.status}`)
    }
  },
  scan: async (path?: string): Promise<{ jobId: number }> => {
    const response = await fetch('/api/library/scan', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: path ?? null }),
    })
    if (!response.ok) {
      throw new Error(`扫描失败：${response.status}`)
    }
    return (await response.json()) as { jobId: number }
  },
  deleteDocument: async (id: number): Promise<void> => {
    const response = await fetch(`/api/documents/${id}`, { method: 'DELETE' })
    if (!response.ok) {
      throw new Error(`删除失败：${response.status}`)
    }
  },
  reingest: async (id: string): Promise<{ jobId: number }> => {
    const response = await fetch(`/api/documents/${id}/reingest`, { method: 'POST' })
    if (!response.ok) {
      throw new Error(`重新导入失败：${response.status}`)
    }
    return (await response.json()) as { jobId: number }
  },
  upload: async (file: File): Promise<{ jobId: number; documentId: number }> => {
    const body = new FormData()
    body.append('file', file)
    const response = await fetch('/api/library/upload', { method: 'POST', body })
    if (!response.ok) {
      throw new Error(`上传失败：${response.status}`)
    }
    return (await response.json()) as { jobId: number; documentId: number }
  },
}
