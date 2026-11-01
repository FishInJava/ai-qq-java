# Agent Note: 移植 Vercel 的 React 技能，只带 Web 三件套

Status: implemented

## Problem

上游 `G:\Grinding Gear Games\agent-skills-main\skills` 是 Vercel 的 agent-skills 仓库，9 个技能目录里只有 4 个和 React 有关：

| 上游目录 | 大小 | 内容 | 与 React 的关系 |
|---|---|---|---|
| `react-best-practices` | 230 KB / 76 文件 | 70 条规则，8 类，含合并稿 `AGENTS.md` | 直接相关 |
| `composition-patterns` | 50 KB / 14 文件 | 8 条组合模式规则 | 直接相关 |
| `react-view-transitions` | 122 KB / 9 文件 | `SKILL.md` + 5 个 `references/` | 直接相关 |
| `react-native-skills` | 159 KB / 42 文件 | Expo / Reanimated / 原生列表性能 | React Native，非 Web |

剩下的 `deploy-to-vercel`、`vercel-cli-with-tokens`、`vercel-optimize` 是 Vercel 平台的部署与账单优化，`web-design-guidelines`、`writing-guidelines` 只是让 Agent 现场 `WebFetch` 远程 `command.md` 再逐条比对，都不是 React 写法知识。

本仓库 `website/` 是 Vue 3 + Vite（`website/package.json` 的 `vue`、`vue-tsc`、`vite`），`RULES.md` 的「技术栈 / 前端」一栏是空的。也就是说这批规则眼下没有执行对象，只是知识储备。同时原技能大量规则以 Next.js App Router 为前提，照抄进 Vue 仓库会误导。

## Decision

移植三件套到 `.agents/skills/`，`react-native-skills` 与另外五个不搬。

| 上游 | 本地 | 文件 | 行数 |
|---|---|---|---|
| `react-best-practices` | `.agents/skills/react-best-practices/` | 74（`rules/` 72） | 3297 |
| `composition-patterns` | `.agents/skills/react-composition-patterns/` | 12（`rules/` 10） | 851 |
| `react-view-transitions` | `.agents/skills/react-view-transitions/` | 7（`references/` 5） | 1009 |

六个落点：

- `rules/` 与 `references/` 逐字节复制，未改一字，`Get-FileHash` 对 90 个文件全部一致。改的只有三个 `SKILL.md`。
- `name` 改成目录名。上游是 `vercel-react-best-practices`、`vercel-composition-patterns`、`vercel-react-view-transitions`，与目录名不一致会吃 `verify-skills` 的 `name-mismatch` warning，而且技能列表里三个名字各带一次 `vercel-` 前缀，读起来是六个不相干的词。
- `composition-patterns` 目录改名 `react-composition-patterns`，与另两个统一 `react-` 前缀。
- 上游 `composition-patterns/SKILL.md` 的 `description` 是跨 6 行的 YAML 折叠块。`verify-skills` 用 `/^description:\s*(.+)$/m` 取描述，`description:` 后面没有内容的行取不到值，会报 `missing-description`。三份 `description` 因此都写成单行中文，顺带把触发词换成仓库常用的中文说法（如「重渲染」「打包体积」「复合组件」）。
- 每个 `SKILL.md` 加一节「适用范围」，写明规则只对 React 成立、`website/` 现在是 Vue 3。不写这一句，Agent 看到「React 最佳实践」会往 `website/src/` 上套。
- 删掉每个目录里的 `AGENTS.md` 与 `README.md`。前者是上游用 pnpm 从 `rules/` 编出来的合并稿，内容与 `rules/` 重复（`react-best-practices` 那份 108 KB），且 `AGENTS.md` 是多数 harness 的仓库级自动读入文件名，埋在技能目录里可能被整份塞进上下文。后者是上游 `pnpm build` / `pnpm validate` 的贡献者说明，绑定 pnpm 与上游构建脚本，进不来。

`metadata.json` 保留（三份合计约 2.6 KB），补了 `source: vercel-labs/agent-skills` 指回上游。

## Alternatives considered

### 全量移植 9 个

保真度最高。否掉的原因：`react-native-skills` 42 个文件全讲 Expo Router、`reanimated`、原生列表虚拟化，本仓库没有 RN 工程，留着只会在「React 性能」这类任务上抢触发；`vercel-optimize` 带 15 个 `scripts/*.mjs` 加 30 多个 `lib/` 模块，要 `VERCEL_TOKEN` 和线上项目才能出结论；两个 guidelines 技能正文只有 1.2 KB，实质内容在远程地址里，搬过来等于搬一个 `WebFetch` 调用。

### 保留每份 `AGENTS.md` 合并稿

好处是「一份文件读完 70 条」。否掉的原因：内容与 `rules/` 是同一批文本的两份拷贝，上游用脚本生成、本地没有生成脚本，两边一旦漂移没人能发现；而 108 KB 的 `AGENTS.md` 被某类 harness 当仓库级约定自动读入时，会白占一大块上下文，正好抵消技能「按需加载」的意义。

### 保留上游的 `vercel-` 前缀

跟上游一一对应，将来同步时好认。否掉的原因：`verify-skills` 会对三个目录各报一条 `name-mismatch`，技能列表里呈现的也是 `vercel-*` 名字，与仓库其余十个技能（`how`、`bug-fix`、`feature`）命名风格对不上。

### 翻译正文

仓库协作语言是中文，`RULES.md` 和其余技能正文都是中文。否掉的原因：正文 5157 行，绝大部分是 TypeScript/TSX 代码块与 API 名称，翻译只动叙述部分，收益是读起来顺一点，代价是 90 个文件全部偏离上游、以后无法逐字节对齐升级，而且代码注释按 `RULES.md` 还得再压成每行 10 字以内，等于把上游示例改残。

## Consequences

收益：写 React 时有 70 + 8 条带反例的规则可查，`<ViewTransition>` 这一块连 CSS 配方和排障路径都是现成的；`verify-skills` 只在装载时读 `SKILL.md`，`rules/` 与 `references/` 按需读，平时不占上下文。

代价：

- 三个技能的实际规则没有在本仓库执行过。`website/` 是 Vue，没有 React 代码可供验证，所以「规则挑得对」「示例能跑」都未证明。
- `react-best-practices` 的 `server-*` 一节、`bundle-dynamic-imports`、`async-api-routes` 以及 `react-view-transitions/references/nextjs.md` 都以 Next.js App Router 为前提，Vite + React 下这些条目要么无落点，要么写法不同。照用前得先判定项目是不是 Next.js。
- 上游升级时要手工同步：没有生成脚本，`rules/` 与上游是两份独立副本，得靠 `Get-FileHash` 对比找出差异。
- 加了三节「适用范围」，写死了「`website/` 现在是 Vue 3」这个事实。哪天前端换成 React，这三句话就过期了，改前端技术栈时要一并改。

## Verification

- `npm run verify-skills`：`checked: 16 skill(s), 66 path reference(s), 0 error(s), 1 warning(s)`。唯一 warning 是既有的 `0test-skill` 名字不一致，与本次无关。
- `npm run verify-notes`：三个脚本全过（树与相对链接、格式、归档）。
- 内容保真：90 个非 `SKILL.md` 文件逐个 `Get-FileHash` 与上游比对，0 处差异。
- 装载验证：写入后 harness 的技能列表当场出现 `react-best-practices`、`react-composition-patterns`、`react-view-transitions` 三条，且第二次改 `description` 后返回的是新描述，说明 frontmatter 能被解析。
- 未做：没有拿真实 React 任务跑过这三个技能，不知道 Agent 会不会在 Vue 代码上误触发。
