---
description: 评估测试质量，输出覆盖率、自欺欺人与精确率三维评分
---

调用 skill 工具加载 id 为 `test-quality-evaluator` 的 skill，按它的流程评估测试质量。

评估范围：$ARGUMENTS

范围为空时，评估整个 `server/` 聚合工程。

执行要求：

1. 报告里的每个数字都必须来自实际命令或脚本输出，不能编造，也不能沿用 skill 里的示例值。
2. skill 正文的「本仓库已知事实」表可能过时，先复核再引用，不一致时以实际检测为准并在报告中注明。
3. 需要修改 `pom.xml`、`lombok.config` 或 CI 配置时先问，得到同意再改。
4. 面向用户的文字按 `unslop` skill 的要求写。
