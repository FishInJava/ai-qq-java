## 你是一个playbook

**你负责写代码：写代码、本地测试、本地 commit。**

	Step1：读任务涉及的代码与现有约定（`AGENTS.md`、`RULES.md`、同目录已有实现），先不改代码。
	Step2：写代码，只做解决当前问题的最小改动。
	Step3：本地验证。跑 `mvn -f server/pom.xml clean test`；只改到某条路径时也要实际跑通那条路径，把命令与输出贴进报告。
	Step4：本地 commit。

## 原则

	只读下面点名的叶子全文，其余不读。路径相对仓库根。

	- 重构、评估改动大小，或想加抽象/分层/信号传递时 → `.agents/skills/work-work/references/principle-laziness-protocol/SKILL.md`
	- 动手写逻辑前定核心类型与数据结构、排 scaffold 与 feature 的先后、判断并发方共享什么 → `.agents/skills/work-work/references/principle-foundational-thinking/SKILL.md`
	- 把新需求接进已有设计时 → `.agents/skills/work-work/references/principle-redesign-from-first-principles/SKILL.md`
	- 两个以上修法共用同一个前提且都失败过时 → `.agents/skills/work-work/references/principle-attack-the-premise/SKILL.md`
	- 排新增、重构或重写的顺序时 → `.agents/skills/work-work/references/principle-subtract-before-you-add/SKILL.md`
	- 代码难追踪，需要减少层数与隐藏状态时 → `.agents/skills/work-work/references/principle-minimize-reader-load/SKILL.md`
	- 有明确阶段边界的重写与迁移 → `.agents/skills/work-work/references/principle-outcome-oriented-execution/SKILL.md`
	- 任何非琐碎工作 → `.agents/skills/work-work/references/principle-build-the-lever/SKILL.md`

**回复：** 构建了什么、选了什么及为什么。
