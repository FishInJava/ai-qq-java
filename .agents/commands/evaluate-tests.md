---
name: evaluate-tests
description: 评估测试质量，输出覆盖率、自欺欺人与精确率三维评分，并给出可执行的改进指令。
input: evaluation_scope
---

## 任务

先完整读取 `.agents/skills/test-quality-evaluator/SKILL.md`（harness 支持按 id 加载技能时优先用它，如 `skill://test-quality-evaluator`），按它的流程评估测试质量。

评估范围：$ARGUMENTS

范围为空时，评估整个 `server/` 聚合工程。

执行要求：

1. 报告里的每个数字都必须来自实际命令或脚本输出，不能编造，也不能沿用技能里的示例值。
2. 技能正文的「本仓库已知事实」表可能过时，先复核再引用，不一致时以实际检测为准并在报告中注明。
3. 需要修改 `pom.xml`、`lombok.config` 或 CI 配置时先问，得到同意再改。
4. 面向用户的文字按 `.agents/skills/unslop/SKILL.md` 的要求写。

## 输出格式

- 三维评分各一段，每段先给分数再给依据。
- 每条改进指令都要可执行：写清命令或文件位置。

> 占位符说明：`$ARGUMENTS` 由支持斜杠命令的 harness 替换成调用时附带的文本；不替换时，把该文本当作 `evaluation_scope`。
