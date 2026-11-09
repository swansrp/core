# TDengine 模块开发者快速参考

## 快速上手（5 分钟）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>td</artifactId>
    <version>${jarVersion}</version>
</dependency>
```

### 2. 配置 TDengine

```yaml
td:
  url: jdbc:TAOS-RS://localhost:6041/water_guard
  username: root
  password: ${TD_PASSWORD:taosdata}
  database: water_guard
```

### 3. 定义实体

```java
import com.bidr.td.annotation.*;

@Data
@TdStable("sensor_data")
public class SensorData {
    @TdTimestamp
    private LocalDateTime ts;
    
    @TdColumn(name = "temperature", type = "FLOAT")
    private Float temperature;
    
    @TdTag(name = "station_id")
    private String stationId;
}
```

### 4. 创建 Repository

```java
@Repository
public class SensorDataRepo extends BaseTdRepo<SensorData> {
}
```

### 5. 使用

```java
@Autowired
private SensorDataRepo repo;

// 插入（必须指定子表名）
repo.insertOne(data, "sensor_station_001");

// 查询
TdRangeReq req = new TdRangeReq();
req.setSubTableName("sensor_station_001");
req.setStartTime(LocalDateTime.now().minusHours(1));
req.setEndTime(LocalDateTime.now());
List<SensorData> results = repo.queryRange(req);
```

---

## 常见场景示例

### 场景 1: 批量插入多个子表

```java
List<SensorData> dataList = Arrays.asList(data1, data2, data3);
List<String> subTables = Arrays.asList(
    "sensor_station_001",
    "sensor_station_002",
    "sensor_station_003"
);
repo.insertBatch(dataList, subTables);
```

### 场景 2: 带过滤条件的查询

```java
TdRangeReq req = new TdRangeReq();
req.setSubTableName("sensor_station_001");
req.setStartTime(LocalDateTime.now().minusHours(24));
req.setEndTime(LocalDateTime.now());

// 添加过滤条件
AdvancedQuery query = new AdvancedQuery();
query.setConditions(Arrays.asList(
    ConditionVO.builder()
        .column("temperature")
        .operator(">")
        .value(25.0)
        .build()
));
req.setQuery(query);

List<SensorData> results = repo.queryRange(req);
```

### 场景 3: 聚合降采样

```java
TdIntervalReq req = new TdIntervalReq();
req.setSubTableName("sensor_station_001");
req.setStartTime(LocalDateTime.now().minusDays(7));
req.setEndTime(LocalDateTime.now());
req.setInterval("1h");  // 按 1 小时间隔
req.setAggrFuncs(Arrays.asList("AVG", "MAX", "MIN"));
req.setColumns(Arrays.asList("temperature", "humidity"));

List<Map<String, Object>> results = repo.queryInterval(req);
```

### 场景 4: 最新数据查询

```java
TdLastReq req = new TdLastReq();
req.setStableName("sensor_data");
req.setTags(Arrays.asList("station_001", "station_002"));

List<SensorData> lastData = repo.queryLast(req);
```

### 场景 5: TopN 查询

```java
TdTopNReq req = new TdTopNReq();
req.setStableName("sensor_data");
req.setOrderByColumn("temperature");
req.setOrder("DESC");
req.setLimit(10);

List<SensorData> topData = repo.queryTopN(req);
```

### 场景 6: 按标签分组聚合

```java
TdGroupReq req = new TdGroupReq();
req.setStableName("sensor_data");
req.setGroupByTag("station_id");
req.setAggrFuncs(Arrays.asList("AVG", "COUNT"));
req.setColumns(Arrays.asList("temperature"));
req.setStartTime(LocalDateTime.now().minusHours(24));
req.setEndTime(LocalDateTime.now());

List<Map<String, Object>> results = repo.queryGroupByTag(req);
```

---

## 关键约束

### ✅ 必须做的

1. **写入时指定子表名**
   ```java
   repo.insertOne(data, "sensor_station_001");  // ✅ 正确
   repo.insertOne(data);  // ❌ 错误，缺少子表名
   ```

2. **实体类使用注解**
   ```java
   @TdStable("table_name")  // ✅ 必须
   public class MyEntity {
       @TdTimestamp  // ✅ 必须有时间戳
       private LocalDateTime ts;
   }
   ```

3. **Repository 继承 BaseTdRepo**
   ```java
   @Repository
   public class MyRepo extends BaseTdRepo<MyEntity> {  // ✅ 正确
   }
   ```

### ❌ 不能做的

1. **不能隐式创建子表**
   - 框架不会自动创建子表，需自行管理

2. **不能使用未授权的聚合函数**
   - 只允许白名单中的函数：AVG, SUM, MAX, MIN, COUNT 等

3. **不能跳过 SQL 注入校验**
   - 所有标识符都会经过白名单校验

---

## 配置项说明

| 配置项 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `td.url` | JDBC 连接串 | - | ✅ |
| `td.username` | 用户名 | - | ✅ |
| `td.password` | 密码 | - | ✅ |
| `td.database` | 数据库名 | - | ✅ |
| `td.precision` | 时间精度 | ms | ❌ |
| `td.keep-days` | 保留天数 | 3650 | ❌ |
| `td.pool.initial-size` | 连接池初始大小 | 5 | ❌ |
| `td.pool.max-active` | 连接池最大活跃数 | 20 | ❌ |
| `td.schema.auto-drop` | 自动删除废弃列 | false | ❌ |

---

## 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `TD_PASSWORD` | TDengine 密码 | taosdata |
| `TD_URL` | TDengine 连接串 | jdbc:TAOS-RS://localhost:6041/water_guard |

---

## 常见问题排查

### Q1: 插入失败，提示子表不存在

**原因**: 子表未创建  
**解决**: TDengine 需要先创建子表，可以使用 `CREATE TABLE IF NOT EXISTS` 语句

### Q2: Schema 同步失败

**原因**: DDL 执行错误  
**解决**: 
1. 查看 `td_sync_log` 表中的错误日志
2. 检查 TDengine 连接权限
3. 手动执行 DDL 语句

### Q3: 查询返回空结果

**原因**: 可能是时间范围错误或过滤条件过严  
**解决**:
1. 检查 startTime 和 endTime 是否正确
2. 检查过滤条件是否合理
3. 使用简单的范围查询测试

### Q4: 聚合函数报错

**原因**: 函数不在白名单中  
**解决**: 检查使用的聚合函数是否在 `ALLOWED_AGGR_FUNCS` 中

### Q5: SQL 注入校验失败

**原因**: 表名/列名包含非法字符  
**解决**: 使用合法的标识符（字母、数字、下划线）

---

## 性能优化建议

### 1. 批量插入优化

```java
// ✅ 推荐：按子表分组后批量插入
Map<String, List<SensorData>> grouped = dataList.stream()
    .collect(Collectors.groupingBy(this::getSubTableName));

