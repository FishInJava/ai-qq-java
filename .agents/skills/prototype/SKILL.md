---
name: prototype
description: 一次性原型流程：只为做一个设计决策而写，做完就扔，不产出可发布代码。work-work 的 prototype-pb 就是它。
---

## 你是一个playbook

**设计决策归你负责，不负责代码。原型是一次性工具，真正的构建交给 `feature-pb`。**

	这是唯一一个把「最小改动」倒过来的 playbook。速度优先于打磨，代码质量不重要，不做计划。严谨性体现在便宜地选对方案。主动提出用户没要求的变体，扔掉一个方案再试另一个。

	Step1：界定原型要做的决策。是接口契约的形状、一次调用的时序、并发下的行为，还是两种实现里选一个。没有决策就没有原型，转 `feature-pb`。
	Step2：设计空间开放时先收集参考。找先例，列出可选方向，让用户先选方向再动手。方向已定就跳过。
	Step3：在 `temp/prototype-<slug>/` 里做一次性实现，与 `server/` 源码分开。接口或并发问题用能跑起来的最小脚本或最小 Spring 上下文；视觉决策用最轻的 HTML，依赖走 CDN。不受生产框架约束，不写测试，不做抽象。
	Step4：比较备选方案时，把它们放在一个切换器后面（命令行参数、URL 参数或按钮），每个变体有标签。至少给两个结构不同的变体，这是便宜版的「把设计空间走一遍」。
	Step5：在匹配的界面上观察。行为或时序决策靠打印输出、记录耗时或看渲染结果；视觉决策实际打开并驱动交互。这里观察就是证据，不是断言。
	Step6：交付备选方案、权衡和建议。产出是决策加一次性产物，不是可发布代码。选中的方向交给 `feature-pb` 做真正构建。明确说原型是一次性的。

## 原则

	只读下面点名的全文，其余不读。路径相对仓库根。

	- 想给原型加抽象、顺手打磨，或纠结要不要写测试 → `.agents/skills/work-work/references/principle-laziness-protocol/SKILL.md`
	- 决定要探的是数据形状还是调用时序 → `.agents/skills/work-work/references/principle-foundational-thinking/SKILL.md`
	- 声明看到了结论之前 → `.agents/skills/work-work/references/principle-prove-it-works/SKILL.md`
	- 用耗时、内存这类观测数字下结论 → `.agents/skills/work-work/references/principle-explain-the-number/SKILL.md`

**回复：** 探索了哪些变体、证据（行为决策给输出或耗时，视觉决策给截图）、权衡、建议、临时目录路径。明确说原型是一次性的。
