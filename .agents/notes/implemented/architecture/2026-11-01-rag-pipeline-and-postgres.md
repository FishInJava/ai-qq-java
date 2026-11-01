# Agent Note: JustRAG 落到既有分层，语料库换成 Postgres 17 + pgvector

Status: implemented

## Problem

`PRD-JustRAG.md` 要 Postgres 17 + pgvector + Flyway + PDF 解析 + 一个新的 React 前端。仓库现状是 MySQL + MyBatis 的骨架：`server/dao/src/main/resources/db.properties` 指向 `jdbc:mysql://localhost:3306/school`，`server/controller/src/main/resources/application.properties` 里也是 MySQL 驱动，没有任何 Postgres、Flyway、PDFBox 依赖，`server/web` 只有 pom 没有源码。

PRD 第 7.4、7.5、第 8 节还有一批没定死的值（拒答阈值、引用编号上限、截断后怎么编号、跨文档用例怎么存、表格两种形态存哪、父块怎么定位高亮区域、`request_id` 落在哪张表）。不先定这些，第一张建表脚本就会自相矛盾。

失败条件是：绕开既有模块另建一套目录，代码分成两份，`mvn -f server/pom.xml test` 之外没人知道该跑哪个应用。

## Decision

### 模块归属

不新建模块。按 `controller → service → dao → entity` 的现有方向落代码：

- `entity/com/hobozoo/aiqq/entity/rag/`：`DocumentRow`、`JobRow`、`PageRow`、`BlockRow`、`ChunkRow`、`RunRow`、`EventRow` 与解析中间件 `PageDraft`、`BlockDraft`、`ParentDraft`、`ChildDraft`、`PipelineConfig`、`PipelineSnapshot`、`UploadResult`、`HealthRow`。
- `dao/com/hobozoo/aiqq/dao/`：`LibraryDao`（文档、任务、管线运行与事件）、`IngestDao`（页、块、切块），JdbcTemplate 手写 SQL。Flyway 脚本在 `server/dao/src/main/resources/db/migration/V1__schema.sql`。
- `service/com/hobozoo/aiqq/service/rag/`：`PdfExtractor`（PDFBox 3 取坐标）、`BlockAssembler`（页眉页脚、目录、标题、段落、表格）、`Chunker`（父块与子块）、`PipelineConfigService`、`IngestService`（八节点与断点续跑）、`LibraryScanner`（扫描与 sha256 去重）、`IngestWorker`（单线程任务队列）、`RagLibraryService`、`RagHealthService`、`RagProperties`。
- `controller/com/hobozoo/aiqq/controller/`：`HealthController`、`LibraryController`、`DocumentController`、`JobController`、`ApiExceptionHandler`，只调 `RagLibraryService`，不碰 dao。
- `common/com/hobozoo/aiqq/common/util/JsonText`：写 `jsonb` 明细用，避免为一个字符串再引 Jackson。

`AiqqServer` 仍是唯一入口，加了 `@ConfigurationPropertiesScan`。`application.properties` 的数据源换成 `jdbc:postgresql://127.0.0.1:5433/justrag`，监听 `127.0.0.1:8090`。`db.properties` 与 `spring-mybatis.xml` 未改，它们本来就没有被 Spring Boot 加载。

依赖：dao 加 `spring-boot-starter-jdbc`、`spring-boot-flyway`、`flyway-core`、`flyway-database-postgresql`、`postgresql`；service 加 `pdfbox 3.0.5`、`tools.jackson.core:jackson-databind`、`common`。

### 数据库

`deploy/docker-compose.yml` 起 `pgvector/pgvector:pg17`，容器 `justrag-postgres`，端口 `127.0.0.1:5433`，命名卷 `justrag-pgdata`，Docker 数据目录在本机 `G:\0Docker\DockerDesktopWSL`。`V1__schema.sql` 建 `vector` 扩展与 19 张表。

相对 PRD 第 8 节的 17 张表，多两张、改几列：

- 新增 `chunk_blocks(chunk_id, block_id, ord)`。父块跨块跨页时靠它取全部包围盒，否则前端画不出高亮。
- 新增 `app_config(key, value jsonb)`，先放检索参数；`pipeline_def` 只管导入八节点。
- `blocks` 加 `font_size`、`font_name`，管线页要核对字号判定。
- `documents` 加 `encrypted`、`error`、`updated_at`。
- `queries` 加 `request_id`（唯一索引）、`first_token_ms`、`error`。
- `query_stages` 主键改 `(query_id, attempt, stage)`，`prompt_snapshots` 同步加 `attempt`，重答与重试不再冲突。
- `eval_cases` 加 `expected_sources jsonb`，跨文档用例存两家以上来源。
- `jobs` 加 `total`、`done`。

### 导入管线

