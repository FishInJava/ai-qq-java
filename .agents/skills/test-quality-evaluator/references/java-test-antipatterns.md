# Java 测试反模式对照（JUnit 5 / AssertJ / Mockito）

本文件配合 `SKILL.md` 的「自欺欺人程度」与「精确率」使用。
每条都给出「AI 常写的错误版本」和「能发现真实 bug 的版本」。

---

## 1. 预期值倒推（权重 +30%）

### 错误：看完实现再抄 expected

```java
// 实现：return a * b;
@Test
void multiply() {
    assertEquals(15, calculator.multiply(3, 5)); // 15 是从 return a*b 抄来的
}
```

如果实现被悄悄改成 `return a + b`，这个用例仍然可能通过（换成另一组数也一样，
因为 expected 本来就来自实现）。

### 正确：从契约推导，再验证实现

```java
// 先看方法名 / Javadoc / 调用方，确认「乘法」语义，独立算出期望值
@Test
void multiply_shouldReturnProduct() {
    assertThat(calculator.multiply(4, 7)).isEqualTo(28);
    assertThat(calculator.multiply(-3, 5)).isEqualTo(-15);
    assertThat(calculator.multiply(0, 99)).isZero();
}
```

### 更隐蔽的变体

```java
// 同义反复：expected 和 actual 都由被测代码产生
assertEquals(service.calculate(order), service.calculate(order));

// 用被测工具类生成 expected
assertEquals(JsonUtil.toJson(expected), JsonUtil.toJson(actual));
```

正确做法是写**字面量**或**独立构造的 fixture**：

```java
assertThat(JsonUtil.toJson(user))
    .isEqualTo("{\"id\":1,\"name\":\"Alice\"}");
```

### 本项目重点

- `DateUtil`：expected 必须是手写的日期字符串/时间戳，而不是再用 `DateUtil` 算一遍。
- `JsonUtil`：expected 用字面量 JSON，顺便验证字段顺序/空值策略是否符合契约。
- `CacheUtil`：expected 用独立构造的对象，验证 TTL/覆盖/并发语义。

---

## 2. mock 万能（权重 +25%）

### 错误：只验证「调用了」

```java
@Test
void loadUser() {
    when(userDao.findById(1L)).thenReturn(userDo);
    userService.loadUser(1L);
    verify(userDao).findById(1L); // 到此为止
}
```

`DO → BO` 映射写错、`null` 处理丢失、异常被吞掉，这个测试都不会失败。

### 正确：mock 之后验证真实结果

```java
@Test
void loadUser_shouldMapDoToBo() {
    UserDo userDo = new UserDo();
    userDo.setId(1L);
    userDo.setNickname("Alice");
    when(userDao.findById(1L)).thenReturn(userDo);

    UserBo bo = userService.loadUser(1L);

    assertThat(bo.getId()).isEqualTo(1L);
    assertThat(bo.getDisplayName()).isEqualTo("Alice");
    verify(userDao).findById(1L);
}

@Test
void loadUser_whenMissing_shouldThrowNotFound() {
    when(userDao.findById(9L)).thenReturn(null);

    assertThatThrownBy(() -> userService.loadUser(9L))
        .isInstanceOf(AiqqException.class)
        .hasMessageContaining("not found");
}
```

### 用 ArgumentCaptor 验证「写出去的数据」

```java
@Test
void createOrder_shouldPersistNormalizedAmount() {
    when(orderDao.insert(any())).thenReturn(1);

    orderService.create(new OrderBo(null, new BigDecimal("10.005")));

    ArgumentCaptor<OrderDo> captor = ArgumentCaptor.forClass(OrderDo.class);
    verify(orderDao).insert(captor.capture());

    assertThat(captor.getValue().getAmount())
        .isEqualByComparingTo("10.01"); // 验证四舍五入，而不只是「调用过」
    assertThat(captor.getValue().getCreatedAt()).isNotNull();
}
```

### 本项目重点

- `service` mock `dao` 后，必须断言 BO 的字段，而不是只 `verify(dao)`。
- `manager` 封装第三方接口时，mock HTTP 客户端后要断言**解析/映射后的 DTO**。
- 不要 mock 被测类自身；不要用 `mockStatic` 绕开真正要测的逻辑。

---

## 3. happy path 依赖（权重 +20%）

### 错误：只有正常值

```java
@Test
void parseDate() {
    assertEquals(LocalDate.of(2026, 10, 29), DateUtil.parse("2026-10-29"));
}
```

### 正确：每个公开方法至少一个边界 + 一个异常路径

```java
@ParameterizedTest
@ValueSource(strings = {"2024-02-29", "2026-01-01", "1970-01-01"})
void parseDate_validBoundaries(String input) {
    assertThat(DateUtil.parse(input)).isNotNull();
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {" ", "2026-13-01", "2026-02-30", "not-a-date", "2026/10/29"})
void parseDate_invalid_shouldThrow(String input) {
    assertThatThrownBy(() -> DateUtil.parse(input))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void parseDate_leapYear() {
    assertThat(DateUtil.parse("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29));
    assertThatThrownBy(() -> DateUtil.parse("2025-02-29"))
        .isInstanceOf(IllegalArgumentException.class);
}
```

### Java 边界清单

