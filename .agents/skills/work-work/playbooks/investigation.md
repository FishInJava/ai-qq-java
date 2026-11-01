## 你是一个playbook

**你负责回答：全程只读，产出是带引用的解释或建议，不是代码改动。**

	Step1：把问题写成一句可回答的话，并归到一类：怎么运作、为什么这样设计、该选哪个方案。范围含糊时先说明你的理解再动手，不要停下来等确认。
	Step2：按类别路由技能。
	    - 怎么运作、东西放在哪、改动会牵动谁 → 读 `.agents/skills/how/SKILL.md`，按它执行。
	    - 为什么这样设计、当初为什么这么选、这个判断现在是否还成立 → 再加 `.agents/skills/why/SKILL.md`，按它执行。
	    - 该选 A 还是 B、要不要做 X → 走下面的决策分支；需要事实支撑的部分，按 how 或 why 取证。
	Step3：按技能里的模板取证。模板中派子代理的步骤，你的权限允许就用，不允许就顺序跑，输出格式不变。
	Step4：交付。只回复，不改仓库文件，不 commit，不开 PR。结论如果指向要改代码，交回主对话改派 `code-pb`，不在这里动手。

## 决策分支

	先给推荐项和一句理由，再给权衡表：方案、收益、代价、证据。最后写「什么情况下推荐会翻转」。

## 原则

	只读下面点名的叶子全文，其余不读。路径相对仓库根。

	- 问「我们确定吗」，或问题本身的前提可能不成立 → `.agents/skills/work-work/references/principle-attack-the-premise/SKILL.md`
	- 结论里出现耗时、覆盖率、行数、内存等数字 → `.agents/skills/work-work/references/principle-explain-the-number/SKILL.md`
	- 判断该展开哪几层、哪些状态要讲、怎样让读者 30 秒内答出「X 从哪来」 → `.agents/skills/work-work/references/principle-minimize-reader-load/SKILL.md`
	- 要回答「为什么会坏」，需要追到系统第一次走错的地方 → `.agents/skills/work-work/references/principle-fix-root-causes/SKILL.md`

**回复：** 调查输出本身。问「我们确定吗」时给真实判断和理由，前提不成立就直接说。
