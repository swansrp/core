# TDengine 时序数据模块

`td` 模块是 Bidr Core 的 TDengine 时序数据基础功能框架，提供超级表管理、数据读写、Schema 自动同步等能力。

## 目录结构

```
td/
├── src/main/java/com/bidr/td/
│   ├── annotation/          # 注解定义
│   │   ├── TdStable.java    # 超级表注解
│   │   ├── TdColumn.java    # 列字段注解
│   │   ├── TdTag.java       # 标签字段注解
│   │   └── TdTimestamp.java # 时间戳注解
│   ├── config/              # 配置类
│   │   ├── TdDataSourceConfig.java
│   │   └── TdProperties.java
│   ├── constant/            # 常量定义
│   │   ├── TdDataType.java
│   │   └── TdErrCode.java
│   ├── controller/          # REST Controller
│   │   ├── BaseTdController.java
│   │   └── TdTagMappingController.java
│   ├── dao/                 # MySQL 配套表（platform 风格）
│   │   ├── entity/          #   实体类
│   │   ├── mapper/          #   Mapper 接口
│   │   ├── repository/      #   Repository 服务
│   │   └── schema/          #   Schema 定义
│   ├── inf/                 # 接口定义
│   │   ├── TdControllerInf.java
│   │   └── TdSchemaInf.java
│   ├── repository/          # 数据访问层
│   │   ├── BaseTdRepo.java
│   │   └── BaseTdSchema.java
│   ├── sync/                # Tag 同步框架
│   │   ├── TagSyncInterceptor.java
│   │   ├── TdAdvancedQueryParser.java
│   │   ├── TdSchemaDiffer.java
│   │   └── TdSyncService.java
│   └── vo/                  # 值对象
│       ├── TdRangeReq.java
│       ├── TdLastReq.java
│       ├── TdIntervalReq.java
│       ├── TdTopNReq.java
│       ├── TdGroupReq.java
│       ├── TdInsertMultiBatchReq.java
│       └── TdAdvancedReq.java
└── src/main/resources/config/
    └── application-td.yml   # 配置文件
```

## 快速开始

### 1. 引入依赖

在业务模块的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>td</artifactId>
    <version>${jarVersion}</version>
</dependency>
```

### 2. 配置 TDengine

在 `application.yml` 或 `application-td.yml` 中配置：

```yaml
td:
  url: jdbc:TAOS-RS://localhost:6041/water_guard
  username: root
  password: ${TD_PASSWORD:taosdata}  # 支持环境变量覆盖
  database: water_guard
  precision: ms
  keep-days: 3650
  pool:
    initial-size: 5
    max-active: 20
  schema:
    auto-drop: false  # 是否自动删除废弃列
```

### 3. 定义超级表实体

使用注解定义 TDengine 超级表结构：

```java
import com.bidr.td.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TdStable("sensor_data")
public class SensorDataEntity {

    @TdTimestamp
    private LocalDateTime ts;

    @TdColumn(name = "temperature", type = "FLOAT")
    private Float temperature;

    @TdColumn(name = "humidity", type = "FLOAT")
    private Float humidity;

    @TdColumn(name = "ph_value", type = "FLOAT")
    private Float phValue;

    @TdColumn(name = "dissolved_oxygen", type = "FLOAT")
    private Float dissolvedOxygen;

    @TdTag(name = "station_id")
    private String stationId;

    @TdTag(name = "sensor_type")
    private String sensorType;
}
```

### 4. 创建 Repository

继承 `BaseTdRepo` 并指定实体类型：

```java
import com.bidr.td.repository.BaseTdRepo;
import org.springframework.stereotype.Repository;

@Repository
public class SensorDataRepo extends BaseTdRepo<SensorDataEntity> {
    // 继承所有基础 API
}
```

### 5. 创建 Controller

继承 `BaseTdController` 快速暴露 REST 端点：

```java
import com.bidr.td.controller.BaseTdController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensor")
public class SensorDataController extends BaseTdController<SensorDataEntity> {
    
    public SensorDataController(SensorDataRepo repo) {
        super(repo);
    }
    
    @Override
    protected String getPrefix() {
        return "sensor";
    }
}
```

## 使用示例

### 数据写入

#### 单条插入

```java
@Autowired
private SensorDataRepo repo;

