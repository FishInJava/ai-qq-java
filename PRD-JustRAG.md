# JustRAG 产品需求文档

版本 v2 · 2026-11-01

## 0. 变更记录

v1 → v2 的改动：

| 项 | v1 | v2 | 原因 |
|---|---|---|---|
| 后端版本 | Spring Boot 3.5 + Spring AI 2.0 | Spring Boot 4.1.2 + Spring AI 2.0.2 | Spring AI 2.x 只支持 Boot 4.x，仓库已是 4.1.2 |
| 前端位置 | 未定 | `website/` 由 Vue 脚手架换成 React 19 | CI 已经按 `website/` 构建，沿用同一目录 |
| KR2 验收阶段 | 阶段 3 | 阶段 5 | 阶段 2、3 用哈希桩嵌入，语义通道接近随机 |
| 延迟指标 | P50 < 8 秒 | 首字 P50 ≤ 3 秒，整答 P50 ≤ 15 秒 | 8 秒整答做不到，两个指标必须分开 |
| 验收问题 | 5 条 | 20 条（12 单点 / 5 跨文档 / 3 负例） | 5 条测不出差异，负例才能测拒答 |
| 语料规模 | 519 份 / 5.48 GB | 519 份 / 5.11 GB 实测，确认会扩到 5000 份 | 实测值 + 容量决策 |
| 嵌入模型 | ONNX 量化版，未指定 | `bge-small-zh-v1.5`，512 维，本地 ONNX | 4 核 CPU 跑 bge-m3 要 2–5 天 |
| 重排 | 阶段 5 接真模型 | 本期直通，只留接口 | 4 核 CPU 重排 20 条要 30 秒以上 |
| 词法引擎 | Lucene 9 + smartcn | Lucene 10.4 + smartcn | `lucene-analysis-smartcn` 10.4.0 已发布 |
| 新增章节 | — | 数据模型、管线节点、接口契约、切块与检索参数、引用与拒答、一致性 | 原文只有功能清单，照着写会卡住 |

## 1. 摘要

JustRAG 是一台机器上运行的研报检索问答工具，服务一位用户：需要靠原文核对结论的量化研究者。它把本地 PDF 建成可检索的库，每个结论都带文件名、页码和原文块，答案里在原文查不到的数字会被标出来。语料现在 519 份、5.11 GB，会扩到 5000 份。

现在能做，是因为解析、嵌入、向量库都能在本机跑，不需要 GPU 或向量服务，生成按 token 付费。生成时只把检索到的 8 段原文发给模型，全量语料不出网。

## 2. 联系人

YG：需求方、唯一用户、验收方。

## 3. 背景

语料在 `F:\研报`。实测 519 份 PDF、5.11 GB，主体是 P神量化资料 389 份、QuantsPlaybook 81 份，其余是券商专题。中文研报 9 到 25 页，每页 700 到 900 字符；英文 TASC 杂志 48 到 100 页，配图密集。40 份抽样里 27 份带 owner password，空口令都能打开；中文研报基本没有书签。

运行机器：

| 项 | 实测 |
|---|---|
| CPU | i5-7500，4 核 4 线程 |
| 内存 | 16 GB，空闲约 5 GB |
| 磁盘 | G: 剩 109 GB（数据库与索引放这里），F: 剩 43.7 GB |
| Docker | 已安装 29.7.2，守护进程未启动 |

现在的做法是用阅读器全文搜索关键词，命中后人工翻页，确认那一段是不是要找的内容；跨文档比较时同时开四五个窗口。问题不是找不到文件，是找到之后无法确认哪一页哪一句是依据。结论写进报告，回头没法回溯；数字抄错没有人拦。

尚未测量的语料指标：扫描版页占比、加密文件实际数量、表格页占比。这三项决定 OCR 排期和解析耗时，阶段 1 前补测。

## 4. 目标与关键结果

目标：每个结论都能回到具体一页，导入和问答的每一步都留下可回看的记录。

这是单人本地工具，不上云，不做多租户，先把导入到问答这一条路走通。

关键结果，按阶段验收，阶段定义见第 11 节。

