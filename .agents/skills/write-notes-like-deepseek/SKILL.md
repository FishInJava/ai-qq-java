---
name: write-notes-like-deepseek
description: 非平凡改动（行为、架构、跨模块契约、工具链、测试策略、落盘/网络/配置格式）前先写或更新决策笔记时使用；技术选型、推翻既有决定、事故复盘同样适用。笔记落在 .agents/notes/，用 npm run verify-notes 机械校验；纯排版、样式、注释、不改行为的依赖补丁、常规 CRUD 不写。
---

# Write Notes Like DeepSeek（ai-qq-java 版）

> 方法提炼自 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) 的工程实践。聊天里的 Agent 负责拆任务、排计划；本 Skill 只做一件事：为什么这样改、放弃了什么、怎么证明改对了，都留在一处，给下一个改这段代码的人用。
> 本副本已按 ai-qq-java 改造：命令换成 npm scripts，分类示例换成 Java / Maven 语境，并按 `RULES.md` 关掉了源码锚点。

## 本仓库落地事实（动手前先对一遍）

| 项 | 值 |
|---|---|
| 笔记根目录 | `.agents/notes/`（脚本按当前工作目录解析，必须在仓库根执行命令） |
| Skill 安装位置 | `.agents/skills/write-notes-like-deepseek/` |
| 运行命令 | 仓库根 `npm run verify-notes`；根 `package.json` 无第三方依赖，Node 24 用类型剥离直接跑 `.ts` |
| 工程形态 | Spring Boot 4.1.2 / Java 21 / Maven 多模块，全部模块在 `server/` 下（`common`、`entity`、`dao`、`manager`、`interface`、`service`、`controller`、`mq`、`quartz`、`rpc`、`web`） |
| 版本库 | git，`.gitattributes` 统一 LF；代码和笔记**同一次提交** |
| 注释纪律 | `RULES.md` 限定注释只写方法功能（≤3 行、每行 ≤10 字）→ **本仓库不写 `// Note:` 源码锚点**，检索只靠目录切片与 ripgrep |
| 优先级 | `AGENTS.md` / `RULES.md` 与用户直接指令**高于本 Skill**；本 Skill 是默认契约，不是更高法律 |

## 红线：先判要不要写，再谈怎么写

模型有迎合强迫症，动不动就想立 Note。先过这道闸：

**快速返回——以下属于"纯机械或局部改动"，严禁立 Note，直接改代码：**

- 纯排版格式化、错别字、无歧义重命名
- 样式调整（不改行为）
- 只补/改代码注释、日志文案（不改行为）
- 依赖补丁（不改行为）、版本发布打标（RC/Release tag）
- 单个 Maven 模块内部看 diff 即懂的显式逻辑修复（无跨模块影响）
- 常规 CRUD、`@Data` DTO 字段增删、pom 版本号跟随升级

**判定对齐 DSH：非平凡改动必须写。** 命中以下任何一项即非平凡——改了**行为**、改了**架构**、改了**跨模块契约**、改了**流程与工具链**、改了**测试策略**、改了**落盘 / 网络 / 配置格式**——或其他维护者日后可能重访的决定。

本仓库语境下的典型命中项：

- 跨模块调用方向或依赖关系变化（例如 `service` 绕过 `manager` 直连 `dao`、新增 `common` 对业务模块的反向依赖）
- HTTP 接口契约变化（路径、请求/响应字段、错误码）、RPC/MQ 消息体或 topic 变化
- 线程模型、连接池、缓存、事务边界、超时与重试策略
- `application*.yml` 的 key 语义、数据源/中间件接入方式、启动流程
- 构建与测试策略：pom 插件、模块拆分、JaCoCo/PIT 口径、测试分层

**写之前，对照三个方向想清楚这篇笔记守住什么：**

1. **往前看（立新规）**——新建的跨模块通信契约、状态流转规则、访问边界、运行时不变量。不记，后来的 Agent 各写一套、随意击穿模块。
2. **往回看（记妥协）**——为看不见的约束放弃了业界主流或直觉的解法。不记，被否掉的老路会被重走一遍。
3. **做减法（收窄）**——破坏性重构、代码裁剪、API 暴露面收窄。不记，没人知道退出条件和迁移边界，减法做不下去——或者做过了头。

一句话记住：**代码和单测说不出来的意图、边界、取舍，就是非平凡。** 判不准时，从严。

> 对 AI 来说，写在散文里的规矩等于没有规矩——能机械检查的纪律都有脚本兜底（见 §6）；决定翻转、当场审计、现在时正文靠操作流，不靠词法扫描。