public void insertData() {
    SensorDataEntity data = new SensorDataEntity();
    data.setTs(LocalDateTime.now());
    data.setTemperature(25.5f);
    data.setHumidity(60.2f);
    data.setPhValue(7.2f);
    data.setDissolvedOxygen(8.5f);
    data.setStationId("station_001");
    data.setSensorType("water_quality");
    
    // 必须指定子表名
    repo.insertOne(data, "sensor_station_001");
}
```

#### 批量插入

```java
public void insertBatch() {
    List<SensorDataEntity> dataList = Arrays.asList(
        createEntity("station_001", 25.5f),
        createEntity("station_002", 26.1f),
        createEntity("station_003", 24.8f)
    );
    
    // 多子表批量插入
    repo.insertBatch(dataList, Arrays.asList(
        "sensor_station_001",
        "sensor_station_002",
        "sensor_station_003"
    ));
}
```

### 数据查询

#### 时间范围查询

```java
import com.bidr.td.vo.TdRangeReq;
import com.bidr.kernel.model.query.AdvancedQuery;

public void queryRange() {
    TdRangeReq req = new TdRangeReq();
    req.setSubTableName("sensor_station_001");
    req.setStartTime(LocalDateTime.now().minusHours(1));
    req.setEndTime(LocalDateTime.now());
    
    // 可选：添加过滤条件
    AdvancedQuery query = new AdvancedQuery();
    // ... 设置查询条件
    req.setQuery(query);
    
    List<SensorDataEntity> results = repo.queryRange(req);
}
```

#### 最新数据查询

```java
import com.bidr.td.vo.TdLastReq;

public void queryLast() {
    TdLastReq req = new TdLastReq();
    req.setStableName("sensor_data");
    req.setTags(Arrays.asList("station_001", "station_002"));
    
    List<SensorDataEntity> lastData = repo.queryLast(req);
}
```

#### 聚合降采样查询

```java
import com.bidr.td.vo.TdIntervalReq;

public void queryInterval() {
    TdIntervalReq req = new TdIntervalReq();
    req.setSubTableName("sensor_station_001");
    req.setStartTime(LocalDateTime.now().minusDays(1));
    req.setEndTime(LocalDateTime.now());
    req.setInterval("1h");  // 按 1 小时间隔
    req.setAggrFuncs(Arrays.asList("AVG", "MAX", "MIN"));
    req.setColumns(Arrays.asList("temperature", "humidity"));
    
    List<Map<String, Object>> results = repo.queryInterval(req);
}
```

#### TopN 查询

```java
import com.bidr.td.vo.TdTopNReq;

public void queryTopN() {
    TdTopNReq req = new TdTopNReq();
    req.setStableName("sensor_data");
    req.setOrderByColumn("temperature");
    req.setOrder("DESC");
    req.setLimit(10);
    
    List<SensorDataEntity> topData = repo.queryTopN(req);
}
```

#### 按标签分组查询

```java
import com.bidr.td.vo.TdGroupReq;

public void queryGroupByTag() {
    TdGroupReq req = new TdGroupReq();
    req.setStableName("sensor_data");
    req.setGroupByTag("station_id");
    req.setAggrFuncs(Arrays.asList("AVG", "COUNT"));
    req.setColumns(Arrays.asList("temperature"));
    req.setStartTime(LocalDateTime.now().minusHours(24));
    req.setEndTime(LocalDateTime.now());
    
    List<Map<String, Object>> results = repo.queryGroupByTag(req);
}
```

### REST API 调用

Controller 自动暴露以下端点（以 `sensor` 为例）：

```bash
# 插入数据
POST /api/sensor/insert
{
  "subTableName": "sensor_station_001",
  "data": { ... }
}

# 批量插入
POST /api/sensor/insertBatch
{
  "dataList": [ ... ]
}

# 时间范围查询
POST /api/sensor/queryRange
{
  "subTableName": "sensor_station_001",
  "startTime": "2026-05-11T00:00:00",
  "endTime": "2026-05-11T23:59:59",
  "query": { ... }
}

# 最新数据
POST /api/sensor/queryLast
{
  "stableName": "sensor_data",
  "tags": ["station_001"]
}

# 聚合降采样
POST /api/sensor/queryInterval
{
  "subTableName": "sensor_station_001",
  "startTime": "...",
  "endTime": "...",
  "interval": "1h",
  "aggrFuncs": ["AVG", "MAX", "MIN"],
  "columns": ["temperature", "humidity"]
}

