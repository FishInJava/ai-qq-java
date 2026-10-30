---
name: work-work
description: work work 模式，通常用来解锁某些特定的工作流
---	
## 必须遵守的规则
	下面的「规则」部分是所有触发规则的依据。回复时，必须说明哪些原则影响了你的决策，以及这些原则具体改变了什么做法。
	
	1.涉及任何面向用户的文字内容时，比如前端，日志，生成的报告，包括回复用户本身：使用unslop skill
	2.修复 Bug 时，必须先在对应的实际运行环境中亲自复现问题。
	3.Bugbot或Agent自动化提出建议时：保持审慎，不要盲目接受所有意见。这些工具既能发现真正的 Bug，也可能提出不成立的问题或无关紧要的细节。必须逐条评估，根据具体理由决定修复、驳回或进一步询问，而不是为了消除所有评论而反复修改代码。

## 原则
	Read the leaf skill in full for any principle you apply. Each entry names when it applies. Each leaf is the **principle-<title>** skill, with the title in kebab case, hyphens kept and other punctuation dropped, unless the entry gives its name.
	
	**Core**
	- **Laziness Protocol.** Refactoring, sizing a diff, or tempted to add abstractions, layers, or signal threading. Bias to deletion and the smallest change that solves the problem.
	- **Foundational Thinking.** Before writing logic: core types and data structures, scaffold-vs-feature sequencing, what concurrent actors share.
	- **Redesign from First Principles.** Integrating a new requirement into an existing design. Redesign as if it had been foundational from day one.
	- **Attack the Premise.** Two or more fixes that share one premise have failed the same gate. Write the premise down and test it directly before you write another fix that assumes it.
	- **Subtract Before You Add.** Sequencing an addition, refactor, or rewrite. Remove dead weight first, then build on the simpler base.
	- **Minimize Reader Load.** Reviewing or shaping code that's hard to trace. Count layers and hidden state, collapse one-caller wrappers, shrink mutable scope.
	- **Outcome-Oriented Execution.** Planned rewrites and migrations with explicit phase boundaries. Converge on the target architecture, don't preserve throwaway compatibility states.
	- **Build the Lever.** Any non-trivial work. Build the tool that does or proves it (codemod, script, generator), not by hand. The tool is the artifact a reviewer reruns.
	
	**Architecture**
	- **Model the Domain.** Writing stateful logic, or code that branches a lot or repeats a shape assumption across files. Encode the domain in a structure (state machine, typed model, table or registry, reducer, boundary, the right collection) instead of scattered conditionals.
	- **Boundary Discipline.** Wiring validation, error handling, or framework adapters. Guards at system boundaries, trust internal types, keep business logic pure.
	- **Type System Discipline.** Designing types or a signature in any typed language. Make illegal states unrepresentable, brand primitives, parse external data at boundaries.
	- **Make Operations Idempotent.** Designing commands, lifecycle steps, or loops that run amid crashes and retries. Converge to the same end state.
	- **Migrate Callers Then Delete Legacy APIs.** Introducing a new internal API while old callers exist. Migrate and delete in one wave.
	- **Separate Before Serializing Shared State.** Concurrent actors might write the same file, branch, key, or object. Eliminate the sharing first.
		
	
	**Verification**
	- **Prove It Works.** After a task, before declaring done. Verify against the real artifact, not a proxy or "it compiles".
	- **Fix Root Causes.** Debugging. Trace each symptom back to the first place the system went wrong and fix it there. A guard is a fix only when the state it handles is legitimate.
	- **Sequence Work into Verifiable Units** (**principle-sequence-verifiable-units**). Multi-step work (sweeps, migrations, runs of similar edits) and how you stack commits and PRs. Break work into small units that each end in a check, verify each before the next, and order delivery so the sequence proves itself.
	- **Test Behavior, Not Implementation.** Writing, changing, or keeping a test. Assert what the code's users can observe, so the test fails when that behavior breaks and keeps passing when only the internals change.
	- **Explain the Number.** Apply before you measure, and before you trust, report, or act on a number you measured (a speedup, a regression, a throughput, a latency, or an eval result). Find what limits it, and rule out that it measured something other than the work you think.
	
## subAgent
	1.允许开启subAgent：subAgent,能解锁某些工作流。
	2.subAgent不能开启subAgent。
	3.子代理定义和父代理需要传递的内容：读 `.agents/agents/playbook-agent.md`。
	4.父代理处理子代理的返回：读 `.agents/agents/playbook-agent.md`。
	5.子代理的注册文件是 `.omp/agents/playbook-agent.md`（薄壳，正文指向第 3 条的路径）；它设了 `spawns: ""`，harness 层禁止递归。派活时 `agent` 填 `playbook-agent`。

## 解锁工作流
    所有工作流列表，读 `.agents/skills/work-work/playbooks`
	
    1.**test-pb**

