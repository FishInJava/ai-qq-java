---
name: test-quality-evaluator
description: 评估 Java/Maven 仓库（Spring Boot + JUnit 5 + Mockito + AssertJ + JaCoCo）的测试可用性，检测 AI 自欺欺人测试（确认偏误），输出覆盖率、自欺欺人与精确率三维评分，并给出可执行的改进指令。用户提到测试质量、测试可用性、覆盖率、自欺欺人测试、AI 确认偏误、测试有效性、改进测试或要求评估现有测试时使用。
---

# 测试质量评估器（Java版）

**触发条件**：用户提及「测试质量」「测试可用性」「覆盖率」「自欺欺人测试」
「AI 确认偏误」「测试有效性」「改进测试」，或要求评估现有测试时调用。
**报告语言**：默认中文；代码、命令、类名保持原文。

---

## 一、本仓库已知事实

评估前先复核以下事实，若与当前仓库不符，以实际检测为准并在报告中注明。

| 项目 | 现状 |
|---|---|
| 构建 | Maven 3.9.6，聚合 POM 为 `server/pom.xml`，11 个模块 |
| 语言/框架 | Java 21，Spring Boot 4.1.2，Spring Framework 7 |
| 测试依赖 | **无** `spring-boot-starter-test`、无 JUnit、无 JaCoCo、无 Surefire 配置 |
| 测试源码 | **不存在** `src/test/java`，当前测试基线为 0 |
| 业务代码 | `common` 工具类（`DateUtil`/`JsonUtil`/`CacheUtil`/`ThreadUtil`）、`controller` 的 `DemoController`、`service` 的并发示例、`dao` 的 MyBatis 骨架 |
| 环境坑 | **`JAVA_HOME` 当前指向 Java 8**（`G:\JetBrains\java8`），PATH 上的 `java` 是 21；直接跑 `mvn` 会因 `release 21` 失败 |

**关键约束**：

- 覆盖率报告不是「项目已有能力」，首次评估需要先补 `spring-boot-starter-test`
  与 JaCoCo（**修改 `pom.xml` 前必须征得用户同意**）。
- 没有测试时，覆盖率/自欺欺人/精确率都**不能记 100 分**，应报告
  「无测试基线（0 个测试）」，并进入「Step 6 建立基线」。
- 禁止为了让数字好看而排除业务包、只跑部分模块、或把测试标成 `@Disabled`。

---

## 二、三个维度

- **覆盖率（Coverage）**：测试执行到了多少代码。客观数字，来自 JaCoCo。
- **自欺欺人程度（Self-Deception）**：测试在多大程度上是 AI 为了
  「看起来完成了任务」而写的，而不是验证业务正确性。越低越好。
- **测试精确率（Precision）**：如果代码里存在真实 bug，测试有多大可能抓住它。
  越高越好，0–100。

**最有价值的一句判断**：高覆盖率 + 高自欺欺人 = 最危险的测试，
它提供虚假的安全感。

---

## 三、评估流程

### Step 0：环境预检（必做，失败则先修环境）

```powershell
# 让 Maven 使用 JDK 21
$env:JAVA_HOME = (Get-Command java).Source | Split-Path -Parent | Split-Path -Parent
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -v
```

`mvn -v` 必须显示 `Java version: 21.x`。如果 PATH 上的 `java` 也不是 21：

