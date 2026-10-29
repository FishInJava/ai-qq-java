# Maven / JUnit 5 / JaCoCo 配置与排错

配套 `SKILL.md` 使用。所有 POM 修改都必须先征得用户同意。

---

## 1. 环境预检

本机（2026-10-29）`JAVA_HOME` 指向 Java 8，而项目要求 Java 21：

```powershell
$env:JAVA_HOME = (Get-Command java).Source | Split-Path -Parent | Split-Path -Parent
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -v
# 期望：Java version: 21.x
```

若 PATH 上的 `java` 不是 21，显式指定：

```powershell
$env:JAVA_HOME = 'G:\JetBrains\jdk-21.0.10'   # 按实际安装位置调整
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

失败症状：`invalid target release: 21` / `release version 21 not supported`。

---

## 2. 添加测试依赖（当前仓库缺失）

在 `server/pom.xml` 的 `<dependencies>` 中加入，所有模块共享：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

它包含：JUnit 5 Jupiter、AssertJ、Mockito、JSONassert、Spring Test。
版本由 `spring-boot-starter-parent` 管理，不要手写版本。

如果只希望在特定模块使用，把依赖放到该模块的 `pom.xml`。
`common` 模块只有 `spring-core`，加 `starter-test` 也能正常跑纯单元测试。

---

## 3. JaCoCo

### 方式 A：不改 POM，命令行直接调（评估时推荐）

```powershell
mvn -f server/pom.xml `
  org.jacoco:jacoco-maven-plugin:prepare-agent clean test `
  org.jacoco:jacoco-maven-plugin:report
