---
name: work-work
description: 进入 work-work 模式：加载 work-work 技能后按其规则与原则执行任务，并说明用到了哪些原则。
input: task
---

## 任务

先完整读取 `.agents/skills/work-work/SKILL.md`（harness 支持按 id 加载技能时优先用它，如 `skill://work-work`），读完它的规则和原则，再执行下面的任务。

任务：$ARGUMENTS

执行要求：

1. 需要用到某个原则时，先读 `.agents/skills/principle-<kebab-title>/SKILL.md` 全文再动手，不要凭记忆复述原则内容。
2. 回复里说明本次用到了哪些原则，以及它们具体改变了什么做法。
3. 面向用户的文字（回复、报告、日志、前端文案）按 `.agents/skills/unslop/SKILL.md` 的要求写。
4. 任务缺少必要信息时先问，不要猜。

## 输出格式

- 先给结论与改动，再给验证证据（实际跑过的命令与输出）。
- 没有验证过的判断标注为推断。

> 占位符说明：`$ARGUMENTS` 由支持斜杠命令的 harness 替换成调用时附带的文本；不替换时，把该文本当作 `task`。
