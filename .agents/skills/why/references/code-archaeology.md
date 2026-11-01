# 代码与仓库历史

## 有什么

- 提交历史（提交信息、日期、作者、diff）
- PR 正文、评审评论和讨论串（用 `gh`）
- 行内代码注释、TODO、FIXME、废弃说明
- `AGENTS.md` 与 `RULES.md` 里的项目约束
- `.agents/notes/` 下的决策笔记
- 测试。测试名和断言常常记录了促成改动的边界情况
- 同一次提交里一起改动的相关文件（共变信号）
- 仓库里的 CHANGELOG 和发布说明

这是最可信、与代码绑定最直接的来源。进过仓库的东西都应该在这里。

## 怎么搜

先扩种子提交列表：

```bash
# 穿过改名的历史
git log --follow --oneline -- <file>

# Pickaxe：新增或删除过这段原文的提交
git log -S '<代码里的原文>' -- <file>

# 按模式搜
git log -G '<正则>' -- <file>

# 每一行是谁、什么时候写的
git blame -L <start>,<end> <file>

# 单次提交的完整 diff
git show <hash>

# 两个点之间影响这个文件的提交
git log <old>..<new> -p -- <file>
```

再找仓库里的旁证文档：

```bash
# 决策笔记：一个决定一个文件，包含 Problem / Decision / Alternatives
rg -l '<符号或子系统名>' .agents/notes

# 笔记自己的小节就是理由，整份读
rg -n -A 20 '^## Decision' .agents/notes

# ADR 常放在 docs/adr/ 之类的地方
rg -l -i 'architecture.decision' --glob '*.md'

# 目标附近的 TODO 与 FIXME
rg -n -C2 '(TODO|FIXME|HACK|XXX|NOTE)' <target_file>

# 相关测试
rg -l '<符号>' --glob '*test*'
```

## 什么算理由

提交信息、PR 正文、评审评论、代码注释、决策笔记是写下来的理由，可以直接引用。

diff、测试和同一次改动的文件是旁证。它们说明改了什么，不说明为什么。从旁证推理由时要标成推断。

## 防御性代码

空值判断、重试、超时、限流、开关、出口守卫、OOM 处理这类代码，优先看三种提交：写着「修事故」或「加防御性检查」的提交，紧跟着的回滚，以及回滚之后的「重新应用，加上……」。这些通常是当时的外部压力留下的痕迹。