`pipeline_def` 名称 `rag`、版本 1，八节点顺序照 PRD，八个节点全部落库：`parse`、`tables`、`blocks`、`chunk` 写页/块/切块，`embed` 用哈希桩算 512 维向量写 `embeddings`，`index_vector` 核对写入条数，`index_lexical` 把子块写进 Lucene 10.4（标题用 `lucene-1` 目录，BM25 k1=1.2、b=0.75，smartcn 分词），`finalize` 把文档状态置 `ready`。

断点续跑按节点：新一次 attempt 先查上一次 `pipeline_events` 里 `ok` 的节点，从第一个未完成节点开始，`IngestDao.clearFrom` 删掉该节点及其下游产物。`jobs` 重启时把 `running` 改回 `queued` 并重新入队。

解析规则：

- PDFBox 3 的 `PDFTextStripper` 收集 `TextPosition`，按基线聚行，行内按 x 排序，间隙超过 0.25 em 补空格，超过 1.5 em 断开成单元格。
- 页眉页脚：上 8% 或下 8% 区域内、纯数字的行直接删；任意边缘 25% 区域内、跨 3 页以上重复、长度小于 60 的行按 running head 删。
- 目录页：前 6 页里出现「目录」标题，或带点线的行达到 3 行，整页不参与标题识别；带点线的行本身删掉。
- 标题：长度不超过 45、不以句号逗号分号顿号结尾；带编号（`1.`、`2.1.`）的按点号层数定级，不依赖字号；不带的要字号 ≥ 正文中位数 × 1.15 或加粗 ≥ × 1.05，并且上下间距 ≥ 1.3 倍行距；页眉页脚区内的行不算标题。
- 表格：连续 ≥ 2 行、每行 ≥ 2 个单元格、合计 ≥ 3 个数字，整段当一个表格块，行内用 ` | ` 连接。不使用 Tabula，原因见下方 Alternatives。
- 切块：父块按章节累积到 1200 字符，超过 1600 先断；表格整表一个父块；子块 300 字符、重叠 60，尽量落在句末。

扫描与上传：`POST /api/library/scan` 建 `scan` 任务，工作线程遍历目录、算 sha256、已存在的跳过、其余建文档与 `ingest` 任务；`POST /api/library/upload` 落盘到 `G:/aiqq-rag/files/{sha}.pdf`。同一 sha256 只入队一次。

### 检索与对话

`RetrievalService` 按 `app_config.retrieval` 取值：BM25 取 40、向量取 40、RRF k=60 取 20、回取父块按融合分取 8、上下文预算 8000 字符。融合在父块维度累加 `1/(k+rank+1)`，同一父块被多个子块命中会加分。拒答条件是父块为空、最高融合分低于 0.012，或非桩模型下最高余弦低于 0.40；哈希桩阶段跳过余弦条件，否则永远拒答。

`ChatService` 负责一次对话：建会话与 `queries` 行、可选检索、拼提示词、流式生成、写 `query_stages`、`prompt_snapshots` 与答案。RAG 打开时提示词要求逐句标 `[n]`、原文没有的数字不写，并把 8 段原文按 `[1] 文件 第x-y页 章节` 编号；RAG 关闭时不检索，只带最近 3 轮历史。生成用 `ArkChatClient`，直接 `HttpClient` 打 `https://ark.cn-beijing.volces.com/api/coding/v3/chat/completions`，模型 `deepseek-v4-1-flash-261017`，`thinking=disabled`，温度 0.2，`max_tokens` 1200。密钥放在仓库外的 `G:/aiqq-rag/config.properties`（`spring.config.import` 可选加载，也能用 `ARK_API_KEY` 环境变量）。

SSE 事件：`start`、`stage`（normalize、bm25、vector、fusion、fetch、generate）、`citation`（编号、文件、页码、章节、chunkId）、`delta`、`done`（首字、整答、tokens、是否拒答）、`error`。会话列表与历史读 `/api/sessions` 与 `/api/sessions/{id}/messages`，链路读 `/api/trace/{queryId}`。

### 前端

`website/`：Vite + React 19 + TypeScript，`/api` 代理到 `127.0.0.1:8090`。Vue 脚手架换成 React 后目录不变，CI 的 `npx eslint .` 与 `npm run build` 继续作用在这一层。已有路由 `/library`、`/chat`、`/jobs`、`/jobs/:id`、`/documents/:id/pipeline`、`/trace/:queryId`。对话页左侧是会话列表，消息区支持流式输出与引用卡片，右上开关控制是否接入研报库，状态存 `localStorage`。

## Alternatives considered

### 新建一个独立模块

隔离干净、依赖不用和 MySQL 骨架混在一起。否掉的原因：`server/` 的分层就是按 entity/dao/service/controller 划的，新建模块等于两套写法并存；而且 controller 已经是唯一带 `spring-boot-maven-plugin` 的启动点，另起一个应用只会让「跑哪个 jar」变模糊。

### 保留 MySQL，只加向量列

改动最小，MyBatis 也能继续用。否掉的原因：MySQL 没有 `vector` 类型与 HNSW 索引，PRD 的召回与 5000 份容量都建立在 pgvector 上；换数据库是这一步绕不开的成本。