| 编号 | 阶段 | 判据 |
|---|---|---|
| KR1 | 1 | 10 份测试文档全部导入成功；每份能看到页级字符数、丢弃原因、块树（阶段 1 先用只读 JSON 页面，正式页面在阶段 4） |
| KR2 | 5 | 20 条验收问题：单点核对 12 条中至少 9 条首个引用落在正确文档和页码；跨文档 5 条中至少 3 条完整给出两家以上来源；3 条负例全部拒答 |
| KR3 | 5 | 首字 P50 ≤ 3 秒，整答 P50 ≤ 15 秒；链路页能看到 BM25、向量、融合、重排四段耗时 |
| KR4 | 5 | 答案中原文查不到的组成数字，没有一个漏标；每条答案误标不超过 1 个 |

指标定义：

- 首字：从发出请求到屏幕上出现第一个答案字符。
- 整答：从发出请求到数字自检结束、最后一个字符到达。
- P50：把所有验收问题的耗时应答时间排序，取中间那个。

## 5. 用户细分

产品只有一位用户，但这位用户带着三种任务来。按任务分，因为三种任务对召回范围、答案形态和可信度的要求不一样。

- 单点核对：写策略或读研报时，确认一个指标的定义、参数或公式原文怎么说。一次一个问题，要准确的那一段。
- 跨文档比较：选因子或写综述时，比较两篇以上研报对同一件事的处理差异。要并列的原文。
- 新语料通读：拿到一期 70 页的英文杂志或一份陌生研报，先判断值不值得细读。

约束：一个人用，没有权限、协作、审计需求；数据留在本机，只监听 `127.0.0.1`；语料中英混排，中文分词不能依赖要自己编译的数据库扩展。

## 6. 价值主张

### 6.1 单点核对

| 项 | 内容 |
|---|---|
| 谁 | 写策略或读研报的量化研究者，一个人用 |
| 为什么 | 要确认一个指标怎么构造，记不清原文的措辞和参数 |
| 现在怎么做 | 阅读器搜关键词，翻三四页找到疑似段落，再判断是不是同一回事；找不到就凭记忆写 |
| 我们怎么做 | 问题命中子块后向上取父块整段，答案每句带文件名、页码和章节路径，点一下跳到 PDF 那一页并高亮 |
| 之后什么样 | 一次核对从十几分钟降到一次提问，写进报告的数字都能指回原文 |
| 替代方案 | 通用助手读不到本地 PDF，上传又受体积限制；手动搜索没有语义召回，换个说法就搜不到 |

一句话：给定一个指标名，JustRAG 在几秒内给出原文段落、页码和可跳转的引用。

### 6.2 跨文档比较

| 项 | 内容 |
|---|---|
| 谁 | 同一个研究者，处在选因子或写综述的阶段 |
| 为什么 | 不同券商对去极值、中性化、择时信号的做法不同，要看清差异再决定用哪个 |
| 现在怎么做 | 同时开四五个 PDF，逐个搜索，把段落复制到临时文档里对齐，漏一篇是常事 |
| 我们怎么做 | 两路各取 40 条再融合，父块回取保住整段语境，提示词按文档分组，答案并列呈现各家做法和出处 |
| 之后什么样 | 一次提问拿到带出处的对照，能看出哪一家在哪一步不同 |
| 替代方案 | 通用助手没有这份语料；手工整理的办法在文档超过十篇后维持不住 |

一句话：JustRAG 把几篇研报对同一件事的处理并排放进一次回答，每条都带出处。

### 6.3 新语料通读

| 项 | 内容 |
|---|---|
| 谁 | 同一个研究者，面对刚下载的一期杂志或一份陌生研报 |
| 为什么 | 70 页里可能只有两页有用，先要知道讲了什么再决定读不读 |
| 现在怎么做 | 读目录，翻前几页，跳到结论页；英文杂志没有书签时只能从头翻 |
| 我们怎么做 | 先问这篇文章讲了什么，答案按章节组织并给出页码区间，需要细节时在同一会话里追问 |
| 之后什么样 | 判断一份文档值不值得细读从半小时降到几分钟 |
| 替代方案 | 通用助手能总结，但会编造章节名和页码，也没法点回原文验证 |

