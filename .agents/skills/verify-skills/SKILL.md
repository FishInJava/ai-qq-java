---
name: verify-skills
description: 校验 .agents/skills 的结构：每个技能目录一个 SKILL.md、frontmatter 完整、引用的路径全部存在。改完任何技能或 playbook 后跑它，把输出交给模型分组分析再给结论。
---

# 校验技能

机械检查由 `scripts/verify-skills.ts` 做，判断和总结由你看输出做。脚本只回答「结构有没有断」，不回答「写得好不好」。

## 什么时候跑

- 新增、改名、移动、删除 `.agents/skills/` 下任何东西之后。
- 改了 `work-work/SKILL.md` 的 playbook 表，或搬动了原则叶子之后。
- 提交前。CI 未接入时这是唯一门禁。

## 怎么跑

仓库根目录：

	npm run verify-skills
	npm run verify-skills -- --json

`--json` 给模型读，字段是 `skills`、`checkedPaths`、`errors`、`warnings`、`findings[]`。每条 finding 带 `severity`、`code`、`skill`、`file`、`line`、`target`、`message`。不带 `--json` 时一行一条，人能直接看。

退出码 1 表示至少一条 error，0 表示只有 warning 或全过。

## 怎么分析

1. 先看 `errors` 与 `warnings` 的条数，再决定要不要读 `findings` 细节。全过就一句话回完，不要编问题。
2. 按 `code` 分组。同一个 code 出现多次，通常是一处根因，比如一个目录改名带出一串 `broken-path`。
3. 每条 error 都要落到「改哪个文件的哪一行」，不要只说「路径有问题」。
4. warning 单独说，不要和 error 混在一个结论里。

## code 对照

| code | 严重度 | 含义 | 修法 |
|---|---|---|---|
| `missing-skill-file` | error | 目录里没有 `SKILL.md` | 补文件，或删掉空目录 |
| `no-frontmatter` | error | 开头没有 `---` 包围的 frontmatter | 补 `name` 与 `description` |
| `missing-name` | error | 缺 `name` | 补成目录名 |
| `missing-description` | error | 缺 `description` | 补一句能说明何时触发的话 |
| `name-mismatch` | warning | `name` 与目录名不一致 | 技能仍能加载，但目录里的条目会对不上。确认是有意为之就留着 |
| `broken-path` | error | 引用的 `.agents/...` 路径不存在 | 改引用，或把目标文件移回来 |
| `broken-relative-path` | error | 引用的 `references/`、`scripts/` 等技能内路径不存在 | 同上，按行号定位 |

## 输出格式

- **结论**：通过 / 有 N 条 error / 只有 N 条 warning。
- **按 code 分组的问题**：每条写文件、行号、路径、以及改哪一边。
- **建议**：改引用还是改目标，说清哪边是错的，别两边都改。
- **没覆盖的**：`description` 能不能触发、正文有没有用、原则挑得对不对，这些一条都没查。

## 边界

- 不要为了让输出清零而改 `name`。`name-mismatch` 可能是有意的显示名。
- 不要跳过 error 只报 warning。
- 占位符（`{lifecycle}`、`2026-…-….md`）和代码块里的路径故意不查，别当漏报。
