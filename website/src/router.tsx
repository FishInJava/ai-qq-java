import { lazy } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import App from './App'
import { preload } from './preload'

const LibraryPage = lazy(preload.library)
const ChatPage = lazy(preload.chat)
const JobsPage = lazy(preload.jobs)
const JobDetailPage = lazy(preload.job)
const PipelinePage = lazy(preload.pipeline)
const TracePage = lazy(preload.trace)

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <LibraryPage /> },
      { path: 'library', element: <LibraryPage /> },
      { path: 'chat', element: <ChatPage /> },
      { path: 'jobs', element: <JobsPage /> },
      { path: 'jobs/:id', element: <JobDetailPage /> },
      { path: 'documents/:id/pipeline', element: <PipelinePage /> },
      { path: 'trace/:queryId', element: <TracePage /> },
    ],
  },
])