一句话：JustRAG 先用带页码的回答说清一份长文档讲了什么，再让人决定读哪几页。

本期这一条只靠检索到的段落回答，不做全书摘要，章节摘要树只建表不实现。验收口径收窄为：能列出这份文档的 3 个主要章节，每个章节给页码区间，且至少 2 个与原文一致。

## 7. 方案

### 7.1 使用流程

导入：文档库页点扫描目录或上传文件，任务进队列，跑批页按文档显示解析、切块、嵌入、索引的阶段进度，刷新或断线后能续上。

提问：对话页输问题，左侧会话历史，中间流式答案与引用卡片，右侧 pdf.js 预览。点引用跳到对应页并高亮；答案下方数字校验条标出原文里找不到的数字，点开看原文对照。

排查：每题有链路页，时间轴列出归一化、BM25、向量、融合、父块回取、重排、提示词、生成、数字自检的耗时和数据。每份文档有管线页，上半是页级清单，下半是可展开的块树。

六条路由：`/library`、`/chat`、`/jobs`、`/jobs/:id`、`/documents/:id/pipeline`、`/trace/:queryId`。

### 7.2 第一期必须有的功能

1. 增量导入。目录扫描和网页上传进同一张任务表，按阶段记进度，进程重启后从未完成的阶段继续；重复文件按 sha256 跳过，删除是软删除。
2. 统一中间表示。PDFBox 取文本与字号，Tabula 抽表格，输出带类型、页码、包围盒和章节路径的 DocIR。切块器只认这个结构，换解析器不用改切块。
3. 切块模板注册表。研报、杂志、通用三个模板按文档类别自动匹配，单文档可覆盖；管线定义存在 `pipeline_def`，八个固定节点，按版本执行，每份文档记录跑的是哪一版。
4. 双通道检索与融合。Lucene 和 pgvector 各取 40 条，RRF 融合取 20，回取父块与表格原文，重排先直通，取 8 条进提示词。
5. 带引用的流式回答。DeepSeek 生成，每块标出文件名、页码、章节，SSE 逐字返回。
6. 数字自检。抽出答案里的金额、百分比、比率和整数，归一化后在命中块原文里匹配，未命中的标出，并允许带只使用原文数字的约束重答。
7. 全链路记录。问答每阶段一行 `query_stage`，导入每阶段一行 `pipeline_event`，提示词全文存 `prompt_snapshot`，三个可视化页面直接读这几张表。
8. 反馈入集。答错或不完整可以标注，写进 `feedback`，需要时生成 `eval_case` 供回归。

### 7.3 技术选择

| 层 | 选择 |
|---|---|
| 后端 | Java 21 + Spring Boot 4.1.2 + Spring AI 2.0.2，一个 jar 跑完导入和问答 |
| 存储 | PostgreSQL 17 + pgvector，Docker 映射到 `127.0.0.1:5433` |
| 词法检索 | 内嵌 Lucene 10.4，`lucene-analysis-smartcn` 分词，避开在 Windows 上编译 zhparser |
| 解析 | PDFBox 3 + Tabula，表格抽取失败按含三个以上数字的行重建纯文本表 |
| 嵌入 | `bge-small-zh-v1.5`，512 维，ONNX 量化，本地 CPU 跑；阶段 0 到 4 用确定性哈希桩 |
| 重排 | 本期直通，接口预留；真重排等有基线再上 |
| 生成 | DeepSeek `deepseek-chat`，温度 0.2 |
| 前端 | `website/`：Vite + React 19 + TypeScript，引用预览用 pdfjs-dist |
| 迁移 | Flyway |

内存预算：Spring Boot 堆 1.5 GB，Postgres `shared_buffers` 1 GB，Lucene 走堆内，ONNX 模型常驻约 0.5 GB，Docker Desktop 自身 2 GB。合计接近 5 GB，是这台机器空闲内存的上限，不能再加常驻服务。

### 7.4 切块与检索参数

切块：

