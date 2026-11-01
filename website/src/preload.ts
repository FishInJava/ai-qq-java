export const preload = {
  library: () => import('./pages/LibraryPage'),
  chat: () => import('./pages/ChatPage'),
  jobs: () => import('./pages/JobsPage'),
  job: () => import('./pages/JobDetailPage'),
  pipeline: () => import('./pages/PipelinePage'),
  trace: () => import('./pages/TracePage'),
}