# TopN
POST /api/sensor/queryTopN
{
  "stableName": "sensor_data",
  "orderByColumn": "temperature",
  "order": "DESC",
  "limit": 10
}

# 标签分组
POST /api/sensor/queryGroupByTag
{
  "stableName": "sensor_data",
  "groupByTag": "station_id",
  "aggrFuncs": ["AVG"],
  "columns": ["temperature"]
}
```

## 核心功能详解

### Schema 自动同步

框架支持超级表结构自动同步，实体变更时自动执行 DDL：

```java
@Repository
public class SensorDataSchema extends BaseTdSchema<SensorDataEntity> {
    
    public SensorDataSchema(TdSchemaDiffer schemaDiffer) {
        super(schemaDiffer, SensorDataEntity.class);
    }
    
    @Override
    protected int getVersion() {
        return 1;  // 版本号，变更实体时递增
    }
}
```

**支持的 DDL 操作：**
- `ADD COLUMN` - 新增列
- `ADD TAG` - 新增标签
- `DROP COLUMN` - 删除列（需开启 `auto-drop`）

### SQL 注入防护

所有动态 SQL 构建点均经过严格校验：

```java
// 标识符白名单校验
validateIdentifier(tableName);    // 表名
validateIdentifier(columnName);   // 列名
validateIdentifier(tagName);      // 标签名

// 聚合函数白名单
ALLOWED_AGGR_FUNCS = ["AVG", "SUM", "MAX", "MIN", "COUNT", ...]

// FILL 策略白名单
ALLOWED_FILL = ["NULL", "PREV", "LINEAR", ...]
```

### Tag 同步框架

支持通过 MyBatis-Plus 拦截器自动同步 Tag 到 MySQL：

```java
@Configuration
public class TdSyncConfig {
    
    @Bean
    public TagSyncInterceptor tagSyncInterceptor() {
        // 默认关闭，需手动启用
        return new TagSyncInterceptor();
    }
}
```

**配套 MySQL 表：**
- `td_tag_mapping` - Tag 映射管理
- `td_sync_log` - 同步日志记录（支持重试）

### 查询过滤

复用 kernel 的 `AdvancedQueryReq`，支持复杂条件组合：

```java
AdvancedQuery query = new AdvancedQuery();
query.setConditions(Arrays.asList(
    ConditionVO.builder()
        .column("temperature")
        .operator(">")
        .value(25.0)
        .build(),
    ConditionVO.builder()
        .column("humidity")
        .operator("BETWEEN")
        .value("50,70")
        .build()
));

// 支持嵌套 AND/OR
query.setAndConditions(...);
query.setOrConditions(...);
```

## 配置说明

### 完整配置项

```yaml
td:
  url: jdbc:TAOS-RS://localhost:6041/water_guard  # JDBC 连接串
  username: root                                   # 用户名
  password: ${TD_PASSWORD:taosdata}               # 密码（支持环境变量）
  database: water_guard                            # 数据库名
  precision: ms                                    # 时间精度（ms/us/ns）
  keep-days: 3650                                  # 数据保留天数
  pool:
    initial-size: 5                                # 连接池初始大小
    max-active: 20                                 # 连接池最大活跃数
  schema:
    auto-drop: false                               # 是否自动删除废弃列
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `TD_PASSWORD` | TDengine 密码 | `taosdata` |
| `TD_URL` | TDengine 连接串 | `jdbc:TAOS-RS://localhost:6041/water_guard` |

## 最佳实践

### 1. 子表命名规范

```java
// 推荐：{业务前缀}_{标识}
String subTableName = "sensor_" + stationId;  // sensor_station_001
String subTableName = "meter_" + deviceId;    // meter_device_12345
```

### 2. 批量插入优化

```java
// 推荐：按子表分组后批量插入
Map<String, List<SensorDataEntity>> grouped = dataList.stream()
    .collect(Collectors.groupingBy(this::getSubTableName));

grouped.forEach((subTable, entities) -> {
    repo.insertBatch(entities, Collections.nCopies(entities.size(), subTable));
});
```

### 3. 查询性能优化

