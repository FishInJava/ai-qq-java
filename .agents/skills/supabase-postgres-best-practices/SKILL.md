---
name: supabase-postgres-best-practices
description: Supabase 维护的 PostgreSQL 最佳实践，任何 PG 实例都适用。动库之前先读：建表改列与选类型、schema 设计、迁移、RLS 策略及其测试、索引、触发器、数据库函数、pg_cron 与 pgmq 任务、pgvector 向量检索、pg_restore 导入数据。排查慢查询、CPU 高、超时、EXPLAIN 计划、连接耗尽、锁、表膨胀、行被错误的人或租户看见时也读。不只是性能手册，改一列或写一条 SQL 同样适用。
license: MIT
metadata:
  author: supabase
  version: "1.1.1"
  organization: Supabase
  date: January 2026
  source: supabase/agent-skills
  abstract: Comprehensive Postgres performance optimization guide for developers using Supabase and Postgres. Contains performance rules across 8 categories, prioritized by impact from critical (query performance, connection management) to incremental (advanced features). Each rule includes detailed explanations, incorrect vs. correct SQL examples, query plan analysis, and specific performance metrics to guide automated optimization and code generation.
---

# Supabase Postgres Best Practices

Comprehensive performance optimization guide for Postgres, maintained by Supabase. Contains rules across 8 categories, prioritized by impact to guide automated query optimization and schema design.

## 适用范围

规则只对 PostgreSQL 成立。本仓库持久层是 MySQL 8（`server` 的 `DB_URL` 默认 `jdbc:mysql://…`，DAO 走 MyBatis），也没用 Supabase。MySQL 没有 RLS、`pg_stat_statements`、`pgvector`、`CREATE INDEX CONCURRENTLY`、`timestamptz`，`security-*` 与 `advanced-*` 两组基本无处落地；`query-*`、`data-*`、`lock-*` 三组的判断（补索引、消 N+1、控长事务）思路能用，SQL 语法必须换。

## When to Apply

Reference these guidelines when:
- Writing SQL queries or designing schemas
- Implementing indexes or query optimization
- Reviewing database performance issues
- Configuring connection pooling or scaling
- Optimizing for Postgres-specific features
- Working with Row-Level Security (RLS)

## Rule Categories by Priority

| Priority | Category | Impact | Prefix |
|----------|----------|--------|--------|
| 1 | Query Performance | CRITICAL | `query-` |
| 2 | Connection Management | CRITICAL | `conn-` |
| 3 | Security & RLS | CRITICAL | `security-` |
| 4 | Schema Design | HIGH | `schema-` |
| 5 | Concurrency & Locking | MEDIUM-HIGH | `lock-` |
| 6 | Data Access Patterns | MEDIUM | `data-` |
| 7 | Monitoring & Diagnostics | LOW-MEDIUM | `monitor-` |
| 8 | Advanced Features | LOW | `advanced-` |

## How to Use

Read individual rule files for detailed explanations and SQL examples:

```
references/query-missing-indexes.md
references/query-partial-indexes.md
references/_sections.md
```

Each rule file contains:
- Brief explanation of why it matters
- Incorrect SQL example with explanation
- Correct SQL example with explanation
- Optional EXPLAIN output or metrics
- Additional context and references
- Supabase-specific notes (when applicable)

## References

- https://www.postgresql.org/docs/current/
- https://supabase.com/docs
- https://wiki.postgresql.org/wiki/Performance_Optimization
- https://supabase.com/docs/guides/database/overview
- https://supabase.com/docs/guides/auth/row-level-security