| 内心的借口 | 现实 |
|---|---|
| "只是改个默认值/重命名" | 默认值和命名都是决策事实；原地更新老笔记只要 30 秒 |
| "先合并，以后再补" | 以后 = 永远不会；腐化从每一篇"以后补"开始 |
| "代码即文档" | 代码只说**是什么**，说不出**为什么**和**放弃了什么** |
| "改动很小，犯不着" | 规模小 ≠ 不用记；一个重试参数曾引发全网故障 |
| "不确定要不要写" | 对照上面的非平凡清单：命中就写，不命中就不写。别拿这句话当挡箭牌 |

- **优先就地同步，非必要不新建**：Note 的变更大头是原地修改现有 Note 的事实（路径、类名、默认参数），而不是开新文件。已有归属的改动直接更新那篇！
- **禁止把一篇 Note 改成另一个决定**：事实（路径、符号、默认值）就地改；决定或理由翻转 → 新开一篇并互链。禁止把 `## Decision` 改写成反面，禁止只靠 git 当旧理由的唯一副本。
- **写新篇当场审计，不准推迟**：按模块名/关键词搜 proposed + implemented + rejected；每篇命中当场分类：无关 / 部分重叠（互链）/ 完全吸收（删或归档）/ 过时提案（reject 或删）。分类结果和这篇新 Note 一起落盘。
- **垃圾不进归档**：过时提案转 `Status: rejected — <原因>`（严禁归档）；无防坑价值的被否记录直接物理删除。

> 「决定」不重——个人项目里就是「为什么选 A 没选 B」。选型、取舍、踩过的坑，都值得写下。

## 0. 先探测，再落地

别一上来建全套目录。按这个仓库现在的样子选：

1. 仓库已有 `AGENTS.md` 与 `RULES.md` → 先读，冲突时它们优先。
2. 笔记树固定为 `.agents/notes/{proposed,implemented,rejected,archived}/{feature,bug-fix,simplification,architecture,process,testing}/`；**用到哪个目录就建哪个**，空目录不必预建。`archived/manifest.json` 由归档脚本自动生成。
3. 仓库暂无 `.github/workflows/`：CI 模板在 `templates/verify-notes.yml`，接入前先确认是否真的要给 Java 仓库加 Node 步骤；不接 CI 时靠提交前手动 `npm run verify-notes`。

## 1. 路径即分类

每条 Note 的路径就是身份：`{lifecycle}/{class}/yyyy-mm-dd-topic.md`

**Lifecycle（一层文件夹，这篇走到哪一步）：**

- `proposed` — 想法阶段，有了方案但还没落地
- `implemented` — 已落地，与代码同批改动保持同步
- `rejected` — 审慎否掉的提案，仅当能防止重犯时保留，否则删整组
- `archived` — 已完成且未来参考价值低的 implemented 记录，冻结不可改

**Class（二层文件夹，就这 6 个；再加要改检查脚本）：**

- `feature` 新能力 — 用户或模型看得见的选择（接口行为、产品语义）
- `bug-fix` 修缺陷 — 修好了什么，或补上复盘里暴露的缺口
- `simplification` 只删不增 — 不增加能力，只删代码、行为或表面
- `architecture` 结构怎么搭 — 模块边界、包依赖、跨模块调用方向
- `process` 工具和流程 — 构建、校验、发布、怎么协作（围着代码转，不是运行时行为）
- `testing` 测试怎么写 — 测试策略和基建

Java / Maven 语境下的归属速查：

| 改动 | 归到 |
|---|---|
| 新增/修改 HTTP、RPC、MQ 对外契约 | `feature`（契约形态另有结构性影响时可拆 `architecture`） |
| `service`/`manager`/`dao` 调用方向调整、模块拆分、循环依赖治理 | `architecture` |
| pom 插件、模块聚合、构建顺序、依赖版本策略、代码生成 | `process` |
| JUnit/JaCoCo/PIT/Testcontainers 口径、测试分层与命名 | `testing` |
| 删掉一层无用的转发、去掉重复 DTO 映射 | `simplification` |
| 修并发/事务/空指针缺陷，或复盘补上的防护 | `bug-fix` |

> `refactor` 不单列：能看见的行为变了，归到对应类；没变就是 `simplification`。不建 `INDEX.md`。细判据见 `references/classification.md`。

## 2. 文件格式（检查脚本会核对）

正文一律用简体中文撰写（代码、类名、命令、配置 key、专有名词除外），与触发任务的输入语言无关。

