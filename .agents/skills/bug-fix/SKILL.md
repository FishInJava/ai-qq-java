---
name: bug-fix
description: 缺陷修复流程：先在真实界面上复现，再二分查因，只上线有运行时证据支持的最小修复。work-work 的 bug-fix-pb 就是它。
---

## 你是一个playbook

**这个任务归你负责。做计划、评审、验证。** 调查和修复可以委派，主导权留在你手上。

	要讲科学。每一行上线的代码都能追溯到运行时证据。「也许有用」的双保险只是假设，不是修复，不能上线。证据推翻假设时，把它促成的改动回滚。只上线证据支持的最小改动。

	Step1：亲自在匹配的界面上复现。这是硬规则，不看调试工具怎么说。控制面确实够不到目标时才问用户，并且要先说清具体理由、先把界面驱动到极限。直接复现不了就合成触发器、收紧条件，或加插桩直到它触发。
	Step2：二分查因。先列候选假设，逐个排除到只剩一个。用受影响子系统的 `how` 打底，回归问题再叠加 `why` 查历史。每一轮选能切掉最多剩余空间的分割，拿运行时证据，排除。程序状态不清楚时加日志，在代码跑起来时读。不要猜。
	Step3：规划修复。跨函数边界时，先把类型、签名和调用方用法落成文字再动手。能开子代理就把实现派出去，评审分离留在自己手上；开不了就自己拥有 diff。
	Step4：在同一界面上验证。原始复现现在必须通过。「不确定」或跑错界面都不算通过，写进报告。单元测试只说明分支行为，不能说明 bug 不存在。
	Step5：安排提交顺序，让失败的复现在历史里先于修复落地。bug 有便宜的本地测试路径时先写失败测试；测试昂贵、偏集成或不清晰时跳过，并在报告里写明原因。跑 `mvn -f server/pom.xml clean test`。
	Step6：拆成小而有序的 commit。注释按 `RULES.md`，只写方法功能。

## 原则

	只读下面点名的全文，其余不读。路径相对仓库根。

	- 不知道受影响的子系统怎么运作 → `.agents/skills/how/SKILL.md`
	- 需要回归历史、当初为什么这么写 → `.agents/skills/why/SKILL.md`
	- 调试时追每个症状到系统第一次走错的地方 → `.agents/skills/work-work/references/principle-fix-root-causes/SKILL.md`
	- 两个以上修法共用同一个前提且都失败过 → `.agents/skills/work-work/references/principle-attack-the-premise/SKILL.md`
	- 声明修好之前 → `.agents/skills/work-work/references/principle-prove-it-works/SKILL.md`
	- 多步推进，失败复现要先于修复落地 → `.agents/skills/work-work/references/principle-sequence-verifiable-units/SKILL.md`
	- 想加防护、兜底或双保险时 → `.agents/skills/work-work/references/principle-laziness-protocol/SKILL.md`
	- 拿耗时、覆盖率这类数字下结论 → `.agents/skills/work-work/references/principle-explain-the-number/SKILL.md`

**回复：** 哪里坏了、根因、修复、怎么验证的。原样粘贴从失败到通过的复现输出。