| 项 | 取值 |
|---|---|
| 父块长度 | 目标 1200 字符，下限 600，上限 1600，不跨章节 |
| 子块长度 | 300 字符，重叠 60 |
| 切分单位 | 字符（中文友好）；嵌入前按模型 tokenizer 截断到 512 token |
| 表格 | 整表一个块，不切；同时存 Markdown 与纯文本两种形态 |
| 章节路径 | 最多三级，标题行按字号与行距识别 |
| 页眉页脚 | 同一位置跨 3 页以上重复、长度小于 50 的行删除，记入 `removed` |
| 跨页段落 | 同章节、上行不以句号结尾、下行不以编号开头时合并 |

检索：

| 步 | 参数 |
|---|---|
| BM25 | k1 = 1.2，b = 0.75，取 40 |
| 向量 | cosine，HNSW（m = 16，ef_construction = 64，ef_search = 40），取 40 |
| 融合 | RRF，k = 60，取 20 |
| 回取 | 按父块 id 去重，表格块整表回取 |
| 重排 | 直通，取 8 |
| 上下文 | 预算 8000 token，超出按融合分截断 |
| 拒答 | 8 块融合分全部低于阈值，或最高向量相似度低于 0.40（初值，阶段 5 调） |

### 7.5 引用、拒答、数字自检

引用：

- 每个父块分配编号 1 到 8，提示词要求每句末尾标 `[n]`。
- 流式输出时解析 `[n]`；编号不在 1 到 8 的，丢弃这段标记并记 `invalid_citation`。
- 引用卡片显示：文件名、页码区间、三级章节路径、块首 40 字。
- 父块跨页时给页码范围；正文里优先显示起始页。

拒答：

- 触发条件见 7.4。触发后回答固定措辞"现有资料里没有找到相关内容"，不生成任何数字。
- 拒绝时仍然展示最近的 3 个候选块，让用户判断是不是问题表述偏了。

数字自检：

| 数字类型 | 抽取规则 |
|---|---|
| 百分比 | `\d+(\.\d+)?%` |
| 倍数 | `\d+(\.\d+)?倍` |
| 金额 | `\d[\d,]*(\.\d+)?(元|万元|亿元|美元)` |
| 比率 | 上下文含"率/比/系数/α/β/σ"的数字 |

归一化：去千分位，全角转半角，中文数字转阿拉伯数字，`0.25` 与 `25%` 视为等值，万与亿统一换算。

匹配范围是 8 个父块的原文。未命中标红，悬浮显示"原文无此数字"。

重答只在用户点"只用原文数字重答"时触发，不自动触发，因为会让延迟翻倍。

### 7.6 一致性与幂等

- 真源是 Postgres，Lucene 是派生索引。
- 写入顺序：Postgres 事务提交，再写 Lucene。Lucene 失败进 `index_retry`，不阻塞问答，由定时任务重试。
- 重建：新索引写到 `lucene-{pipeline_version}` 目录，完成后原子切换，旧目录保留 7 天。
- 软删除：`documents.deleted_at` 置位后检索立即过滤，硬删由定时任务清两处。
- 嵌入幂等：块内容 sha256 作键，重跑时跳过未变块。
- 问答幂等：每次请求带 `request_id`，同一 id 返回缓存结果，不重复调用模型。
- 上传与扫描：同一 sha256 只入队一次；已存在则只更新路径。

### 7.7 假设与开放问题

- 扫描版页占比未测。占比高则本期这批文档只能显示"无法解析"，OCR 需要单独立项。
- 5000 份的实际文本量未测，全量嵌入时长按 519 份实测外推，误差可能在一倍以内。
- Tabula 对无框线表格的识别率没测。方案是抽取失败时按含三个以上数字的行重建纯文本表。
- 章节边界的人工纠正没有落进功能清单，本期不做，靠 `structure_source` 标出哪些文档是靠字号猜的。
- 生成质量受 DeepSeek 的限流和超时影响。按 SSE 断流重连处理，问答默认重试一次，失败原因记进 `query_log`。
- 没有登录。能操作这台机器的人都能读全部语料。

