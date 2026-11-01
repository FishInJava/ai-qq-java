# JustRAG 前端

Vite + React 19 + TypeScript + React Router 的最小工程，Backend 是 `server/` 里的 Spring Boot 应用。

## 命令

```
npm install
npm run dev
npm run build
npm run lint
```

开发服务器把 `/api` 转发到 `http://127.0.0.1:8090`，配置在 `vite.config.ts` 的 `server.proxy`。

## 页面

- `/library` 文档库：扫描目录、上传 PDF、文档列表
- `/jobs`、`/jobs/:id` 任务与进度
- `/documents/:id/pipeline` 页级清单、块树、管线运行事件
- `/chat`、`/trace/:queryId` 占位，等检索与生成接上
