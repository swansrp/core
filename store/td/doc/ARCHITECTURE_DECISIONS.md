# 架构决策记录

## ADR-001: TDengine 模块架构设计

**日期**: 2026-05-11  
**状态**: 已接受  
**上下文**: 水质在线监测平台需要高效存储和查询时序数据

### 决策

在 `server/core/td/` 下创建独立的 TDengine 时序数据基础功能框架模块。

### 架构设计

#### 1. 模块定位

- 与 `mongo`/`neo4j`/`elasticsearch` 同级，作为数据存储层的一部分
- 依赖 `kernel` 模块（复用查询体系和基础工具）
- 提供独立的 Spring Boot Starter 能力

#### 2. 包结构设计

```
com.bidr.td/
├── annotation/      # 注解定义（@TdStable, @TdColumn, @TdTag, @TdTimestamp）
├── config/          # 配置类（数据源、属性绑定）
├── constant/        # 常量（数据类型、错误码）
├── controller/      # REST Controller（BaseTdController）
├── dao/             # MySQL 配套表（platform 风格四目录）
│   ├── entity/
│   ├── mapper/
│   ├── repository/
│   └── schema/
├── inf/             # 接口定义
├── repository/      # 数据访问层（BaseTdRepo, BaseTdSchema）
├── sync/            # Tag 同步框架
└── vo/              # 值对象（查询请求）
```

**决策理由**:
- DAO 层参照 `platform/dao` 四目录结构，保持项目一致性
- 接口定义独立成 `inf` 包，便于扩展和测试
- 同步逻辑独立成 `sync` 包，职责清晰

#### 3. 注解系统设计

```java
@TdStable("sensor_data")
public class SensorDataEntity {
    @TdTimestamp
    private LocalDateTime ts;
    
    @TdColumn(name = "temperature", type = "FLOAT")
    private Float temperature;
    
    @TdTag(name = "station_id")
    private String stationId;
}
```

**决策理由**:
- 参照 JPA 注解风格，降低学习成本
- 显式声明列类型，避免类型推断错误
- 区分 Column 和 Tag，符合 TDengine 数据模型

#### 4. Schema 自动同步机制

- 使用 `BaseTdSchema` + `TdSchemaDiffer` 实现实体变更自动同步
- 支持 `ADD COLUMN` / `ADD TAG`
- 支持 `autoDrop` 配置（可选删除废弃列）
- DDL 命名规范：`stable_{tableName}_{version}`

**决策理由**:
- 避免手动维护 DDL 脚本
- 版本号机制确保同步可控
- autoDrop 默认关闭，防止误删数据

#### 5. 数据访问层设计

**BaseTdRepo 提供的 API**:
- `insertOne()` - 单条插入（强制指定子表名）
- `insertBatch()` - 多表批量插入
- `queryRange()` - 时间范围查询
- `queryLast()` - 最新数据查询
- `queryInterval()` - 聚合降采样
- `queryTopN()` - TopN 查询
- `queryGroupByTag()` - 标签分组聚合

**强制子表名设计**:
```java
repo.insertOne(data, "sensor_station_001");  // 必须指定
```

**决策理由**:
- TDengine 子表需显式创建，框架不隐式建表
- 避免命名冲突和误操作
- 批量插入使用多子表语法，提升性能

#### 6. 查询体系复用

复用 kernel 的 `AdvancedQueryReq`/`AdvancedQuery`/`ConditionVO`：

```java
TdRangeReq req = new TdRangeReq();
req.setQuery(new AdvancedQuery());  // 复用 kernel 查询对象
```

**决策理由**:
- 前端无需定制查询接口
- 保持查询语法一致性
- 支持复杂条件组合（AND/OR 嵌套）

#### 7. SQL 注入防护

**防护措施**:
1. `validateIdentifier()` - 白名单校验所有 SQL 拼接点
2. `ALLOWED_AGGR_FUNCS` - 聚合函数白名单
3. `ALLOWED_FILL` - FILL 策略白名单
4. 所有动态 SQL 构建点均经过严格校验

**决策理由**:
- TDengine SQL 拼接点较多，需统一防护
- 白名单机制比黑名单更安全
- 经过 3 轮审查修复 31 个安全问题

