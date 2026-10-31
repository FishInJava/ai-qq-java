# ai-qq

AI 问答项目后端。Spring Boot 4 / Java 21 多模块工程，仓库根只放 `server/`，前端将放在同级目录。

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 4.1.2（Spring Framework 7.0.10） |
| MyBatis | mybatis-spring-boot-starter 4.1.0 |
| 接口文档 | springdoc-openapi 3.2.0 |
| 数据库 | MySQL（mysql-connector-j） |
| 其他 | Lombok 1.18.48、Guava 33.7.2-jre |

## 目录

```
server/
├── pom.xml        聚合 + parent（继承 spring-boot-starter-parent）
├── common/        通用工具、异常
├── entity/        DO / DTO
├── dao/           MyBatis 持久层
├── manager/       DAO 与第三方接口的封装
├── interface/     第三方接口定义
├── service/       业务层
├── controller/    HTTP 接口层，唯一可启动模块
├── mq/            消息消费
├── quartz/        定时任务
├── rpc/           RPC 接口
└── web/           对外门面
```

`interface`、`manager`、`quartz`、`rpc`、`web` 目前只有骨架，无源码。

## 构建与运行

```bash
cd server
mvn clean package
java -jar controller/target/controller-1.0-SNAPSHOT.jar
```

数据源通过环境变量注入，默认指向本机：

| 变量 | 默认值 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/school?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | 空 |

## 测试

```bash
# 仓库根目录执行
mvn -f server/pom.xml test
```

覆盖率报告生成在各模块 `target/site/jacoco/index.html`，聚合查看：

```powershell
pwsh -File .agents/skills/test-quality-evaluator/scripts/coverage-summary.ps1 -Root server
```

测试与 JaCoCo 已配置在 `server/pom.xml`，新增模块无需重复声明。
运行前确认 `mvn -v` 使用 JDK 21（`JAVA_HOME` 与 PATH 上的 `java` 可能不一致，以 `mvn -v` 为准）。

## 换行符与跨平台

Windows / Linux / macOS 均可开发，换行符策略由仓库根 `.gitattributes` 固定，不依赖个人 `core.autocrlf` 设置：

| 类型 | 库内存储 | 检出 |
|---|---|---|
| 源码、配置、文档 | LF | LF |
| `.bat` / `.cmd` / `.ps1` | LF | CRLF |
| `.jar`、图片等二进制 | 原样 | 原样 |

老仓库首次应用该策略后，如需让工作区与属性一致，执行一次 `git add --renormalize .`。

## 接口

| 路径 | 说明 |
|---|---|
| `POST /demo/ping` | 连通测试，`{"name":"ping"}` → `{"name":"pong"}` |
| `GET /v3/api-docs` | OpenAPI 3.1 描述 |
| `GET /swagger-ui.html` | Swagger UI |

## 当前状态

- 构建、启动、`/demo/ping` 已验证通过
- 持久层尚未装配：`DemoDao` / `DemoMapper` 未注册为 Bean，`mapper/demo.xml` 为空
- `service` 模块目前只有并发示例代码，无业务实现
- `mq` 模块的 `RetryAspect` 切点为空表达式，接入应用前需修复