前三行固定：

```markdown
# Agent Note: <标题>

Status: <状态>
```

状态必须与所在 lifecycle 文件夹一致（`rejected` 带一句话原因）；文件名日期是**首次提出日**。Body 骨架：

- `proposed`：`## Problem` → `## Proposal` → …自由节… → `## Alternatives considered` → `## Acceptance criteria` → `## Risks`
- `implemented`：`## Problem` → `## Decision`（现在时） → …自由节… → `## Alternatives considered` → `## Consequences`
- `rejected`：冻结的 proposal 形态，结论在 `Status:` 行

> 备选方案必填：只记录真实考虑过的对手方案，先写它最强的理由再否定。没有过的选项不要编。「不做 / 复用现状」仅当当时真的权衡过才写。脚本只检查有没有 `## Alternatives considered`。确实没有真实备选时，保留节标题、正文写「无」即可过检，不要为凑数编造。
> `implemented` 的 `## Decision` 用现在时；门禁只拒提案标题（`## Proposal` / `## Plan` / `## Migration plan` / `## Acceptance criteria` 及其中文别名）。展开见 `references/note-format.md`。

本仓库常用的自由节（放在 `Decision` 与 `Alternatives` 之间，标题保持可检索）：

- `## 模块与调用方向` — 谁调谁、允许与禁止的依赖边
- `## 接口契约` — 路径、请求/响应字段、错误码、兼容窗口
- `## 配置与运行时` — `application*.yml` 的 key、线程池/连接池/超时
- `## 数据与迁移` — 表结构、索引、回滚方式
- `## Testing` / `## Verification` — 验证落到哪条命令

模板见 `templates/`。

## 3. 动手前检索历史决策（去中心化 3 法）

动手重构或选型前，先查历史约束，防止重复踩坑或破坏前人妥协：

1. **分类树物理切片**：不扫全库，按意图直切目录（架构看 `implemented/architecture/`，避坑看 `rejected/`，测试口径看 `implemented/testing/`）。
2. **精准全局检索**：用 ripgrep 搜关键词或机制名，**必带 `--hidden` 并排除 `archived/`**：
   ```powershell
   rg --hidden --glob '!.agents/notes/archived/**' "<机制名或关键词>" .agents/notes/
   ```
3. **模块文档下钻**：子模块 README 或笔记里的相对 Markdown 链接，直达对应 Note。

> 本仓库不写 `// Note:` 源码锚点：`RULES.md` 限定注释只描述方法功能。检索不要指望代码里的路标。

**问用户之前，先自己查。** 以上三法能答的事实，不要抛给用户；只有真正的决策才占用用户时间。

## 4. 什么时候写、什么时候改

**对话里的触发信号**——用户或自己说出这类话，就该动笔（文首红线清单里的机械改动除外，别拿这些短语当过度记录的理由）：

- 拍板新路线："就选 X"、"决定用 X"、"我们先用 X 顶着" → 触发 Note
- 比较中："X 和 Y 怎么选"、"为什么倾向 X" → 触发备选记录
- 同一段理由被解释了第二遍 → 该写下来了

判定与操作流：

- **既有架构重构 / 路径迁移 / 参数改动** → **【首选原地同步】**：直接在持有该决定的老 Note 里修正事实（类名、包路径、方法签名、默认值），不另起新篇，也不要在正文追加流水账历史。`## Decision` 核心理由不变；理由变了就走新建。
- **写新 Note 之前** → 按模块名/关键词搜活跃笔记，当场分类（见文首「当场审计」），禁止留到以后大扫除。
- **新想法、还没动手** → 先写 `proposed`（为什么想这么做、考虑过哪几条路），评审完再动手。交互纪律见下。
- **施工完（proposed → implemented）**，同一次改动里做完：① 移到 `implemented/<class>/`，文件名日期不动；② `Status: proposed` → `Status: implemented`；③ `## Proposal` → 现在时 `## Decision`；④ `## Acceptance criteria` / `## Risks` 折进 `## Consequences`（或现在时 `## Testing`）；⑤ 删计划段。与代码同批落盘——**git 场景即同一 commit**。
- **方案被新决策部分取代** → 两篇都留，双方加相对链接；只更新仍成立的事实。禁止归档。
- **方案被新决策完全取代** → 新 Note 接管并写入旧篇全部独特理由/备选/后果/验证缺口；入站链接改完后，能删则删，不能删（旧篇仍有独立杠杆）再 `npm run archive-note`。指针写在新笔记里，不写进归档篇。
- **免写场景**：见文首红线——快速返回清单里的，直接提交代码。

