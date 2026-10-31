---
name: work-work
description: playbook就是某个具体工作流，如果确认要使用某个playbook，必须开启subAgent。
---
## 描述
    设计目的：1.纰漏playbook目录 2.允许开启subAgent

## 必须遵守的规则
	下面的「规则」部分是所有触发规则的依据。回复时，必须说明哪些原则影响了你的决策，以及这些原则具体改变了什么做法。

	-修复 Bug 时，必须先在对应的实际运行环境中亲自复现问题。（不应该在这里）
	-Bugbot或Agent自动化提出建议时：保持审慎，不要盲目接受所有意见。这些工具既能发现真正的 Bug，也可能提出不成立的问题或无关紧要的细节。必须逐条评估，根据具体理由决定修复、驳回或进一步询问，而不是为了消除所有评论而反复修改代码。
	
## subAgent
	-playbook-agent：`.agents/agents/playbook-agent.md`

## playbooks（这种竖杠分割的形式，AI好看吗？）
	按需加载：只读本次要跑的那一个 playbook，其余不读。派活时把下表里的完整路径原样传给 `playbook-agent`。

	| playbook | 何时用 | 路径 |
	|---|---|---|
	| test-pb | 测试相关工作流；Step 1 执行 `0test-skill` | `.agents/skills/work-work/playbooks/test-pb/test-playbook.md` |