## 8. 数据模型

十七张表，Postgres 17，Flyway 管理。

| 表 | 关键列 |
|---|---|
| `documents` | id, path, sha256, filename, title, category, pages, bytes, language, structure_source, pipeline_version, status, deleted_at, created_at |
| `pages` | document_id, page_no, width, height, rotation, char_count, is_empty |
| `blocks` | id, document_id, page_no, block_type(text/table/heading), bbox(x0,y0,x1,y1), text, order_no, section_path |
| `chunks` | id, block_id, parent_id, level(parent/child), text, char_count, page_from, page_to, section_path, sha256 |
| `embeddings` | chunk_id, model, dim, vector `vector(512)`, created_at |
| `jobs` | id, type(scan/upload/ingest), source_path, document_id, status, stage, progress, error, created_at, updated_at |
| `pipeline_def` | id, name, version, definition(jsonb), created_at |
| `pipeline_runs` | id, document_id, pipeline_def_id, status, started_at, finished_at, stats(jsonb) |
| `pipeline_events` | id, run_id, node, status, started_at, duration_ms, detail(jsonb) |
| `sessions` | id, title, created_at, updated_at |
| `queries` | id, session_id, question, answer, model, tokens_in, tokens_out, latency_ms, status, created_at |
| `query_stages` | query_id, stage, duration_ms, payload(jsonb) |
| `prompt_snapshots` | id, query_id, system_prompt, user_prompt, blocks(jsonb), created_at |
| `feedback` | id, query_id, verdict(wrong/incomplete/ok), note, created_at |
| `eval_cases` | id, question, kind(pos/neg/cross), expected_doc, expected_page_from, expected_page_to, expected_keywords, created_at |
| `index_retry` | id, chunk_id, operation, attempts, last_error, next_at |
| `artifacts` | id, document_id, kind, payload(jsonb), created_at（预留给章节摘要树、图表理解） |

坐标约定：`bbox` 存 PDF 用户空间，单位 point，原点在页面左下角，随 `pages.rotation` 记录页面旋转。前端换算：`x' = (x - cropX0) * scale`，`y' = (cropY1 - y) * scale`，再按 `rotation` 旋转。

## 9. 管线节点

`pipeline_def` 固定八个节点，按顺序执行，每个节点单独记 `pipeline_event`，失败可从该节点继续。

| 序 | 节点 | 输入 | 输出 | 失败处理 |
|---|---|---|---|---|
| 1 | `parse` | PDF 文件 | `pages` 表 | 加密且非空口令：记 `locked`，跳过 |
| 2 | `tables` | 页面 | 表格块 | 失败回退为纯文本行，记 `table_fallback` |
| 3 | `blocks` | 页面与表格 | `blocks` 表，章节路径 | 字号识别失败：全文一个章节，记 `structure_source=font_guess` |
| 4 | `chunk` | blocks | `chunks` 表 | 空块记 `discard_reason` |
| 5 | `embed` | child chunks | 向量 | 单块失败重试 3 次，仍失败跳过并记 |
| 6 | `index_vector` | 向量 | pgvector | 批量写入，冲突则 upsert |
| 7 | `index_lexical` | 子块文本 | Lucene | 失败进 `index_retry` |
| 8 | `finalize` | 全部 | 文档状态、统计 | 清理临时文件 |

## 10. 接口契约

