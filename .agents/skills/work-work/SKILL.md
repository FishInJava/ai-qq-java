---
name: work-work
description: work work 模式，通常用来解锁某些特定的工作流
---
## 必须遵守的规则
	下面的「规则」部分是所有触发规则的依据。回复时，必须说明哪些原则影响了你的决策，以及这些原则具体改变了什么做法。
	
	1.涉及任何面向用户的文字内容时，比如前端，日志，生成的报告，包括回复用户本身：使用unslop skill
	2.修复 Bug 时，必须先在对应的实际运行环境中亲自复现问题。
	3.Bugbot或Agent自动化提出建议时：保持审慎，不要盲目接受所有意见。这些工具既能发现真正的 Bug，也可能提出不成立的问题或无关紧要的细节。必须逐条评估，根据具体理由决定修复、驳回或进一步询问，而不是为了消除所有评论而反复修改代码。
	
## subAgent
	1.允许开启subAgent：subAgent,能解锁某些工作流。
	2.subAgent不能开启subAgent。
	3.子代理定义和父代理需要传递的内容：读 `.agents/agents/playbook-agent.md`。
	4.父代理处理子代理的返回：读 `.agents/agents/playbook-agent.md`。
	5.子代理的注册文件是 `.omp/agents/playbook-agent.md`（薄壳，正文指向第 3 条的路径）；它设了 `spawns: ""`，harness 层禁止递归。派活时 `agent` 填 `playbook-agent`。

## playbooks
	按需加载：只读本次要跑的那一个 playbook，其余不读。派活时把下表里的完整路径原样传给 `playbook-agent`。

	| playbook | 何时用 | 路径 |
	|---|---|---|
	| test-pb | 测试相关工作流；Step 1 执行 `0test-skill` | `.agents/skills/work-work/playbooks/test-pb/test-playbook.md` |

## 原则
	原则叶子在 `.agents/skills/work-work/references/principle-*/SKILL.md`，共 19 份。
	加载责任在子代理：它按拿到的 playbook 的 `## 原则` 节读取被点名的叶子；playbook 没点名的，读完 playbook 也不读。
	父代理不读原则叶子，也不把原则清单转述给子代理。