grouped.forEach((subTable, entities) -> {
    repo.insertBatch(entities, Collections.nCopies(entities.size(), subTable));
});
```

### 2. 查询性能优化

```java
// ✅ 推荐：使用标签过滤减少扫描范围
TdRangeReq req = new TdRangeReq();
req.setSubTableName("sensor_station_001");  // 指定子表

// ✅ 推荐：限制时间范围
req.setStartTime(LocalDateTime.now().minusHours(1));
req.setEndTime(LocalDateTime.now());

// ✅ 推荐：指定返回列
req.setColumns(Arrays.asList("ts", "temperature"));
```

### 3. 连接池调优

```yaml
td:
  pool:
    initial-size: 10    # 根据并发量调整
    max-active: 50      # 根据并发量调整
```

---

## 测试指南

### 运行单元测试（不需要 TDengine 服务）

```bash
cd server/core/td
mvn test -Pdevelopment

# 或从 server 目录
mvn test -pl core/td -Pdevelopment
```

### 运行集成测试（需要真实 TDengine 服务）

```bash
# 运行全部集成测试
mvn verify -pl core/td -Pdevelopment

# 运行单个测试类
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdConnectionIT"
```

### 单元测试类列表

| 测试类 | 说明 |
|--------|------|
| `BaseTdControllerMvcTest` | Controller MVC 测试 |
| `BaseTdRepoInsertTest` | 插入操作测试 |
| `BaseTdRepoQueryTest` | 查询操作测试 |
| `BaseTdSchemaTest` | Schema 同步测试 |
| `TdSchemaDifferTest` | 差异对比器测试 |
| `TdAdvancedQueryParserTest` | 查询解析器测试 |
| `TagSyncInterceptorTest` | Tag 同步拦截器测试 |
| `TaosJdbcTemplateTest` | JDBC 模板测试 |
| `TdTagMappingControllerTest` | Tag 映射 Controller 测试 |
| `TdTagMappingSchemaTest` | Tag 映射 Schema 测试 |
| `TdSyncLogRetryTest` | 同步日志重试测试 |

### 集成测试类列表

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `TdConnectionIT` | 3 | 连接验证、数据库存在、版本号检查 |
| `TdSchemaIT` | 5 | 注解建表、幂等性、DDL 生成、手动 DDL |
| `TdRepoCrudIT` | 7 | 单条/批量插入、子表生命周期、tag 修改 |
| `TdRepoQueryIT` | 11 | 范围查询、分页、聚合、窗口、TopN、LAST_ROW |
| `TdSchemaDifferIT` | 4 | Schema Diff（缺列/缺 tag/完全匹配） |

### 集成测试架构说明

- **不使用 `@SpringBootTest`**：手动构建 `JdbcTemplate`（HikariCP + RestfulDriver），避免 kernel 模块干扰
- **独立数据库**：使用 `water_itest` 与生产数据库隔离
- **三级清理策略**：
  - `AbstractTdIT.cleanupChildTables()`：默认仅删子表（CRUD 测试用）
  - `TdSchemaIT/TdSchemaDifferIT.cleanupFull()`：删除子表 + STABLE（Schema 测试用）
  - `TdRepoQueryIT.cleanupChildTables()`：空操作，保留预置数据（查询测试用）
- **资源管理**：`HikariDataSource` 用 List 追踪，`@AfterAll` 统一关闭

---

## 更多资源

- **完整文档**: [README.md](../README.md)
- **架构决策**: [ARCHITECTURE_DECISIONS.md](./ARCHITECTURE_DECISIONS.md)
- **变更日志**: [CHANGELOG.md](../../../../CHANGELOG.md)
- **TDengine 官方文档**: https://docs.taosdata.com/

---

## 需要帮助？

如遇问题，请：
1. 先查看本文档的常见问题排查部分
2. 检查完整文档 README.md
3. 查看测试用例获取使用示例
4. 联系模块维护者
