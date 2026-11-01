---
name: feature
description: 新功能或行为变更的实现流程：先定数据形状，再实现、验证、拆成小而有序的提交。work-work 的 feature-pb 就是它。
---

## 你是一个playbook

**你负责设计：计划、评审、验证。实现可以委派，主导权留在你手上。**

	Step1：对受影响的子系统跑 `.agents/skills/how/SKILL.md`。只改一个类时直接读那几条路径。
	Step2：先定数据形状，再谈实现。用状态机代替散落的布尔值，用表或注册表代替分支，用类型化模型代替重复的形状假设。判据见 `principle-model-the-domain`。
	Step3：把吞吐检查点写成四个 todo。确实不适用的也保留，写 `n/a: <原因>`，不能删项：
	    - 阻塞性第一步。扇出之前先跑关卡。
	    - 独立工作流。不相交的文件、模块或层可以并行。共享写入要串行。
	    - 共享可变状态。默认拆分目标；只有真实不变量才串行。
	    - 最小安全分解。一个执行者最好时，说明为什么。
	Step4：实现。范围写死三样：文件路径、数据形状及其组织方式、成功标准。能开子代理就把实现派出去，评审分离留在自己手上；开不了就自己拥有 diff，但先把设计落成文字再改代码，改完按 Step5 自评。不要回一句「待命」去等嵌套代理。
	Step5：在匹配的界面上验证。跑 `mvn -f server/pom.xml clean test`，并且实际调到那条路径。不确定、没跑到、跑错界面都不算通过，写进报告。
	Step6：拆成小而有序的 commit。每个单元构建、验证、提交完再进下一个。
	Step7：交付。注释按 `RULES.md`，只写方法功能，一句话，最多 3 行，每行不超过 10 字。

## 原则

	只读下面点名的全文，其余不读。路径相对仓库根。

	- 动手写逻辑前定核心类型与数据结构、判断并发方共享什么 → `.agents/skills/work-work/references/principle-foundational-thinking/SKILL.md`
	- 写有状态逻辑，或分支散落、同一形状假设跨文件重复 → `.agents/skills/work-work/references/principle-model-the-domain/SKILL.md`
	- 并发方可能写同一份状态 → `.agents/skills/work-work/references/principle-separate-before-serializing-shared-state/SKILL.md`
	- 多步推进，每步都要留下可验证状态 → `.agents/skills/work-work/references/principle-sequence-verifiable-units/SKILL.md`
	- 声明完成之前 → `.agents/skills/work-work/references/principle-prove-it-works/SKILL.md`
	- 想加抽象、分层，或评估改动大小 → `.agents/skills/work-work/references/principle-laziness-protocol/SKILL.md`
	- 拿测量数字下结论（耗时、覆盖率、行数） → `.agents/skills/work-work/references/principle-explain-the-number/SKILL.md`

**回复：** 构建了什么、选了什么及为什么、吞吐检查点、未决决策。设计备选用表格。