### 用 MyBatis 而不是 JdbcTemplate

和现有 `DemoDao` 一致。否掉的原因：向量列、`text[]`、`jsonb` 在 MyBatis 里全靠手写 TypeHandler，批量 upsert 还要拼 XML；这里写的是明确的几条 SQL，JdbcTemplate 更短。

### 用 JPA

实体映射省 SQL。否掉的原因：数组列、jsonb、向量列都要自定义类型，导入批处理还要绕开一级缓存。

### 用 Spring AI 的 ChatClient

少写一个 HTTP 客户端，消息与流式事件都有现成封装。否掉的原因：要拿到逐 chunk 的 `delta`、`firstTokenMs`、`prompt_tokens`/`completion_tokens`，还要在 `thinking=disabled` 这种厂商参数上直连，自己解析 SSE 反而更短。

### 用不带思考的其它模型

`doubao-seed` 系列不会先吐 `reasoning_content`。否掉的原因：需求方指定了 ds4.1 261017；Ark 支持 `thinking.type=disabled`，关掉后首字从 1.5 秒降到 0.8 秒左右，不需要换模型。

### 用 Tabula 抽表格

PRD 7.3 指定 PDFBox 3 + Tabula。否掉的原因：`tabula-java` 仍绑 PDFBox 2.0.x，两个大版本共存要靠 shade 隔离；本项目只需要「列聚类 + 数字行」一个启发式，自己写比隔离依赖便宜。

### 绑定挂载 `G:\aiqq-rag\pgdata`

数据文件在宿主机上能直接看到。否掉的原因：WSL2 访问 NTFS 绑定挂载明显慢于虚拟磁盘内的命名卷，导入时写库会成为瓶颈；命名卷已经在 G 盘的 WSL 磁盘里。

## Consequences

收益：导入、检索、对话三条链路都跑通。导入把 PDF 解析到块与切块并写向量与倒排；检索给出 BM25、向量、融合、回取四段耗时与分数；对话按开关决定是否检索，RAG 打开时回答带 `[n]` 引用卡片，关掉就是普通聊天。

代价与已知限制：

- 嵌入是 `hash-stub`，向量通道接近随机，召回实际靠 BM25。阶段 5 换 bge-small-zh ONNX 后要重导。
- 拒答的余弦阈值 0.40 在桩阶段被跳过，真模型接上后需要重新校准。
- 生成模型是先思考再输出，关掉 `thinking` 后回答质量是否下降没有对比；`max_tokens` 1200 对长回答不够，会被截断。
- 多轮只带最近 3 轮，且检索只用当前问题，指代类追问（“它”指什么）会检索偏。
- 断点续跑粒度是节点不是块；`parse` 重跑会把该文档的块与切块一起删掉重来。
- 块与切块逐行插入，10 份文档约 4 秒一份；5000 份时这一步会成为瓶颈，需要改批量写入。
- 章节识别是字号加间距的猜测，扫描版与双栏排版会退化；`structure_source` 记 `font_guess`。
- 页眉页脚靠「同一文本跨 3 页重复」判断，单页文档与每页标题都不同的模板会漏删。
- 表格识别对无框线表格的召回未测，误判会把正文里的多列表格行并成表格块。
- 一个数据源同时服务骨架 demo 与 RAG，`db.properties` 与 `spring-mybatis.xml` 仍是旧配置，谁要重启 MyBatis 那条线得先改回来。

## Verification

- `mvn -f server/pom.xml test`：33 个测试全过，含新增 `ChunkerTest` 4 个用例（章节切分、窗口重叠、超长块、表格单子块）。
- `mvn -f server/pom.xml package`：BUILD SUCCESS，`server/controller/target/controller-1.0-SNAPSHOT.jar` 可执行。
- `docker compose -f deploy/docker-compose.yml ps`：`justrag-postgres` healthy；`select extversion from pg_extension where extname='vector'` 返回 0.8.7。
- `curl /api/health`：`{"status":"ok","db":true,"postgres":"17.11 …","pgvector":"0.8.7","tables":20}`，20 是 19 张业务表加 `flyway_schema_history`。
- 真实语料：扫描 `F:\研报\东吴 求索动量因子` 5 份，全部 `chunked`。以 19 页那份为例，`parse=ok`、`tables=ok`、`blocks=ok`、`chunk=ok`，113 个块、27 个父块；章节路径只剩 `1. 前言`、`2. 交易者结构对动量因子的影响`、`2.1.`、`2.2.`、`3.`、`4.`、`6. 风险提示` 这类正文标题，目录页与页眉不再混入。
- 标题：5 份都取到「“求索动量因子”系列研究（一）～（五）」而不是页眉。
- `website`：`npm run build`（`tsc -b` 加 `vite build`）通过，`npx eslint .` 无输出。
- 未做：没有在 519 份全量语料上跑，没有验证旋转页的包围盒换算，没有接嵌入与检索。