REST，前缀 `/api`，返回 JSON。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/health` | 存活与数据库连通 |
| POST | `/api/library/scan` | 扫描目录入队 |
| POST | `/api/library/upload` | 上传 PDF 入队 |
| GET | `/api/documents` | 文档列表，可按状态过滤 |
| GET | `/api/documents/{id}` | 文档详情 |
| GET | `/api/documents/{id}/pipeline` | 页级清单与块树 |
| GET | `/api/documents/{id}/file` | PDF 流，支持 Range |
| GET | `/api/jobs`、`/api/jobs/{id}` | 任务与进度 |
| POST | `/api/chat/stream` | 问答，SSE |
| GET | `/api/sessions`、`/api/sessions/{id}/messages` | 会话与历史 |
| POST | `/api/feedback` | 标注 |
| GET | `/api/trace/{queryId}` | 链路详情 |
| GET | `/api/eval/cases`、`POST /api/eval/run` | 验收集与跑分 |

SSE 事件：

```
event: start        data: {"queryId":"q_1","sessionId":"s_1"}
event: stage        data: {"stage":"bm25","ms":84}
event: stage        data: {"stage":"vector","ms":126}
event: stage        data: {"stage":"fusion","ms":3}
event: citation     data: {"index":1,"doc":"xxx.pdf","pageFrom":12,"pageTo":13,"section":"3.2 去极值"}
event: delta        data: {"text":"因子值先做"}
event: number_check data: {"total":6,"missing":["0.35%"]}
event: done         data: {"queryId":"q_1","latencyMs":6120,"tokensIn":6800,"tokensOut":420}
event: error        data: {"code":"UPSTREAM_TIMEOUT","message":"...","retryable":true}
```

约定：`start` 必发；`stage` 可重复；`citation` 在对应正文之前发；`done` 与 `error` 互斥且必有一个。

## 11. 发布

| 阶段 | 内容 | 完成标志 | 相对时间 |
|---|---|---|---|
| 0 | 骨架、建表、`/health`、三页空壳 | 浏览器能开三页并读到 `/api/health` | 半天 |
| 1 | 解析、表格、块、切块、管线、事件，块树只读 JSON 页 | 10 份全部导入成功，换模板重跑能在 `pipeline_run` 里对比 | 四天 |
| 2 | 桩嵌入、双通道检索、无生成版问答、埋点 | 5 条链路验证问题能召回正确文档和页码，链路页能看三段得分（只验链路，不进入 KR 计分） | 一天半 |
| 3 | 流式生成、引用、数字自检、提示词快照 | 中英文各 3 条冒烟问答通过，数字全部可回查（不计入 KR2） | 一天半 |
| 4 | 四个页面、PDF 跳页高亮、反馈入集 | 点引用跳到正确页码并高亮 | 两天 |
| 5 | 真嵌入、调参、519 份全量导入，再补到 5000 份 | KR2 到 KR4 全部达成 | 机器时间一到两天加调参两天 |

第一期是阶段 0 到 4，只碰 10 份文件，约九个半工作日。

5000 份全量导入分两批：先导 519 份验证，再后台补齐；夜间跑，不占用白天。

之后：接真重排、OCR、图表理解、代码文件索引、章节摘要树，`artifact` 表已经留好位置。

明确不做：多用户和权限，OCR，图片与图表理解，可视化编排画布和通用 DSL，多向量库与多模型矩阵，服务拆分，QuantsPlaybook 的 `.py` 索引。

## 12. 验收集

20 条，存在 `eval_cases`，字段见第 8 节。构成：

| 类型 | 条数 | 判分方式 |
|---|---|---|
| 单点核对 `pos` | 12 | 首个引用必须落在 expected_doc 与 expected 页码区间；答案里的关键参数必须出现在原文 |
| 跨文档比较 `cross` | 5 | 至少两家来源，且各自引用正确 |
| 负例 `neg` | 3 | 必须拒答；任何带数字的生成都算失败 |

前 6 条按语料里最常见的指标先写，其余在阶段 1 导完 10 份文档后按实际内容补：

| 序 | 类型 | 问题形态 | 期望 |
|---|---|---|---|
| 1 | pos | 某指标在原文里的构造步骤与参数 | 指向具体文档与页 |
| 2 | pos | 某指标的标准化窗口与去极值方法 | 指向具体文档与页 |
| 3 | pos | 某信号的触发条件与阈值 | 指向具体文档与页 |
| 4 | pos | 某公式里各符号的含义 | 指向具体文档与页 |
| 5 | cross | 两篇研报对同一指标的处理差异 | 两家来源并列 |
| 6 | neg | 语料中不存在的指标名 | 拒答 |

阶段 5 前必须补齐 20 条并逐条人工标注 expected_doc 与页码；标注人就是 YG，标注结果直接写进 `eval_cases`。