#### 8. 配置管理

```yaml
td:
  url: jdbc:TAOS-RS://localhost:6041/water_guard
  username: root
  password: ${TD_PASSWORD:taosdata}  # 支持环境变量
  database: water_guard
  precision: ms
  keep-days: 3650
  pool:
    initial-size: 5
    max-active: 20
  schema:
    auto-drop: false
```

**决策理由**:
- 无前缀 `td:` 配置，参照现有中间件风格
- 密码支持环境变量，符合安全规范
- 使用 HikariCP 连接池，性能优异

#### 9. Tag 同步框架

- `TdSyncService` - 异步 Tag 同步服务
- `TagSyncInterceptor` - MyBatis-Plus 拦截器（默认关闭）
- MySQL 配套表：`td_tag_mapping`、`td_sync_log`

**决策理由**:
- Tag 元数据存储在 MySQL，便于管理和查询
- 拦截器默认关闭，避免影响性能
- 异步同步不阻塞主流程

### 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| JDBC 驱动 | taos-jdbcdriver 3.3.3 | TDengine 官方 RESTful 驱动 |
| 连接池 | HikariCP | Spring Boot 默认，性能最优 |
| ORM | MyBatis-Plus | kernel 已集成，保持一致 |
| 测试 | JUnit 5 | Spring Boot 2.7 默认 |

### 替代方案

#### 方案 A: 使用 TDengine ORM 框架

**优点**: 更贴近 TDengine 特性  
**缺点**: 增加学习成本，与现有架构不一致  
**结论**: 不采用，自研更可控

#### 方案 B: 复用 mongo/neo4j 模块结构

**优点**: 结构统一  
**缺点**: TDengine 时序特性无法充分体现  
**结论**: 不采用，时序数据库有特殊需求

### 影响范围

- **前端开发**: 无需定制查询接口
- **后端开发**: 继承 BaseTdRepo 和 BaseTdController 快速接入
- **运维**: 需配置 TDengine 连接信息
- **数据库管理**: 超级表结构由框架自动管理

### 后续演进

1. 补充集成测试（真实 TDengine 实例）
2. 添加性能基准测试
3. 完善监控指标
4. 考虑支持 TDengine 原生 WebSocket 驱动
5. 评估是否需要数据压缩配置

### 参考资料

- [TDengine 官方文档](https://docs.taosdata.com/)
- [CHANGELOG](../../CHANGELOG.md)
- [TD 模块文档](../td/README.md)

---

## ADR-002: TDengine 模块代码质量保障

**日期**: 2026-05-11  
**状态**: 已接受  
**上下文**: 确保 TDengine 模块代码质量和安全性

### 决策

实施严格的代码审查流程，经过 3 轮审查修复 31 个问题。

### 审查流程

#### 第 1 轮（16 个问题）

**关键修复**:
- ✅ `insertBatch` 列括号错误（CRITICAL）
- ✅ `queryTopN` 语法错误（CRITICAL）
- ✅ SQL 注入白名单校验
- ✅ DDL 异常传播机制

#### 第 2 轮（25 个问题，完整审查）

**关键修复**:
- ✅ `insertBatch` 改为多表批量语法
- ✅ 密码默认值安全处理
- ✅ 聚合函数白名单
- ✅ Parser 单例模式

#### 第 3 轮（6 个剩余问题）

**关键修复**:
- ✅ `queryGroupByTag` 聚合函数白名单
- ✅ `initStable` 异常传播
- ✅ `fill` 策略白名单

### 质量保障机制

1. **单元测试覆盖**: 11 个测试类
2. **MVC 测试**: Controller 端点验证
3. **安全审计**: SQL 注入防护全面审查
4. **异常处理**: 所有关键路径异常传播

### 经验教训

1. **SQL 拼接必须校验**: 所有动态 SQL 点都需白名单校验
2. **异常不能吞掉**: DDL 失败必须向上抛出
3. **白名单优于黑名单**: 聚合函数、FILL 策略等使用白名单
4. **测试要覆盖边界**: 空值、重复、越界等场景

---

**本文档持续更新，记录 TDengine 模块的重要架构决策。**
