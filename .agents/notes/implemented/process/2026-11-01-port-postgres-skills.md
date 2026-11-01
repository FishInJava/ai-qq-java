# Agent Note: 移植 Supabase 的 Postgres 规则集，保留上游名字

Status: implemented

## Problem

上游 `G:\Grinding Gear Games\agent-skills-main11\agent-skills-main\skills\supabase-postgres-best-practices` 是 Supabase 的 agent-skills 里的数据库技能，版本 1.1.1，MIT。结构是 `SKILL.md`（3.2 KB）+ `references/` 34 份（31 条规则分 8 类，外加 `_sections.md`、`_template.md`、`_contributing.md`）+ `CHANGELOG.md`（5.7 KB，release-please 生成）。

本仓库没有 Postgres：`server/dao/src/main/resources/db.properties` 第 1 行是 `jdbc:mysql://localhost:3306/school`，第 4 行驱动是 `com.mysql.cj.jdbc.Driver`，持久层是 MyBatis；全仓库 grep `postgres|supabase|pgvector` 除本技能自身外 0 命中。MySQL 与 Postgres 的差距不是方言问题：RLS、`pg_stat_statements`、`pgvector`、`pg_cron`、`CREATE INDEX CONCURRENTLY`、`timestamptz`、`skip locked` 语义在 MySQL 里都没有对应物。

和上一批 React 技能一样，这批规则眼下没有执行对象，价值在于：将来换成 PG、或迁到云上托管 PG 时，schema 设计、索引选择、N+1、长事务这些判断是现成的；`query-*` 与 `data-*` 两组即使留在 MySQL 也能当检查清单用。

## Decision

整份复制到 `.agents/skills/supabase-postgres-best-practices/`，只动三处：

- **目录名与 `name` 都不改**，仍叫 `supabase-postgres-best-practices`。上一批 React 技能我改过名字（统一 `react-` 前缀、去 `vercel-` 前缀），这次不改：`name` 与目录名本来就一致，不产生 `name-mismatch`；上游名带 `supabase-` 是准确的归属信息，改了反而看不出这批规则从哪来。代价是技能列表里会多一个「本仓库没在用」的厂商名，靠正文的「适用范围」抵消。
- **`description` 写成单行中文**，与仓库其余技能一致。原描述是 800 多字符的英文单行引号串，塞满触发条件（RLS、迁移、EXPLAIN、连接耗尽、pg_restore……），压缩后保留同样的触发词。
- **加一节「适用范围」**，写明只对 PostgreSQL 成立、本仓库是 MySQL 8 + MyBatis、没有 Supabase，并点明 `security-*` 与 `advanced-*` 无处落地、`query-*` / `data-*` / `lock-*` 三组的判断能用但语法要换。

删掉 `CHANGELOG.md`。它是上游技能自身的版本流水（1.0.0 → 1.1.1），记的是上游哪次提交加了哪几条规则，对执行规则的 Agent 没有信息量；版本号已经写进 `metadata.version`。

`references/` 34 份逐字节复制，未改一字（`Get-FileHash` 全部一致）。

## Alternatives considered

### 改名 `postgres-best-practices`

去掉 `supabase-` 前缀，理由是技能自己宣称「for Postgres running anywhere」，而本仓库既没有 Supabase 也可能永远不用。否掉的原因：改名后与上游对不上，将来上游出 1.2.0 时要先人工确认「`postgres-best-practices` 就是上游那个」，同步成本换来的只是列表里少一个词。React 那批改名是因为三份技能各自带不同前缀、不成体系，这里只有一份。

### 干脆不移植

本仓库是 MySQL，规则全部要换方言才能用。否掉的原因：`query-missing-indexes`、`data-n-plus-one`、`data-pagination`、`lock-short-transactions`、`schema-data-types` 这些条目讲的是判断而不是语法，MySQL 下同样成立；换 PG 的路径也没有消失，留一份比事后翻上游快。

### 只挑与 MySQL 兼容的条目移植

比如只带 `query-*` 与 `data-*`。否掉的原因：31 份合计 1400 行，按方言筛一遍要逐条判断，筛错一条就没了；整份留着，让「适用范围」交代边界，比删减更可逆。

### 保留 `CHANGELOG.md`

好处是升版时能看出上游动了哪几条规则。否掉的原因：5.7 KB 全是上游发布记录，Agent 每次装技能都要多读一份与执行无关的文件；真要升版，比对 `references/` 的 hash 比读 changelog 准。

## Consequences

收益：31 条带错误/正确 SQL 对照的规则，覆盖索引、连接池、RLS、锁、分页、JSONB、全文检索；`references/_template.md` 与 `_contributing.md` 让新增规则有统一格式可循。

代价：

- 8 类里 `security-*`（3 条）与 `advanced-*`（2 条）在 MySQL 下基本无用，占总数 16%。`conn-*` 里讲 PgBouncer 与 prepared statement 的部分也不适用。
- 正文的例子是 PG 语法（`CONCURRENTLY`、`timestamptz`、`ANY(ARRAY[...])`、`EXPLAIN (ANALYZE, BUFFERS)`），直接抄到 MyBatis 的 XML 里会报错。用之前必须先换方言。
- 技能名带 `supabase-`，本仓库不用 Supabase，列表里这个名字会让人误以为项目依赖它。
- 上游升级仍需手工比对 hash，没有生成脚本。
- 「适用范围」写死了当前是 MySQL 8。哪天真换 PG，这一节要改。

## Verification

- `npm run verify-skills`：`checked: 17 skill(s), 66 path reference(s), 0 error(s), 1 warning(s)`，唯一 warning 仍是既有的 `0test-skill`。
- `npm run verify-notes`：树与相对链接、格式、归档三个脚本全过。
- 内容保真：34 个 `references/` 文件逐个 `Get-FileHash` 与上游比对，0 处差异；`SKILL.md` 换成 LF，与其他技能一致。
- 装载验证：写入后 harness 技能列表出现 `supabase-postgres-best-practices`，改完 `description` 后返回的是新描述，frontmatter 能解析。
- 未做：没有在真实 Postgres 上跑过任何一条 SQL，也没有在 MySQL 上逐条验证哪些判断可迁移；「哪些条目真能复用」目前只有推断，没有实测。

上一批 React 技能的移植见 [2026-11-01-port-react-skills.md](2026-11-01-port-react-skills.md)。
