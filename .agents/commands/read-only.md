---
name: read-only
description: 本会话进入只读调查：不写文件、不执行写入型命令，改代码交给子代理。仅提示词约束，不是权限拦截。
input: investigation_target
---

## 任务

用户消息已含 `/read-only`，本会话进入只读模式。协议正文见 `AGENTS.md` 的「只读协议」节，本文件只是同一个协议的 harness 变体：

1. 不改仓库内任何文件：不调用 edit / write / patch，不执行写入型 shell（重定向、`Set-Content`、`New-Item`、`Remove-Item`、`git apply`、`mvn clean test` 等）。
2. 允许只读工具（read / grep / glob / 联网搜索）与只读型 shell（`git status`、`git diff`、`git log`、`mvn -v`）；唯一写入例外是 `temp/`，写完在回复里说明。
3. 需要改代码时交给子代理（subagent），任务描述里写清文件、改动与验收命令；不要把 `/read-only` 或 `MODE: READ-ONLY` 带进子任务。
4. 每轮回复首行 `MODE: READ-ONLY`，末行说明本轮有无写操作；越界立即自报「越界：<命令>」。

调查目标：$ARGUMENTS

## 输出格式

- 首行 `MODE: READ-ONLY`。
- 结论在前，证据在后（文件路径、行号、命令输出）。
- 产出物只写 `temp/`。

> 占位符说明：`$ARGUMENTS` 由支持斜杠命令的 harness 替换成调用时附带的文本；不替换时，把该文本当作 `investigation_target`。

> harness 说明：dsh 没有 markdown 命令加载器，`/read-only` 在 dsh 里只是普通消息文字，协议由 `AGENTS.md` 承载。本文件是 ACS 约定的副本；OpenCode 读 `.opencode/commands/`、Claude Code 读 `.claude/commands/`，需要那两边生效就同步过去。
