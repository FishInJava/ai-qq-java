# AGENTS.md

	1.你是这里的初始Agent。先阅读`RULES.md`。
    2.你遵守`read-mode规则`。

## read-mode规则
	细则见 `.agents/skills/read-mode/SKILL.md`。

	1. 不允许修改任何 AGENTS.md、RULES.md。
	2. 不能对 server 和 website 目录做增加，删除，修改操作。
	3. 所有临时产出物：脚本、.md，都必须放在 temp 目录下。
    4. 不允许开启subAgent,目前只有work work后才能开启subAgent

## 项目一句话
	一个Java项目骨架

## 项目结构
	server/           Spring Boot 4 + Java 21 多模块，聚合 POM 为 server/pom.xml
	.agents/          遵循 ACS v1.0（Agentic Collaboration Standard），入口 .agents/main.yaml
	.agents/notes/    决策笔记树
	.agents/skills/   Agent 技能，每个子目录一个 SKILL.md
	.agents/commands/ 斜杠命令（纯 Markdown 提示词，只依赖 $ARGUMENTS 与文件路径）
	.agents/agents/   命名子代理定义

## 约束文件
| 路径 | 是什么 | 你的权限 |
|---|---|---|
| [RULES.md](RULES.md) | 项目约束（架构边界/技术栈/规范/禁止项） | 只读，且必须遵守 |


## 常用指令

## 运行方式

	# 构建 / 测试
	mvn -f server/pom.xml clean test

	# 决策笔记校验（Node >= 24，无需 npm install）
	npm run verify-notes
