# Agent Note: playbook-agent 注册到 harness 可发现的路径

Status: implemented

## Problem

`.agents/agents/playbook-agent.md` 是 work-work 的子代理定义，但父代理按名字派活时解析不到它。原因是 omp 的 task 代理发现只扫 `.omp/agents/*.md`（项目级）与 `~/.omp/agent/agents/*.md`（用户级），并且显式跳过 `.claude/agents`、`.codex/agents`、`.agents/agents` 这类跨 harness 目录。`.agents/main.yaml` 里的 `layers.agents: true` 没有任何代码消费，全仓库 grep 只命中文档自身。

同时 `playbook-agent` 的定义没有禁止递归开子代理，`work-work` 的规则 2 只约束了父对话。

## Decision

- 注册文件放 `.omp/agents/playbook-agent.md`，只承载 frontmatter 与一句指向，正文规则仍留在 `.agents/agents/playbook-agent.md`。`AGENTS.md` 声明 `.agents/agents/` 是「命名子代理定义」且该文件只读，所以定义正文不搬走，注册层做成薄壳。
- 注册文件的 frontmatter 写 `spawns: ""`。omp 把空 spawns 解析成「禁止派生」，子代理即使工具列表里还有 `task`，preflight 也会拒绝。
- `.agents/agents/playbook-agent.md` 增加规则 4：不得调用 `task`、`eval` 的 `agent()`/`workpool()`；需要并行时把拆分需求报回父代理。
- `work-work/SKILL.md` 的 subAgent 节补第 5 条，写明注册文件位置与 `agent` 填 `playbook-agent`；同节工作流路径的反斜杠改成 `/`（`RULES.md` 要求 harness 相关设计跨平台通用）。

## 注册契约

父代理派活时 `agent` 填 `playbook-agent`，`context` 里给 playbook 绝对路径（或让它按 `.agents/skills/work-work` 自搜）。

## Alternatives considered

### 把定义正文搬到 `.omp/agents/playbook-agent.md`

一步到位，只留一份文件。否掉的原因：`AGENTS.md` 把 `.agents/agents/` 定义为子代理定义目录且不可改，搬走后文档与事实不一致，而这份文档是每个会话常驻加载的。

### 在 `~/.omp/agent/agents/` 放用户级注册

能立刻生效且不碰仓库。否掉的原因：绑定单台机器，`RULES.md` 要求 harness 相关设计不依赖某台机器或某个 Agent 的私有能力。

### 只靠 skill 文本约束递归，不设 `spawns: ""`

少一个 frontmatter 字段。否掉的原因：提示词约定没有门禁，实测中同类越界仍会发生；`spawns` 是 harness 层拒绝，成本为零。

## Consequences

收益：按名字派 `playbook-agent` 可用；递归在 harness 层被拒，错误信息是 `Cannot spawn 'sonic'. Allowed: none (spawns disabled for this agent)`。

代价：

- 定义与注册两处文件，改规则时只改 `.agents/agents/playbook-agent.md`，注册文件的正文已写明不要复制规则。
- 注册只在 omp 生效。其他 harness 若不读 `.omp/agents/`，仍要按 `.agents/agents/` 的路径手工塞进 context。

## Verification

- 实际派活 `agent: playbook-agent`：spawn 成功，子代理执行 `test-pb` 的 Step1，输出 `[hbz你好，我是0test-skill]`，并回报读到的角色定义路径 `.agents/agents/playbook-agent.md`。
- 让该子代理尝试 `task(agent=sonic)`：preflight 拒绝，原文 `Cannot spawn 'sonic'. Allowed: none (spawns disabled for this agent)`。
- `npm run verify-notes` 通过。
