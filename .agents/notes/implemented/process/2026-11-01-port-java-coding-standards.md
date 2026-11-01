# Agent Note: 移植 ECC 的 Java 编码规范，保留双框架结构

Status: implemented

## Problem

上游 `G:\Grinding Gear Games\ECC-main\skills\java-coding-standards` 是 ECC v2.2.3 里的单文件技能，MIT（Copyright 2026 Affaan Mustafa），11,550 字节 / 384 行，只有一个 `SKILL.md`，没有 `references/`。

和前面两批（React、Postgres）不同，这份规则在本仓库直接有执行对象：Spring Boot 4 / Java 21 / MyBatis 多模块，`server/` 下 31 个 `.java`。

它的问题不是「用不上」，是三处口径和仓库对不上：

- **双框架**。文档覆盖 Spring Boot 与 Quarkus 两套，靠文中 `[QUARKUS]` / `[SPRING]` 标记切换（`QUARKUS` 出现 42 次，`SPRING` 19 次），开头还写了「Framework Detection」按构建文件判定。本仓库永远不会走到 Quarkus 那一半。
- **包结构**。文末 `[SPRING] Maven/Gradle` 给的布局是 `config/controller/service/repository/domain/dto/util`，本仓库是 `common/entity/dao/manager/interface/service/controller/mq/quartz/rpc/web`，包名 `com.hobozoo.aiqq.<模块>`。照抄会在 `server/` 下凭空长出 `repository/`、`domain/`。
- **不可变与 Lombok**。文中提倡 record 或「只有 getter 没有 setter」的类，仓库的实体是 Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`（`server/entity/.../demo/Cat.java`）。两者不是一套，得说清以哪边为准。

## Decision

整份复制到 `.agents/skills/java-coding-standards/`，正文一字不改，只动开头：

- `description` 换成单行中文，与前三个移植技能一致。
- frontmatter 补 `license: MIT`、`version: "2.2.3"`、`source`。上传只写了 `metadata.origin: ECC`，版本信息在仓库根 `VERSION` 里，单文件技能没有自己的版本号。
- 加「适用范围」三条，把上面三处分歧逐个钉死：Quarkus 段跳过、按 `AGENTS.md` 的模块分层、注释按 `RULES.md` 的三行十字、Lombok 按仓库现状用。

**不删 Quarkus 段**。虽然 42 处标记在本仓库永远不命中，删掉要重排 8 个章节、判断哪些句子是共享的哪些是框架专属，任何一处判断错都会把一条 Spring 规则连带删掉；而保留的代价只是读到 `[QUARKUS]` 时跳过。上游将来升版也能按行对齐。

正文保真度：从 `## When to Use` 到文件末尾 373 行，`Compare-Object` 与上游逐行相等。

## Alternatives considered

### 删掉 Quarkus 段，压成 Spring Boot 单框架版

最贴仓库。否掉的原因：删完大约剩 240 行，但要逐段判断「这句是无标记的共享规则还是 Quarkus 专属」，判断依据只有代码块里的 import；改完与上游已经不是同一份文档，以后无法按行对比升级，收益（少读 140 行）抵不过分叉成本。

### 把包结构那段直接改成仓库的模块结构

好处是 Agent 不会再新建 `repository/`。否掉的原因：那段是上游的通用示例，改了就成「上游文本 + 本仓库方言」的混合物，升级时说不清哪些行是上游的。用「适用范围」加一段说明更清楚，也不会误伤将来真要做独立模块的场景。

### 不移植，靠现有 `test-quality-evaluator` 管规范

仓库已有测试质量评估技能，覆盖测试分层与覆盖率口径，部分重叠。否掉的原因：那份管测试怎么写，不管命名、注入方式、异常、Optional、Stream、日志；这批规则补的正是写代码这一段。

## Consequences

收益：Spring 侧的命名、构造器注入、统一异常（`@RestControllerAdvice`）、`@ConfigurationProperties`、切片测试用法都有一条明确说法，且与仓库现有代码（4 空格缩进、构造器注入、`private final` 字段）基本一致，不用改现状。

代价：

- 每读一次要跳过约四成 Quarkus 内容，`[QUARKUS]` 42 处分散在 12 个章节里，没法整段略过。
- 「适用范围」写死了当前是 Spring Boot 4 / MySQL 时代的模块划分和 Lombok 用法。哪天拆模块或改成 record，这几行要跟着改。
- 版本号只能记 ECC 仓库的 2.2.3，单文件技能本身没有版本，上游改了哪几条无从得知，只能整份重取再 diff。
- 文中示例注释带 `// PASS:` / `// FAIL:` 前缀，按 `RULES.md` 的三行十字规则这些写法在本仓库代码里都不成立；「适用范围」里提了一句，但读的人未必会先读那节。
- 没有在本仓库跑过任何形式化检查（没有 Checkstyle / Spotless / ArchUnit），规范靠 Agent 自觉。

## Verification

- `npm run verify-skills`：`checked: 18 skill(s), 66 path reference(s), 0 error(s), 1 warning(s)`，唯一 warning 仍是既有的 `0test-skill`。
- `npm run verify-notes`：树与相对链接、格式、归档三个脚本全过。
- 正文保真：`## When to Use` 之后的 373 行 `Compare-Object` 结果为空；`SKILL.md` 存的 LF。
- 装载验证：写入后 harness 技能列表出现 `java-coding-standards`，改完 `description` 后返回新描述。
- 事实核对改过一次：初稿写「Lombok 依赖在但不没用上」，是拿 `server\*\src\**\*.java` 这种通配去 grep 的，漏了文件；改用 `Get-ChildItem -Recurse -Filter *.java` 重扫 31 个文件，命中 15 处，`entity/demo/Cat.java` 明确在用 `@Data @Builder`，已改回「实体在用 Lombok」。
- 未做：没有拿它评审真实代码，不知道 Agent 会不会把 Quarkus 段里的 `@ApplicationScoped` 误用到 Spring 类上。

前两批移植：[2026-11-01-port-react-skills.md](2026-11-01-port-react-skills.md)、[2026-11-01-port-postgres-skills.md](2026-11-01-port-postgres-skills.md)。