```java
// 使用标签过滤减少扫描范围
TdRangeReq req = new TdRangeReq();
req.setSubTableName("sensor_station_001");

// 添加时间范围限制
req.setStartTime(LocalDateTime.now().minusHours(1));
req.setEndTime(LocalDateTime.now());

// 指定返回列减少数据传输
req.setColumns(Arrays.asList("ts", "temperature"));
```

### 4. Schema 版本管理

```java
@Override
protected int getVersion() {
    // 每次变更实体结构时递增版本号
    // v1: 初始版本
    // v2: 新增 ph_value 列
    // v3: 新增 station_id 标签
    return 3;
}
```

## 测试

### 运行单元测试（不需要 TDengine 服务）

```bash
# 在项目根目录执行
cd server/core/td
mvn test -Pdevelopment

# 或从 server 目录执行
mvn test -pl core/td -Pdevelopment
```

### 运行集成测试（需要真实 TDengine 服务）

**前置条件**：需要 TDengine 3.x 服务运行中，连接信息在 `AbstractTdIT` 中配置。

```bash
# 运行全部集成测试
mvn verify -pl core/td -Pdevelopment

# 仅运行连接验证测试
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdConnectionIT"

# 仅运行 Schema 测试
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdSchemaIT"

# 仅运行 CRUD 测试
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdRepoCrudIT"

# 仅运行查询测试
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdRepoQueryIT"

# 仅运行 Schema Diff 测试
mvn failsafe:integration-test -pl core/td -Pdevelopment "-Dit.test=com.bidr.td.itest.TdSchemaDifferIT"
```

### 测试架构

**单元测试**（`*Test.java`）— 不依赖真实数据库，验证 Java 对象结构和反射逻辑：
- 11 个测试类，通过 `mvn test` 执行

**集成测试**（`*IT.java`）— 连接真实 TDengine，验证 SQL 执行和数据库交互：
- 位于 `com.bidr.td.itest` 包下，共 5 个测试类、30 个测试方法
- 使用 `AbstractTdIT` 基类手动构建 `JdbcTemplate`（不依赖 `@SpringBootTest`）
- 使用独立数据库 `water_itest` 做隔离，STABLE 名带 `itest_` 前缀
- 通过 `maven-failsafe-plugin` 执行

| 集成测试类 | 测试数 | 说明 |
|-----------|--------|------|
| `TdConnectionIT` | 3 | 连接验证、数据库存在、版本号检查 |
| `TdSchemaIT` | 5 | 注解建表、幂等性、DDL 生成、手动 DDL |
| `TdRepoCrudIT` | 7 | 单条/批量插入、子表生命周期、tag 修改 |
| `TdRepoQueryIT` | 11 | 范围查询、分页、聚合、窗口、TopN、LAST_ROW |
| `TdSchemaDifferIT` | 4 | Schema Diff（缺列/缺 tag/完全匹配） |

## 常见问题

### Q: 为什么写入时必须指定子表名？

A: TDengine 的子表需要显式创建，框架不隐式建表，避免误操作和命名冲突。

### Q: 如何启用 Tag 自动同步？

A: `TagSyncInterceptor` 默认关闭，需在配置类中手动注册为 Bean。

### Q: Schema 同步失败怎么办？

A: 同步失败会记录到 `td_sync_log` 表，支持重试。也可手动执行 DDL。

### Q: 如何自定义聚合函数？

A: 聚合函数需加入 `ALLOWED_AGGR_FUNCS` 白名单，防止 SQL 注入。

## 技术栈

- **TDengine JDBC**: 3.3.3（RESTful 驱动）
- **Spring Boot**: 2.7.3
- **连接池**: HikariCP
- **ORM**: MyBatis-Plus（kernel 提供）
- **测试**: JUnit 5

## 架构特点

1. **强制子表名** - 写入时必须指定，框架不隐式建表
2. **复用查询体系** - 查询过滤复用 kernel 的 `AdvancedQueryReq`
3. **灵活过滤** - 支持任意嵌套 AND/OR 组合条件
4. **配置风格统一** - 无前缀 `td:` 配置，参照现有中间件
5. **DAO 分层规范** - 严格遵循 `entity`/`mapper`/`repository`/`schema`

## 相关文档

- [CHANGELOG](../../CHANGELOG.md)
- [TDengine 官方文档](https://docs.taosdata.com/)
- [Kernel 模块文档](../kernel/README.md)