| 类型 | 必测值 |
|---|---|
| 引用类型 | `null`、未初始化字段、空对象 |
| String | `""`、`" "`、超长、Unicode/emoji、前后空白、非法格式 |
| 数值 | `0`、`-1`、`Integer/Long.MIN_VALUE`、`MAX_VALUE`、溢出、`BigDecimal` 精度/scale |
| 集合 | 空集合、单元素、重复元素、`null` 元素、超大集合 |
| Optional | `Optional.empty()`、`null` 返回值（不要混用两种空语义） |
| 日期时间 | 闰年、月末、跨年、时区/夏令时、`LocalDate.MIN/MAX` |
| 分页 | `page=0`、`page<0`、`size=0`、越界页、超大 size |
| 并发 | 中断、超时、竞态（用 `CountDownLatch`/`Awaitility`，不要 `sleep`） |

### 本项目重点

- `DateUtil`：闰年、月末、时区、非法格式、`null`。
- `JsonUtil`：非法 JSON、空串、`null`、泛型反序列化、未知字段、循环引用。
- `CacheUtil`：过期、容量上限、并发写入、key 为 `null`。
- `ThreadUtil` / `CompletableFuture` 示例：超时、异常完成、中断。

---

## 4. 实现耦合（权重 +15%）

### 错误：断言内部实现

```java
@Test
void cacheHit() {
    cacheService.get("k");
    assertEquals(1, ReflectionTestUtils.getField(cacheService, "hitCount"));
    verify(cacheService, times(1)).loadFromDb("k"); // 锁死内部调用顺序
}
```

重构（例如把命中计数换成 Micrometer）后行为不变，测试却挂了。

### 正确：断言公开行为

```java
@Test
void cacheHit_shouldReturnCachedValueWithoutSecondLoad() {
    when(loader.load("k")).thenReturn("v");

    assertThat(cacheService.get("k")).isEqualTo("v");
    assertThat(cacheService.get("k")).isEqualTo("v");

    verify(loader, times(1)).load("k"); // 这里验证的是「只加载一次」这一业务约束
}
```

判断标准：**行为不变的重构是否会让测试挂掉？** 会挂，就是耦合。

### 常见耦合信号

- `ReflectionTestUtils.setField/getField`、`getDeclaredField`、`setAccessible`。
- `verify(mock, times(n)).privateMethod(...)` / `inOrder(...)` 锁调用顺序。
- 断言具体 SQL 字符串、日志文本、异常消息全文（应断言异常类型 + 关键片段）。
- 断言缓存内部 `Map` 的 size/内容，而不是通过公开 API 观察命中/过期。
- 用 `@VisibleForTesting` 把私有方法暴露出来专门测。

---

## 5. 占位填充（权重 +10%）

### 错误

```java
@Test
void placeholder() {
    assertTrue(true);
}

@Test
void todo() {
    // TODO: add real test
}

@Disabled("flaky")
@Test
void disabledTest() { ... }

@Test
void noAssertion() {
    service.doWork(); // 没有任何断言
}
```

### 正确

- 删除占位测试，或改写成真实断言。
- `@Disabled` 必须有 issue 链接和修复计划；长期禁用的测试等于没有。
- 异步/不稳定测试用 `Awaitility`，不要禁用。
- 测试方法至少有一个与业务结果相关的断言。

---

## 6. Java 额外气味（不参与加权，写入「其他风险」）

| 气味 | 为什么危险 | 替代 |
|---|---|---|
| 只断言 `assertNotNull` | 字段映射错了也能过 | 断言关键字段值 |
| `try { ...; fail(); } catch (Exception ignored) {}` | 吞掉所有异常 | `assertThrows` / `assertThatThrownBy` |
| `Thread.sleep(1000)` | 慢、随机失败、掩盖竞态 | `Awaitility`、`CountDownLatch`、`CompletableFuture.get(timeout)` |
| `@SpringBootTest` 测纯函数 | 慢、依赖 DB、掩盖设计问题 | 普通 JUnit + Mockito |
| 只断言 HTTP 200 | 响应体可能全错 | `MockMvcTester` 断言 JSON 字段 |
| `@Transactional` 测试 | 永远回滚，验证不到提交/唯一约束 | 单独集成测试 + 显式清理，或 Testcontainers |
| 测 Lombok getter/setter | 覆盖率高、价值为零 | 测业务方法，不测生成的访问器 |
| 依赖 `LocalDate.now()` | 跨天/时区随机失败 | 注入 `Clock` |
| `mockStatic` 滥用 | 把要测的逻辑也 mock 掉 | 提取可注入的接口/函数 |
| `lenient()` / `Mockito.reset()` | 掩盖多余 stub 和测试间污染 | 每个测试独立 fixture |
| 断言具体异常消息全文 | 改文案就挂 | `hasMessageContaining` + 异常类型 |
| 测试只能按固定顺序跑 | 单独跑就失败 | 每个测试自建数据、互不依赖 |

---

## 7. 提交前的自检清单

- [ ] 每个新增 expected 都能从业务语义独立推导，不是从实现抄的。
- [ ] mock 之后验证了返回值/映射/异常/持久化副作用，而不只是 `verify`。
- [ ] 每个公开方法至少有 1 个 happy path、1 个边界、1 个异常路径。
- [ ] 断言只依赖公开 API，行为不变的重构不会挂。
- [ ] 没有 `assertTrue(true)`、空测试、`@Disabled`、`Thread.sleep`。
- [ ] 新增测试在「实现被改错」时确实会失败（可以手动改一行实现验证，随后还原）。
- [ ] 覆盖率提升来自关键逻辑，而不是 DTO/Lombok/配置类。
