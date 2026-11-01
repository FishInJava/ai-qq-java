import { Suspense } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { api } from './api/client'
import { useApiQuery } from './api/query'
import { errorMessage } from './lib/errors'
import { preload } from './preload'

const NAV = [
  { to: '/library', label: '文档库', load: preload.library },
  { to: '/chat', label: '对话', load: preload.chat },
  { to: '/jobs', label: '任务', load: preload.jobs },
] as const

export default function App() {
  const health = useApiQuery('/api/health', api.health)
  const status = health.data

  return (
    <div className="shell">
      <header className="topbar" style={{ viewTransitionName: 'site-header' }}>
        <div className="brand">JustRAG</div>
        <nav>
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onMouseEnter={() => void item.load()}
              onFocus={() => void item.load()}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className={status?.db ? 'health ok' : 'health bad'}>
          {status
            ? `PG ${status.postgres} · pgvector ${status.pgvector} · ${status.tables} 张表 · ${status.latencyMs}ms`
            : health.error
              ? errorMessage(health.error)
              : '连接中'}
        </div>
      </header>
      <main>
        <Suspense fallback={<p className="empty">加载中</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
