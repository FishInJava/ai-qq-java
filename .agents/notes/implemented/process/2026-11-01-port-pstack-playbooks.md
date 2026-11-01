# Agent Note: 移植 pstack 的 Feature、Bug fix、Refactoring、Prototype 四个 playbook，只取流程层

Status: implemented

## Problem

上游四个流程正文合计 66 行（Feature 21、Bug fix 15、Refactoring 16、Prototype 14，按 `(Get-Content).Count` 量）。它们点名的支线在本仓库大多不存在，全量移植要连带搬运 511 行文档。

各支线按同一量法：

| 支线 | 行数 | 本仓库 |
|---|---|---|
| `architect/SKILL.md` 及其 `references/` | 79 + 112 | 无 |
| `arena/SKILL.md` | 48 | 无 |
| `interrogate/SKILL.md` 及 `in-your-own-words` | 31 + 7 | 无 |
| `opening-a-pr.md` 及其传递依赖 | 34 + 158 | 无 |
| `tdd/SKILL.md` | 42 | 无 |

这些文档还各自绑定本仓库不能接受的东西：默认模型 slug `grok-4.7-xhigh-fast`、Cursor 的 `~/.cursor/rules/pstack-models.mdc`、`generalPurpose` 与 `poteto-agent` 这类特定 harness 的子代理类型、`gh` 与 Origin 三套 forge 分支、`cursor-team-kit` 里的 `deslop` 插件、Cursor 的 `/loop` 命令。`RULES.md` 第 6 到 7 行要求这类文档跨 harness 通用，逐条改写的工作量比正文本身大一个量级。

注释也有冲突。上游 `Comments` 节要求「只留代码说不出的非显然 why」，`RULES.md` 第 13 到 15 行要求注释只描述方法功能、最多 3 行、每行不超过 10 字。两者不能同时满足，本仓库以 `RULES.md` 为准。

## Decision

只移植流程层，四个技能放在 `.agents/skills/` 下，依赖内联成规则，不新建 `architect`、`arena`、`interrogate`、`tdd` 四个支线技能。`work-work/SKILL.md` 的 playbook 表加四行。

| 上游 | 本地 | 行数 |
|---|---|---|
| `playbooks/feature.md` | `.agents/skills/feature/SKILL.md` | 34 |
| `playbooks/bug-fix.md` | `.agents/skills/bug-fix/SKILL.md` | 32 |
| `playbooks/refactoring.md` | `.agents/skills/refactoring/SKILL.md` | 36 |
| `playbooks/prototype.md` | `.agents/skills/prototype/SKILL.md` | 28 |

四条落点：

- 设计分离保留。四个技能都写明「能开子代理就派出去，开不了就自己拥有 diff」，评审分离留在手上。`playbook-agent` 的「子代理不能开子代理」限制因此不阻断流程，只降低并行度。
- 支线判据内联。`architect` 的「至少两个结构不同的候选」压成「先把类型、签名、调用方用法落成文字再动手」；`tdd` 42 行压成一句「bug 有便宜的本地测试路径时先写失败测试，否则说明原因」。
- 注释与验证换成本仓库口径。注释指向 `RULES.md`，验证指向 `mvn -f server/pom.xml clean test` 加实际调用路径，未跑通不算通过。Bug fix 的「必须先在真实界面上复现」沿用 `work-work/SKILL.md` 已有的硬规则。
- Prototype 的临时产物落在 `temp/prototype-<slug>/`，与 `server/` 源码分开。这条跟 `read-mode` 的「临时产出物一律放 `temp/`」一致，两处规则不打架。Prototype 是唯一把 Laziness Protocol 倒过来用的流程，正文第 5 行明写了这一点。

文件放 `.agents/skills/` 而不是 `.agents/skills/work-work/playbooks/`。`AGENTS.md` 把 `.agents/skills/` 定义为「每个子目录一个 SKILL.md」，放在 playbooks 下只有一个裸 `.md`，按名字发现不到，也不进技能目录。代价是与 `code.md`、`investigation.md` 的位置不一致，那两份将来迁移时一并处理。

## Alternatives considered

### 全量移植支线技能

保真度最高，`architect` 的 `design-red-flags.md`（八条设计红线，Java 下「可导入的内部实现」对应 `package-private`）确实有价值。否掉的原因：511 行里有 158 行是 PR 流程，而 `code-pb` 明确止于本地 commit，本仓库没有任何 PR 基线；剩下的模型分发段落还要逐条改写成 harness 中立版本，改完与上游已经不同源。

### 分三批移植，把 `arena` 作为共用底座

`architect`、`interrogate`、`refactoring` 都依赖 `arena` 的候选扇出，先落 `arena` 再包几层是干净的依赖顺序。否掉的原因：本仓库默认只有一个模型可用，多候选竞技退化成同一模型跑多遍，收益不足以支撑四个新技能目录和 `available_skills` 的增长。

### 不移植，继续用 `code-pb` 写一切

零改动。否掉的原因：`code-pb` 的验收只看测试通过。Feature 少了「先定数据形状」和「吞吐检查点」，Bug fix 少了「复现先于修复」的提交顺序，Refactoring 少了「先钉契约再动结构」，Prototype 则完全没有位置，因为它的产出本来就不该入库。这四道闸正是新流程相对普通写代码的增量。

## Consequences

收益：新增 134 行文档（四个 SKILL.md 共 130 行加表里四行）换到四条带检查点的流程；不引入任何绑定特定 harness 的措辞，`RULES.md` 的兼容性要求继续成立；上游 `how` 与 `why` 直接复用，没有第二份。

代价：

- 并行设计探索与对抗评审只以文字规则存在，没有可执行的候选扇出步骤。执行者按字面照做时，产出是纸面候选而不是真实实现。
- `design-red-flags.md` 的八条判据没有搬进来，设计评审时少一份对照清单。
- 四个流程与 `code-pb` 在「写代码 + 本地测试 + commit」这几步上重叠，差别只剩各自的检查点。
- 四个文件写成了技能而不是 playbook，`work-work` 的表同时指向两类路径，读表的人要先分辨。
- Prototype 的一次性产物会落在 `temp/`，需要确认它没有进 `.gitignore` 之外的地方，否则会被误提交。

## Verification

- `npm run verify-notes` 通过。
- 四个 SKILL.md 里引用的 `.agents/skills/...` 路径去重后 15 条，逐个 `Test-Path` 全部存在，缺失 0 条。
- 未完成的验收：还没有拿真实任务跑过这四个流程，因此「检查点是否真出现」「报告格式是否被遵守」都未验证。按 `principle-prove-it-works` 这属于未证明，不当作通过。
