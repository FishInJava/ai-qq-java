import { createBrowserRouter } from 'react-router-dom'
import App from './App'
import LibraryPage from './pages/LibraryPage'
import ChatPage from './pages/ChatPage'
import JobsPage from './pages/JobsPage'
import JobDetailPage from './pages/JobDetailPage'
import PipelinePage from './pages/PipelinePage'
import TracePage from './pages/TracePage'

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
