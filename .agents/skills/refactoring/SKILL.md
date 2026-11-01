---
name: refactoring
description: 重构流程：先钉住行为契约，再只改结构，最后给等价性证明。work-work 的 refactoring-pb 就是它。
---

## 你是一个playbook

**契约归你负责。结构变，行为不变。** 它不同于 Feature（增加行为）和 Bug fix（修正行为）。

	清理过程中发现缺失功能或真实 bug，就拆出去，先针对钉住的契约把结构改动发掉。重设计可以，但要先命名，再转 `feature-pb`。大型或跨切面的结构工作不属于本 playbook。

	Step1：先钉住行为契约。跑 `how` 了解契约，然后写特征测试、快照或等价校验，在任何结构移动之前捕获当前行为。该区域没有覆盖时，先写钉子再碰结构。类型检查和 lint 不是钉子。
	Step2：按 principle-model-the-domain 命名代码缺失的结构。形状已经清晰且局部时，无聊的代码留着。重塑必须删掉分支或非法状态，不能增加间接层。
	Step3：命名目标形状。写清今天从头建的话，模块布局、类型和调用图该是什么样。目标跨函数边界时，先把形状落成文字再动手。
	Step4：先减后加。删死代码、折叠单调用包装、去掉冗余校验、清孤儿引用，都排在引入新形状之前。「也许有用」的投机清理要回滚。
	Step5：小步移动，每步保持行为，钉子一直绿。API 重塑时同一波迁移所有调用方并删除旧 API，不留兼容垫片、不留新旧并行路径。每个重命名都对照实际文件抽查，重命名会漏掉字符串和注释里的用法。机械编辑可以派给子代理，范围写死文件路径、要移动的名字、要保持的行为。
	Step6：在真实产物上证明行为未变，不是「能编译」。较大重塑跑等价检查，比如比较新旧输出的脚本，或把录制的基线在新代码上重放。跑 `mvn -f server/pom.xml clean test`。
	Step7：确认改动值得保留。成功标准是读者负担下降。diff 没在任何地方降低读者负担就回滚。
	Step8：拆成小而有序的 commit。一个减法提交，然后重塑，然后收尾清理。注释按 `RULES.md`，只写方法功能。

## 原则

	只读下面点名的全文，其余不读。路径相对仓库根。

	- 不知道受影响的子系统怎么运作、契约是什么 → `.agents/skills/how/SKILL.md`
	- 动手定核心类型与数据结构 → `.agents/skills/work-work/references/principle-foundational-thinking/SKILL.md`
	- 把新需求接进已有设计，按 day-one 假设重想 → `.agents/skills/work-work/references/principle-redesign-from-first-principles/SKILL.md`
	- 代码里有散落分支或重复的形状假设 → `.agents/skills/work-work/references/principle-model-the-domain/SKILL.md`
	- 排新增与删除的先后 → `.agents/skills/work-work/references/principle-subtract-before-you-add/SKILL.md`
	- 评估改动大小，或想加抽象层 → `.agents/skills/work-work/references/principle-laziness-protocol/SKILL.md`
	- 换内部 API 而旧调用方还在 → `.agents/skills/work-work/references/principle-migrate-callers-then-delete-legacy-apis/SKILL.md`
	- 代码难追踪，要减少层数与隐藏状态 → `.agents/skills/work-work/references/principle-minimize-reader-load/SKILL.md`
	- 声明行为未变之前 → `.agents/skills/work-work/references/principle-prove-it-works/SKILL.md`
	- 多步推进，每步都要留下可验证状态 → `.agents/skills/work-work/references/principle-sequence-verifiable-units/SKILL.md`

**回复：** 改了什么结构、拿什么钉子钉住、等价性证明、读者负担变化、发布什么和回滚什么。没有新行为。
