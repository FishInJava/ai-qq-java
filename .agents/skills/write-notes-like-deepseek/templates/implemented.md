# Agent Note: <标题>

Status: implemented

<!--
  【维护提醒】
  1. 现行法律契约：代码重构、改名或路径变更时，同批就地更新事实。禁止把 Decision 改写成另一个决定。
  2. 时态纪律：严禁 ## Proposal / ## Plan / ## Acceptance criteria 等计划态标题；Decision 用现在时（门禁只拒标题）。
  3. 归档契约：只插 Archived: YYYY-MM-DD 紧邻 Status: implemented。互链写在新笔记，不写进归档篇。
-->

## Problem

<动机：脱离方案也能独立成立。说明触发原因：什么破了、什么要变、不做会怎样>

## Decision

<已落地事实，全篇使用现在时描述。可插入「模块与调用方向 / 接口契约 / 配置与运行时 / 数据与迁移」等自由小节>
<例：`service` 只依赖 `manager`，禁止直连 `dao`；违规在评审与 `rg` 检索中拦截。>
<例：`application.yml` 的 `aiqq.thread.pool-size` 默认 8，上限 32，超过按 32 处理。>

## Alternatives considered

- **<备选 A>** — <先写其最强论据，再写为何被否>
- **<备选 B>** — <先写其最强论据，再写为何被否。不做/复用仅当当时真的权衡过才写，不要编>

## Consequences

- **收益**：<带来了什么确定性提升，无基线不写相对量词>
- **代价与已知上限**：<牺牲了什么，什么信号发生时必须重访该决定>

## Verification

<可选，现在时事实：如何证明该决定在当前代码库中依然生效。落到可确认表面——类/包路径、量级、命令>
<例：`mvn -f server/pom.xml -pl service -am test` 通过；`rg "import com.hobozoo.aiqq.dao" server/service/src/main` 无命中。>
