# 决策笔记（write-notes-like-deepseek）

> 一句话：把「为什么这样改、放弃了什么、怎么证明改对了」写进仓库，让下一个接手的 Agent 先看见规矩，再动代码。
> 完整规则见 [.agents/skills/write-notes-like-deepseek/SKILL.md](.agents/skills/write-notes-like-deepseek/SKILL.md)。

## 1. 它解决什么问题

代码能说清「现在怎么跑」，说不清三件事：

- 当初为什么必须这样写——看起来别扭的那段，可能是在躲死锁或兼容某个约束；
- 哪些路已经试过并被否掉——不写下来，新会话会再提一遍；
- 哪些边界不能击穿——跨模块直连、绕过 manager、乱改接口契约。

所以规矩必须写在仓库里、和代码同一次提交、还能被脚本检查。**光靠提示词说「注意架构」没有用。**

## 2. 目录就是状态

```
.agents/notes/
├── proposed/       # 想清楚了但还没动手：背景、备选、验收标准
├── implemented/    # 已落地：只写现在时事实，随代码一起改
├── rejected/       # 被否掉：写清原因防重犯，没价值就删
└── archived/       # 已封存：只读，改了哈希会报警（manifest.json）
```

每条笔记的路径：`{状态}/{类别}/yyyy-mm-dd-主题.md`，类别只有 6 个：

| 类别 | 用在 |
|---|---|
| `feature` | 接口行为、产品语义这类看得见的新能力 |
| `bug-fix` | 修缺陷，或复盘补上的防护 |
| `simplification` | 只删不增：删代码、收窄暴露面、行为不变的重构 |
| `architecture` | 模块拆分、包依赖、跨模块调用方向 |
| `process` | pom、构建、校验、发布、协作流程 |
| `testing` | 测试分层、JaCoCo/PIT 口径、测试基建 |

没有总索引 `INDEX.md`：文件夹位置本身就是状态，多分支开发也不会抢同一个文件。

## 3. 什么时候必须写

**命中任意一项就写**：改了行为、架构、跨模块契约、工具链、测试策略、落盘/网络/配置格式。

**直接改代码，不要写**：

- 纯排版、错别字、无歧义重命名
- 样式调整、只改注释或日志文案
- 不改行为的依赖补丁、版本打标
- 单个模块内看 diff 就懂的常规 CRUD、DTO 字段增删

判不准时按一句话收口：**代码和单测说不出来的意图、边界、取舍，就是非平凡。**

## 4. 一篇笔记长什么样

```markdown
# Agent Note: service 只经 manager 访问持久层

Status: implemented

## Problem

service 直连 dao 后，缓存与重试逻辑在多处复制，改一次要全模块搜。

## Decision

service 只依赖 manager；dao 仅由 manager 调用。

## Alternatives considered

- **维持现状** — 少一层转发，调用链短；但缓存、重试、事务边界会继续各写一套。

## Consequences

- **收益**：持久化策略只有一处实现。
- **代价**：多一层转发，纯查询也要过一次 manager。
```

三件事别省：

1. **备选先写它最强的理由，再说为什么不用**——否则后人只会看到「弱稻草人」，然后翻案。
2. **代价和收益一起写**——没有代价的决定，通常是挑选过的叙述。
3. **`implemented` 用现在时**——只描述已经落地的事实，不写「本次新增」「后来改成」。

## 5. 常用命令

在仓库根目录执行，Node ≥ 24，**不需要 `npm install`**：

```sh
npm run verify-notes     # 写完笔记必跑：目录+链接、头块骨架、归档封印
npm run verify-note-tree # 只查目录、分类、文件名、笔记间相对链接
npm run verify-note-format # 只查头块、状态、必备节、备选方案
npm run verify-archived  # 只查归档封印（改一个字就红）
npm run archive-note -- .agents/notes/implemented/<类别>/<文件>.md
npm run init-board       # 可选：生成本地决策看板 board.html
```

`verify` 红了就修，不要靠改预期或删断言绕过。

## 6. 本仓库的三条定制

1. **笔记根目录固定 `.agents/notes/`**，不与 Java 源码混放。
2. **不写 `// Note:` 源码锚点**：`RULES.md` 限定注释只描述方法功能。检索用分类树 + `rg --hidden --glob '!.agents/notes/archived/**'`。
3. **不引入 tsx**：根 `package.json` 只用 Node 24 的类型剥离直接跑 `.ts`，零第三方依赖。

## 7. 和现有规则的关系

- `AGENTS.md`、`RULES.md` 与用户指令**高于**本制度。
- 本制度是默认契约：非平凡改动先写或更新笔记，机械改动直接改代码。
- 被新决策取代的旧笔记：部分重叠就互链；完全吸收后能删则删，否则归档并封存。