```

- 版本由 Spring Boot 父 POM 的 `pluginManagement` 提供；若报
  “plugin version not specified”，改成
  `org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent`（以 Maven Central 最新为准）。
- `prepare-agent` 必须在 `test` 之前，否则 `jacoco.exec` 为空。
- 单模块：`mvn -f server/pom.xml -pl service -am ...`（`-pl` 相对 `server/`）。

### 方式 B：写入 POM（长期使用）

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

注意：

- 阈值按模块配置；`entity`、启动类等不应拉高/拉低核心模块的门槛。
- 如果 Surefire 已自定义 `<argLine>`，JaCoCo 会覆盖它；需要写成
  `<argLine>@{argLine} 你的参数</argLine>`，否则 Java 8+ 的 `--add-opens` 等参数会丢失。
- 排除项要谨慎，禁止用排除业务包的方式美化数字：

```xml
<configuration>
    <excludes>
        <exclude>**/entity/**</exclude>
        <exclude>**/*Application.class</exclude>
        <exclude>**/*Config.class</exclude>
    </excludes>
</configuration>
```

### 读取报告

```text
server/<module>/target/site/jacoco/
├── jacoco.csv     ← 机器可读
├── jacoco.xml     ← CI / Sonar
└── html/index.html
```

聚合脚本：

```powershell
pwsh -File .opencode/skills/test-quality-evaluator/scripts/coverage-summary.ps1 -Root server
```

### Lombok

`entity` / DTO 大量使用 Lombok，生成方法会稀释或扭曲覆盖率。建议在
`server/lombok.config` 加入（改配置前征得同意）：

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
```

JaCoCo 会自动忽略带 `@lombok.Generated` 的方法。

---

## 4. 测试分层与命名

| 类型 | 命名 | 运行方式 | 说明 |
|---|---|---|---|
| 单元测试 | `*Test.java` | Surefire / `mvn test` | 纯 JUnit + Mockito，毫秒级 |
| 切片测试 | `*Test.java` | `mvn test` | `@WebMvcTest` / `@MybatisTest` |
| 集成测试 | `*IT.java` | Failsafe / `mvn verify` | 真实 DB/容器，较慢 |
| 架构测试 | `*Test.java` | `mvn test` | ArchUnit（可选） |

当前仓库没有 Failsafe 插件，`*IT.java` 不会自动执行；需要时再加
`maven-failsafe-plugin`。

### Spring Boot 4 注意事项

- `@MockBean` / `@SpyBean` **已移除**，改用
  `org.springframework.test.context.bean.override.mockito.@MockitoBean` /
  `@MockitoSpyBean`。
- Controller 优先 `@WebMvcTest(DemoController.class)` + `MockMvc`（或 Spring 6.2+
  的 `MockMvcTester`）：

```java
@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void ping_shouldReturnPong() throws Exception {
        mvc.perform(post("/demo/ping")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ping\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("pong"));
    }
}
```

- 不要默认用 `@SpringBootTest`：`controller` 的配置默认连本机 MySQL，
  CI/本机没库会直接启动失败。优先切片测试或 Testcontainers。
- DAO 用 `@MybatisTest`（需要 `mybatis-spring-boot-starter-test`）：

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter-test</artifactId>
    <version>4.1.0</version>
    <scope>test</scope>
</dependency>
```

- MySQL 集成测试优先 Testcontainers，而不是 H2（方言/函数差异会掩盖真实 SQL 错误）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@Testcontainers
@SpringBootTest
class DemoDaoIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
}
```

---

## 5. PIT 变异测试（可选，精确率参考）

```powershell
mvn -f server/pom.xml -pl common -am test `
  org.pitest:pitest-maven:1.17.4:mutationCoverage `
  -DtargetClasses='com.hobozoo.aiqq.common.*' `
  -DtargetTests='com.hobozoo.aiqq.common.*Test' `
  -DoutputFormats=HTML,XML
```

- 版本以 Maven Central 最新稳定版为准。
- 只对纯逻辑模块跑；Spring 上下文测试会让 PIT 极慢。
- mutation score 解读：`<50%` 测试基本抓不住 bug；`60–80%` 可用；
  `>80%` 较好。它**不能**替代三维评分，只作为精确率的客观佐证。

---

## 6. 常见问题

| 症状 | 原因 | 处理 |
|---|---|---|
| `release version 21 not supported` | Maven 用了 JDK 8 | 修正 `JAVA_HOME`，见第 1 节 |
| `No tests to run` / 0 tests | 命名或路径不符 | 测试放 `src/test/java`，类名 `*Test`；`mvn -X` 查看 Surefire 扫描 |
| `jacoco.exec` 为空 | `prepare-agent` 在 `test` 之后，或 `argLine` 被覆盖 | 调整命令顺序；POM 用 `@{argLine}` |
| 报告里没有某个模块 | 该模块没执行 `test` | `-pl <module> -am`，或全量构建 |
| `@MockBean` 编译不过 | Boot 4 已移除 | 改 `@MockitoBean` / `@MockitoSpyBean` |
| `Failed to load ApplicationContext ... datasource` | `@SpringBootTest` 连 MySQL 失败 | 用切片测试、test profile 或 Testcontainers，不要禁用测试 |
| 测试本地过、CI 挂 | 时区/locale/端口/并发 | 固定 `-Duser.timezone=UTC -Duser.language=en`；随机端口；避免共享状态 |
| JUnit 4/5 混用报 `NoSuchMethodError` | 同时引入 vintage/jupiter 且版本冲突 | 统一 Jupiter；确需 JUnit 4 才加 vintage |
| 中文注释乱码 | 源文件编码不是 UTF-8 | 源文件保存为 UTF-8；父 POM 已设 `project.build.sourceEncoding=UTF-8` |
| 测试通过但业务有 bug | 断言只覆盖 mock 交互/正常路径 | 回到 `SKILL.md` Step 3/4，按反模式逐条复核 |

---

## 7. 评估命令清单

```powershell
# 0) 环境
$env:JAVA_HOME = (Get-Command java).Source | Split-Path -Parent | Split-Path -Parent
mvn -v

# 1) 测试
mvn -f server/pom.xml clean test

# 2) 覆盖率
mvn -f server/pom.xml org.jacoco:jacoco-maven-plugin:prepare-agent clean test org.jacoco:jacoco-maven-plugin:report
pwsh -File .opencode/skills/test-quality-evaluator/scripts/coverage-summary.ps1 -Root server

# 3) 反模式扫描
pwsh -File .opencode/skills/test-quality-evaluator/scripts/scan-test-smells.ps1 -Root .

# 4) 集成测试（如有 *IT.java 且配置了 Failsafe）
mvn -f server/pom.xml verify

# 5) 变异测试（可选）
mvn -f server/pom.xml -pl common -am test org.pitest:pitest-maven:1.17.4:mutationCoverage `
  -DtargetClasses='com.hobozoo.aiqq.common.*' -DtargetTests='com.hobozoo.aiqq.common.*Test'
```