**交互协议（向用户提问时）：**

1. **先分 facts 和 decisions**：环境里查得到的事实（代码、笔记树、`rg`、`mvn` 输出）自己查完再问；只有真正要拍板的取舍才占用用户时间。
2. **一轮全抛**：所有待拍板的问题编号列出，每题独立一行给推荐答案 `➡️ <推荐>`；用户按编号批量应答（"1 yes，2 第二个选项"），不挤牙膏、不来回试探。
3. **收敛靠确认门，不靠题数上限**：落笔/动手前复述全部决定，用户确认达成共识后再执行——复述中被默认掉的任何一点，用户在确认时纠偏；没确认不动手。
4. **开放决策超过五个是信号，不是配额**：说明这次改动太大——拆成多篇笔记，或先交 proposed 草稿走评审，别在一次对话里硬塞。

> 判定细则见 `references/when-to-write.md`，归档与删除见 `references/archiving.md`。

## 5. 怎么写好

- `## Consequences` 同时写**代价和收益**，不是只写"放弃了什么"。
- 自由节（模块拓扑、接口契约、配置 key、线程模型）放在 `Decision` 与 `Alternatives` 之间，保持可检索的机制名与 `must / may / never` 时序强调。
- 跨 Note 引用用相对 Markdown 链接 `[topic](../../implemented/architecture/2026-…-….md)`，不要裸数字，以便机械可校验。
- **不写源码锚点**：本仓库的注释纪律只允许描述方法功能；决定被取代时，需要同步的是笔记之间的链接，不是代码注释。
- 文风与去推导痕迹见 `references/prose-checklist.md`；简化机会见 `references/simplification-checklist.md`。
- 写完后过一遍 `references/quality-gate.md` 的语义自检，只向用户报**缺口和写得好的地方**（≤5 行），缺口给具体修法。结构靠脚本，意思靠人点头。

## 6. 校验与运维命令

全部在**仓库根目录**执行（`package.json` 已配好，无需 `npm install`）：

```sh
npm run verify-notes             # 三线串跑：目录/链接 + 头块骨架 + 归档封印（写完笔记必跑）
npm run verify-note-tree         # 目录合法性、分类、文件名、笔记间相对链接
npm run verify-note-format       # 头块、状态、必备节、备选方案、implemented 禁用提案标题
npm run verify-archived          # 归档封印：头部布局、manifest 哈希、只增不改（无 git 自动降级）
npm run verify-archived-write    # 为历史归档补封印（先验旧封印再追加，绝不重写历史）
npm run archive-note -- <path> [--superseded-by <新笔记>]  # 一键归档；可选在新笔记插入互链 + 入站死链报告
npm run check-anchors            # 软报告；本仓库默认没有源码锚点，一般不用跑
npm run init-board               # 生成 ~69KB 轻量看板 board.html（直读本地目录，热刷新）
npm run bundle-board             # 打包内嵌数据的自包含 demo.html
```

说明：

- 脚本位于本 Skill 的 `scripts/`，只从宿主仓库根调用，默认检查当前目录下的 `.agents/notes/`；可用环境变量 `AGENT_NOTE_ROOT` 指向别处。
- 运行时依赖 Node ≥ 24（类型剥离直接执行 `.ts`），不引入 tsx，也不产生 `node_modules`。
- 写完或改完任何笔记后必须运行 `npm run verify-notes`；不得以「未配置校验入口」为由跳过。
- 需要指定归档基线时设 `AGENT_NOTE_ARCHIVE_BASE_REF`（默认 `HEAD`）。
- 团队若接入 CI：把 `templates/verify-notes.yml` 复制到 `.github/workflows/`，并把命令改为 `npm run verify-notes`。

## References

按需加载：

- `references/note-format.md` — 头块与 body 骨架展开
- `references/classification.md` — 6 class 判定与边界（含 Java/Maven 示例）
- `references/when-to-write.md` — 何时新建 / 更新 / 流转（含与 DSH 判定对齐关系的说明）
- `references/archiving.md` — 归档与合并删除（含"未来参考价值"判定）
- `references/prose-checklist.md` — 行文与去泄露自检
- `references/simplification-checklist.md` — 简化机会自检
- `references/quality-gate.md` — 写后语义自检：Problem / Alternatives / Consequences / Verification 判定 + 汇报形态
- `references/verification.md` — 校验脚本说明与本仓库运行方式
