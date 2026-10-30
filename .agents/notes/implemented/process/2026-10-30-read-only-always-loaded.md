# Agent Note: 只读协议改为常驻加载

Status: implemented

## Problem

`/read-only` 在此之前从未真正生效，原因有三层，每一层都能独立复现：

1. dsh 的斜杠命令由插件 `ctx.commands.register()` 注册，这套安装里只有 `compact`、`feedback`、`goal`、`plan`、`export`、`catalog`、`workspace-write`、`danger-full-access`。它没有文件系统命令加载器——只有 skill 有（`.dsh/skills`、`.agents/skills`、`~/.dsh/skills`、`~/.agents/skills`）。所以 `.agents/commands/read-only.md` 在 dsh 里是死文件。
2. 输入框的 `/` 菜单只是往草稿插一段文本（`{ text: '/' + name + ' ' }`），提交时找不到同名命令就 fall through 成普通消息发送。带参数的 `/read-only 分析 server/` 更是必然落空。
3. 原规则的触发条件是「本次对话出现过 `[READ-ONLY]` 标记」，而标记只写在那个死文件里。标记永不出现，规则永不激活；反过来，任何讨论这个标记的对话又满足字面条件，造成误触发。

另有一处自相矛盾：原 skill 规则 2 禁止一切写入，规则 4 又要求产出物落 `temp/`。

## Decision

read-mode 协议正文常驻 `AGENTS.md` 的「read-mode规则」节。每个会话加载项目时它随工作区指令进入上下文，不依赖斜杠命令注册，也不依赖模型主动加载 skill。

- 激活只看用户消息里的 `/read-mode`、`/只读`、「只读调查」；退出只看「退出只读」。删除 `[READ-ONLY]` 标记这个触发词，避免「提到即触发」。
- 边界写成表：允许 read / grep / glob / web 与只读 shell；禁止 edit / write / patch 与写入型 shell，含 `mvn clean test`、`npm install`、`git apply`。
- `temp/` 是唯一写入例外，解决原规则 2 与规则 4 的冲突，并要求在回复里说明写了什么。
- 越界必须自报「越界：<命令>」，让违反可见，而不是静默继续。
- 三级载体分工固定：`AGENTS.md` 承载协议正文（常驻），`.agents/skills/read-mode/SKILL.md` 承载判定细则与 subagent 交接模板（按需加载），`.agents/commands/read-mode.md` 只服务会读命令目录的 harness（OpenCode / Claude Code），并在文件末尾写明这一点。

## Alternatives considered

### Why not 用 SessionStart hook 注入

`dsh-hooks-claude-code` 的 `SessionStart` 能在首个轮次前把上下文注入会话，是「任何 session 都加载」的正解形态。没把它当第一步，是因为它挂在 profile 层（`~/.dsh/profiles/web/cordis.patch.yml`），改完要重启 dsh，且把仓库行为绑死在一台机器的 harness 配置上；`AGENTS.md` 在同一台机器上零配置，换 harness 也成立。等需要硬门禁时再上 hook：那时 hook 负责执行，`AGENTS.md` 负责表达。

### Why not 写 dsh 原生插件注册 /read-only

照抄 `dsh-plan-mode` 做一个真模式（`ctx.commands.register` 开模式 + `tools/pre-execute` 拒绝写工具 + 每轮注入 reminder）能力最全，也是唯一能给出机器可读状态位的做法。代价是它属于 dsh 插件开发，不在本仓库交付物范围，且与仓库外的 harness 版本耦合。协议文本先行、插件后补，两者不冲突。

### Why not 保留 [READ-ONLY] 标记做兼容

标记的语义是「上下文出现过即生效」，与「用户消息里出现激活词才生效」互斥。同时保留会让讨论型对话继续误触发，等于把已经定位到的 bug 留在原地。

## Consequences

收益：协议在每个会话都在，模型不必先判断要不要加载 skill；触发词唯一，漏触发与误触发同时消失；`temp/` 例外让只读调查在实际上可执行——否则连解压一份会话日志都做不到。

代价与边界：

- 仍是提示词约定，没有工具级门禁。本轮实测中，标记已在上下文里、skill 正文也已加载，主 Agent 仍写了 6 次盘。同类越界依然可能发生，只是现在必须自报。
- `AGENTS.md` 变长约 1.5KB，每个会话都要付这份 token，换来的是不依赖模型判断「要不要加载」。
- 激活状态只存在于消息文本里，会话压缩后需要按协议重建；没有机器可读的状态位。
- 硬门禁（`PreToolUse` hook 或原生插件）仍是未完成项，见 Alternatives 的两条路线。

## Verification

- `npm run verify-notes` 通过。
- skill 目录热更新已验证：改完 `SKILL.md` 的 description 后，本会话的 skill 目录立即刷新成新描述，说明 `dsh-skill-filesystem` 的 watch 生效。
- 新会话是否真的带上协议，需要在下次开会话时看首轮回复有没有引用「read-mode规则」节；这是唯一无法在本会话内完成的验收。
