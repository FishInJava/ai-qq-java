import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { api, type Health } from './api/client'

export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [healthError, setHealthError] = useState<string | null>(null)

  useEffect(() => {
    api
      .health()
      .then(setHealth)
      .catch((error: unknown) => setHealthError(error instanceof Error ? error.message : String(error)))
  }, [])

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">JustRAG</div>
        <nav>
          <NavLink to="/library">文档库</NavLink>
          <NavLink to="/chat">对话</NavLink>
          <NavLink to="/jobs">任务</NavLink>
        </nav>
        <div className={health?.db ? 'health ok' : 'health bad'}>
          {health
            ? `PG ${health.postgres} · pgvector ${health.pgvector} · ${health.tables} 张表 · ${health.latencyMs}ms`
            : healthError ?? '连接中'}
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