```powershell
# 本机示例路径，按实际安装位置调整
$env:JAVA_HOME = 'G:\JetBrains\jdk-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

**不要**通过降级 `java.version` 或修改 POM 来绕开环境问题。

### Step 1：项目检测

1. 确认构建方式：`server/pom.xml` 是聚合 POM；模块命令的 `-pl` 路径
   相对于 `server/`。
2. 扫描测试相关配置：

   ```powershell
   Select-String -Path server/pom.xml,server/*/pom.xml `
     -Pattern 'starter-test|junit|jacoco|pitest|surefire|failsafe|testcontainers|mockito'
   Get-ChildItem server -Recurse -Directory |
     Where-Object { $_.FullName -match 'src[\\/]test[\\/]java$' }
   ```

3. 识别测试框架与命名：
   - Surefire 默认：`*Test.java`、`Test*.java`、`*Tests.java`、`*TestCase.java`
   - Failsafe 默认：`*IT.java`、`IT*.java`（集成测试，需单独 `verify`）
   - 断言：JUnit Jupiter `Assertions` / AssertJ `assertThat`
   - Mock：Mockito `mock()` / `@Mock` / `@MockitoBean`（Boot 4 **已移除**
     `@MockBean`、`@SpyBean`）
   - Spring 测试切片：`@WebMvcTest`、`@DataJpaTest`、`@MybatisTest`、`@SpringBootTest`
   - 外部依赖：Testcontainers、H2、WireMock 等
4. 统计测试方法数。**没有测试就到此为止**，直接跳到 Step 6，不要伪造
   覆盖率或评分。

### Step 2：运行覆盖率（JaCoCo）

仓库当前未配置 JaCoCo，可用「命令行直接调插件」的方式，不改 POM：

```powershell
# 全量（聚合 POM）
mvn -f server/pom.xml `
  org.jacoco:jacoco-maven-plugin:prepare-agent clean test `
  org.jacoco:jacoco-maven-plugin:report

# 单模块 + 依赖模块（示例：只评估 common）
mvn -f server/pom.xml -pl common -am `
  org.jacoco:jacoco-maven-plugin:prepare-agent clean test `
  org.jacoco:jacoco-maven-plugin:report
```

说明：

- `prepare-agent` 必须在 `test` 之前；顺序不对会得到空的 `jacoco.exec`。
- 若父 POM 的 `pluginManagement` 没有 JaCoCo 版本，命令会报
  “plugin version not specified”，此时显式加版本（以 Maven Central 最新为准）：
  `org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent`。
- 报告位置：`server/<module>/target/site/jacoco/`
  （`jacoco.csv`、`jacoco.xml`、`html/index.html`）。
- 聚合汇总用本 skill 自带脚本：

  ```powershell
  pwsh -File .agents/skills/test-quality-evaluator/scripts/coverage-summary.ps1 -Root server
  ```

**读取顺序**：先看 **分支覆盖率（BRANCH）**，再看行/方法覆盖率。
不要只看行覆盖率下结论。

**阈值口径**（业务模块）：

| 指标 | 目标 | 说明 |
|---|---|---|
| 分支覆盖率 | ≥ 80% ✅；60–79% 🟡；< 60% 🔴 | 核心业务/工具类模块 |
| 行覆盖率 | ≥ 80% ✅ | 参考值，不单独作为结论 |
| 方法覆盖率 | ≥ 85% ✅ | 对应原 skill 的 Functions |

例外：纯 `entity` DTO/DO、配置类、`*Application` 启动类、Lombok 生成代码
可以单独统计，不计入核心阈值；但必须在报告中说明，不能静默排除。
建议在 `server/lombok.config` 添加 `lombok.addLombokGeneratedAnnotation = true`，
让 JaCoCo 过滤 Lombok 生成的方法（改配置前征得用户同意）。

### Step 3：扫描 AI 自欺欺人反模式

**扫描单位是「测试方法」，不是文件。** 一条测试可命中多个反模式，权重叠加，
上限 100%：

```
Self-Deception = min(100%, Σ(命中反模式的测试数 × 权重) / 总测试数)
```

| # | 反模式 | 权重 | Java 场景下的典型信号 |
|---|---|---|---|
| 1 | **预期值倒推** | +30% | 读完实现后，把 `return` 里的常量/表达式直接抄进 `assertEquals`；expected 由被测方法或同一工具方法计算出来；断言值只在实现中存在、业务语义上无法独立推导 |
| 2 | **mock 万能** | +25% | 所有外部依赖都 mock，断言只有 `verify(...)` / `verifyNoInteractions(...)`；没有验证返回值、DTO 映射、异常、持久化结果；`ArgumentCaptor` 抓了参数却不断言字段 |
| 3 | **happy path 依赖** | +20% | 只有正常输入；从未出现 `null`、`0`、`""`、`[]`、`Optional.empty()`、负数、上界、超长/Unicode、时区/闰年、并发边界 |
| 4 | **实现耦合** | +15% | `ReflectionTestUtils`/`getDeclaredField` 断言私有字段；`verify` 精确到内部调用顺序；断言具体 SQL/日志文本；断言缓存内部 Map；断言私有方法 |
| 5 | **占位填充** | +10% | `assertTrue(true)`、空测试体、无任何断言、`@Disabled`/`@Ignore`、`assumeTrue(false)`、`// TODO`、只有 `assertDoesNotThrow` |

**检测方法（逐条测试执行）**：

1. **预期值倒推**：定位被测方法，读它的 `return` / 赋值 / 常量。
   对比测试中的 expected 是否能从实现「直接抄到」。能抄到，且无法从
   方法名、Javadoc、类型、README 独立推导 → 命中。
   注意：评估阶段**必须**读实现来对比；写新测试时则相反，先按契约写 expected。
2. **mock 万能**：统计 `mock()`/`@Mock`/`@MockitoBean`/`when(...)`/`verify(...)`。
   若一个测试的所有断言都是 `verify` 系列，且没有对返回值/副作用/异常的断言
   → 命中。Mock 后必须再验证一层「mock 数据经过被测代码处理后的真实结果」。
3. **happy path 依赖**：列出被测方法的输入域，检查测试是否覆盖
   `null`、空集合、`0`、负数、边界日期、非法格式、超时/中断。
   一个方法从未被边界值调用过 → 命中。
4. **实现耦合**：断言是否依赖私有实现（反射、内部字段、调用顺序、SQL 文本、
   日志文本）。行为不变的重构会让测试挂掉 → 命中。
5. **占位填充**：正则匹配 `assertTrue(true)`、`assertFalse(false)`、
   `@Disabled`、`@Ignore`、`assumeTrue(false)`、`// TODO`、空方法体、
   零断言。

**辅助脚本**（启发式，结果必须人工复核，不能直接当结论）：

```powershell
pwsh -File .agents/skills/test-quality-evaluator/scripts/scan-test-smells.ps1 -Root .
# 机器可读：
pwsh -File .agents/skills/test-quality-evaluator/scripts/scan-test-smells.ps1 -Root . -Json
```

脚本会按测试方法输出：是否含边界值、异常路径、参数化、纯 mock 断言、
仅 not-null 断言、占位/无断言、实现耦合、mock 重、`Thread.sleep`。

**Java 额外气味（不参与加权，写入报告「其他风险」）**：

- 同义反复断言：`assertEquals(service.calc(x), service.calc(x))`、
  用被测工具类生成 expected。
- 只断言 `assertNotNull`，不断言字段值。
- `try { ... fail(); } catch (Exception ignored) {}` 吞异常。
- 只断言 HTTP 200，不断言响应体字段。
- `@SpringBootTest` 拉起全量上下文却只测一个纯函数（慢且掩盖设计问题）。
- 用 `Thread.sleep` 等异步；应改用 `Awaitility` 或 `CountDownLatch`。
- 依赖 `LocalDate.now()`/`System.currentTimeMillis()` 且无法注入 `Clock`。
- `lenient()`、`Mockito.reset()`、`mockStatic` 滥用。
- `@Transactional` 测试无条件回滚，永远验证不到提交行为。
- 测 Lombok getter/setter、测框架本身。

### Step 4：计算测试精确率

先对每个测试方法打四个布尔标记，再算比例：

```
boundaryRatio  = 含边界值输入的测试数 / 总测试数
exceptionRatio = 断言了异常/失败路径的测试数 / 总测试数
contractRatio  = 验证行为契约的测试数 / 总测试数
pureMockRatio  = 只有 mock 交互断言的测试数 / 总测试数

Precision = 25×boundaryRatio + 25×exceptionRatio
          + 30×contractRatio + 20×(1 − pureMockRatio)
```

结果天然落在 0–100。**这是对原 skill 公式的修正**：原公式在算出各维度
比例后又除以一次总测试数，会重复惩罚测试数量；这里改为「先算比例，再加权」。

各维度判定口径：

- **边界值覆盖**：`null`、空字符串、空白、`0`、负数、`Integer/Long.MIN|MAX_VALUE`、
  `BigDecimal` 精度/scale、空集合、`Optional.empty()`、超长字符串、Unicode/emoji、
  闰年/月末/时区、分页越界、重复键。
  优先使用 `@ParameterizedTest` + `@ValueSource`/`@CsvSource`/`@MethodSource`。
- **异常路径覆盖**：`assertThrows`/`assertThatThrownBy` 断言**具体异常类型**
  （必要时断言消息关键片段或异常字段），异步失败（`ExecutionException`、
  `TimeoutException`、`isCompletedExceptionally`），以及错误码/降级结果。
- **行为契约验证**：断言公开方法的返回值、DTO 映射结果、数据库可见状态、
  HTTP 响应体。不依赖内部字段/调用顺序。
- **纯 mock 断言**：该测试的所有断言都是 `verify`/`verifyNoInteractions`，
  没有值断言、异常断言或副作用断言。

**可选：用 PIT 做客观精确率参考**（比覆盖率更接近「发现 bug 的能力」）：

```powershell
mvn -f server/pom.xml -pl common -am test `
  org.pitest:pitest-maven:1.17.4:mutationCoverage `
  -DtargetClasses='com.hobozoo.aiqq.common.*' `
  -DtargetTests='com.hobozoo.aiqq.common.*Test' `
  -DoutputFormats=HTML,XML
```

PIT 版本以 Maven Central 最新稳定版为准。mutation score 可写入报告作为
「精确率参考值」，但不要用它替代三维评分；PIT 较慢，只对纯逻辑模块运行。

### Step 5：生成评分报告

固定输出以下结构（数字必须来自实际命令/脚本，不能编造）：

```
📊 测试质量评估报告（Java / Maven）

环境：JDK 21.x / Maven 3.9.x / Spring Boot 4.1.2
范围：server/<modules>（共 N 个测试类、M 个测试方法）

一、覆盖率（JaCoCo）
  Module        Branch      Line       Method
  common        62.0% 🔴    81.3% ✅   90.0% ✅
  service       41.2% 🔴    66.8% 🔴   58.3% 🔴
  TOTAL         55.6% 🔴    74.9% 🟡   71.4% 🟡
  说明：entity/DTO 与启动类已单列，不计入核心阈值（原因：……）

二、自欺欺人程度：55%（越高越差）
  🔴 [P0] 预期值倒推：4 处
     server/common/src/test/java/.../DateUtilTest.java:42
       expected 与实现中的常量/分支直接对应，无法从契约独立推导
  🟡 [P1] mock 万能：3 处
     .../DemoServiceTest.java:22,35,48
       只验证 dao 被调用，未验证 DO → BO 映射结果
  🟡 [P1] happy path 依赖：5 个方法从未被边界值覆盖
     DateUtil.parseDate、JsonUtil.toJson、...
  🟢 [P2] 实现耦合：0 处
  🟢 [P2] 占位填充：0 处
  其他风险：Thread.sleep 2 处；仅 assertNotNull 6 处

三、测试精确率：45/100
  边界值覆盖：2/12 个方法
  异常路径：  1/12 个方法
  行为契约：  5/12 个方法
  纯 mock 断言：40%
  （PIT mutation score：xx%，如已运行）

四、关键问题（按影响排序）
  1. ……
  2. ……

五、结论
  可用性：不可用 / 有风险 / 可用 / 优秀
  （<60 不可用；60–74 有风险；75–89 可用；90+ 优秀）
```

**无测试时的报告**：

```
📊 测试质量评估报告
  测试基线：0 个测试类 / 0 个测试方法
  覆盖率：N/A（不是 0%，也不是 100%）
  自欺欺人：N/A
  精确率：0/100（没有任何测试能发现 bug）
  结论：不可用。先按 Step 6 建立最小基线，再评估。
```

### Step 6：给出改进指令

按 P0 / P1 / P2 排序，每条必须包含：文件、方法、问题、**可直接执行的改法**
（Java 代码示例）、改完后的验证命令。示例：

```
[P0] 消除预期值倒推 — DateUtilTest#formatDate
  当前：
    assertEquals("2026-10-29", DateUtil.formatDate(LocalDate.of(2026,10,29)));
    // expected 是从实现里的 formatter 模式抄来的
  正确做法：
    - 从方法契约（Javadoc / 方法名 / 调用方）确认输出格式
    - 补独立可推导的用例，并覆盖时区/闰年：
      assertEquals("2024-02-29", DateUtil.formatDate(LocalDate.of(2024,2,29)));
    - 若实现与契约不符，改实现，不要改预期来迁就实现
  验证：
    mvn -f server/pom.xml -pl common -am test

[P0] 补全 mock 后的业务验证 — DemoServiceTest#getUser
  当前：
    when(dao.find(1L)).thenReturn(doObj);
    service.getUser(1L);
    verify(dao).find(1L);
  补全：
    UserBO bo = service.getUser(1L);
    assertThat(bo.getId()).isEqualTo(1L);
    assertThat(bo.getDisplayName()).isEqualTo("Alice");
    // 验证 DO → BO 的字段映射、空值处理、找不到时的异常

[P1] 增加边界值 — JsonUtilTest
  null、""、"{}"、非法 JSON、超长字符串、Unicode
  统一用 @ParameterizedTest + @CsvSource

[P1] 用真实持久层替代 SQL mock — DemoMapperTest
  不要 verify(mapper).selectById(1L)
  用 @MybatisTest + H2/Testcontainers，断言查询结果

[P2] 去掉 Thread.sleep，改用 Awaitility / CountDownLatch
```

改完测试后必须重跑覆盖率与扫描脚本，并对比前后数字。

---

## 四、核心原则（防自欺欺人）

1. **先写预期，后看实现**
   - 读方法名、参数类型、Javadoc、README、调用方，推导输入 X 的业务输出 Y。
   - 先写 expected，最后才看实现并运行。
   - 实现与预期不符时，**改实现**，而不是把 expected 改成实现的样子。
2. **Mock 是手段，不是目的**
   - mock 掉外部依赖后，必须验证「mock 数据经过被测代码处理后的结果」：
     返回值、DTO 映射、异常、持久化副作用。
   - 不能停在 `verify(mock).xxx()`。
3. **Happy path 只是起点**
   - 每个公开方法至少 1 个正常路径 + 1 个边界值 + 1 个异常路径。
   - 边界值 = 类型的最小/最大/零/空/非法值 + 业务边界（分页、日期、金额精度）。
4. **测试是契约，不是实现说明书**
   - 只断言公开接口的输入输出和可观察副作用。
   - 不碰私有字段、不锁调用顺序、不断言 SQL/日志文本。
   - 行为不变的重构，测试必须仍然通过。
5. **覆盖率是结果，不是目标**
   - 不为凑数字写无断言测试、空测试、DTO getter 测试。
   - 缺失覆盖必须先判断是否是关键逻辑，再决定补不补。

---

## 五、禁止事项

- **禁止**看了实现后把 expected 改成和实现一样。
- **禁止**仅因覆盖率不足就要求「加测试凑数字」。
- **禁止**在报告中删除/修改被测源代码来「提升覆盖率」——只改测试。
- **禁止**用「这个函数太简单」为缺少边界测试开脱。
- **禁止**用 `@Disabled`、`assumeTrue(false)`、注释掉断言来让构建变绿。
- **禁止**为了数字好看而排除业务包、缩小 `-pl` 范围、隐藏失败模块。
- **禁止** mock 被测类本身，或用 `mockStatic` 绕开真实业务逻辑。
- **禁止**用 `Thread.sleep` 等待异步结果。
- **禁止**在未征得用户同意时修改 `pom.xml`、`lombok.config` 或 CI 配置。
- **禁止**把启发式脚本的输出直接当最终结论；必须读代码复核。

---

## 六、命令速查

```powershell
# 环境
$env:JAVA_HOME = (Get-Command java).Source | Split-Path -Parent | Split-Path -Parent
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -v

# 编译 + 测试（全量）
mvn -f server/pom.xml clean test

# 单模块（含依赖）
mvn -f server/pom.xml -pl service -am test

# 覆盖率
mvn -f server/pom.xml org.jacoco:jacoco-maven-plugin:prepare-agent clean test org.jacoco:jacoco-maven-plugin:report
pwsh -File .agents/skills/test-quality-evaluator/scripts/coverage-summary.ps1 -Root server

# 自欺欺人扫描
pwsh -File .agents/skills/test-quality-evaluator/scripts/scan-test-smells.ps1 -Root .

# 集成测试（若使用 *IT.java + Failsafe）
mvn -f server/pom.xml verify

# 变异测试（可选，单模块）
mvn -f server/pom.xml -pl common -am test org.pitest:pitest-maven:1.17.4:mutationCoverage `
  -DtargetClasses='com.hobozoo.aiqq.common.*' -DtargetTests='com.hobozoo.aiqq.common.*Test'
```

---

## 七、支持文件

- `references/java-test-antipatterns.md` — 5 类反模式的 Java 代码对照与正确写法
- `references/maven-coverage-setup.md` — POM 片段、JaCoCo/JUnit/Testcontainers/PIT 配置与排错
- `scripts/coverage-summary.ps1` — 聚合所有模块的 `jacoco.csv`，输出 Markdown 表格
- `scripts/scan-test-smells.ps1` — 按测试方法扫描自欺欺人/精确率信号（启发式）
