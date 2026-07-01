# AdminStatisticMetricInf 接口原理分析文档

## 📋 目录

1. [接口概述](#1-接口概述)
2. [核心参数说明](#2-核心参数说明)
3. [基本原理](#3-基本原理)
4. [功能详解](#4-功能详解)
5. [样例输入与SQL对应](#5-样例输入与sql对应)
6. [优化建议](#6-优化建议)
7. [前端通用穿透工具函数](#7-前端通用穿透工具函数)

---

## 1. 接口概述

### 1.1 基本信息

- **文件路径**: `framework/core/kernel/src/main/java/com/bidr/kernel/controller/inf/statistic/AdminStatisticMetricInf.java`
- **接口类型**: 泛型接口 `<ENTITY, VO>`
- **继承关系**: 
  - `AdminStatisticBaseInf<ENTITY, VO>`
  - `AdminStatisticParseInf`
- **作者**: Sharp
- **创建时间**: 2025/4/29

### 1.3 调试路径支持

所有 statistic 接口均支持 `/**` 通配路径，便于调试时在 URL 末尾追加描述性标识（不影响业务逻辑）：

| 接口 | 标准路径 | 调试路径示例 |
|------|----------|-------------|
| BaseAdminController | `/general/statistic` | `/general/statistic/潜在客户统计` |
| BaseAdminController | `/advanced/statistic` | `/advanced/statistic/潜在客户` |
| DynamicQueryController | `/{portalName}/general/statistic` | `/{portalName}/general/statistic/新签项目` |
| DynamicQueryController | `/{portalName}/advanced/statistic` | `/{portalName}/advanced/statistic/客户趋势` |

**实现方式**：
```java
@RequestMapping(value = {"/advanced/statistic", "/advanced/statistic/**"}, method = RequestMethod.POST)
```

> 末尾的路径段仅用于调试识别，不会被解析或使用。

### 1.4 核心职责

`AdminStatisticMetricInf` 是一个**多维度指标统计接口**，用于实现灵活的数据统计查询功能。它通过 MyBatis-Plus-Join (MPJ) 框架动态构建 SQL，支持：

- ✅ 按字典字段分组统计
- ✅ 自定义条件统计（COUNT/SUM）
- ✅ 支持去重统计（DISTINCT）
- ✅ 双层指标嵌套（父子指标结构）
- ✅ 动态排序和限制条数

### 1.5 使用场景

- 仪表盘数据统计
- 多维度报表生成
- 动态指标分析
- 数据可视化支撑

---

## 2. 核心参数说明

### 2.1 请求对象参数

#### GeneralStatisticReq / AdvancedStatisticReq

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `metricColumn` | `List<Metric>` | 是 | **分组指标**，按哪些字段进行 GROUP BY | `[{"column": "status", "dictMap": {...}}]` |
| `metricCondition` | `List<MetricCondition>` | 否 | **自定义条件指标**，手写 WHERE 条件进行统计 | 见下方 MetricCondition 详解 |
| `majorCondition` | `String` | 否 | **主指标标识**，"1"=以条件为主，"0"/null=以分组指标为主 | `"0"` 或 `"1"` |
| `statisticColumn` | `List<KeyValueResVO>` | 是 | **统计列**，要统计的目标字段 | `[{"value": "amount", "label": "金额"}]` |
| `sort` | `Integer` | 否 | **排序方式**，对应 `PortalSortDict` | `1`=ASC, `2`=DESC |
| `limit` | `Integer` | 否 | **限制条数**，统计前 N 名 | `10` |
| `distinct` | `String` | 否 | **全局去重**（仅 AdvancedReq 支持） | `"0"` 或 `"1"` |

#### Metric 对象结构

```json
{
  "column": "status",
  "dictMap": {
    "1": "进行中",
    "2": "已完成",
    "3": "已取消"
  }
}
```

#### MetricCondition 对象结构

| 参数名 | 类型 | 默认值 | 说明 | 示例值 |
|--------|------|--------|------|--------|
| `distinct` | `String` | `"0"` | **是否去重统计**，"1"=使用 DISTINCT | `"1"` |
| `count` | `String` | `"0"` | **统计类型**，"1"=COUNT，"0"=SUM | `"1"` |
| `value` | `String` | null | 条件值（预留字段） | `null` |
| `label` | `String` | - | **条件标签**，用于结果集 key 拼接 | `"重大项目"` |
| `condition` | `AdvancedQuery` | - | **查询条件**，构建 CASE WHEN 子句 | 见下方示例 |

#### KeyValueResVO 对象结构

```json
{
  "value": "contract_amount",
  "label": "合同金额"
}
```

---

## 3. 基本原理

### 3.1 核心执行流程

```
接收统计请求
    ↓
检查 metricCondition
    ↓
├─ 有自定义条件 → 构建条件统计Wrapper → 检查 majorCondition
│                                         ├─ "1" 条件为主 → getConditionMajorStatisticRes
│                                         └─ "0" 指标为主 → getMetricMajorStatisticRes
│
└─ 无自定义条件 → 构建分组统计Wrapper → getStatisticRes
                                            ↓
                                      返回统计结果
```

### 3.2 SQL 构建原理

#### 场景 A：有 metricCondition（自定义条件统计）

**核心方法**: `buildStatisticWrapper(metricColumn, metricCondition, statisticColumn)`

**SQL 生成逻辑**:

```sql
SELECT 
    IFNULL(status, '__NULL__') AS status,
    COUNT(DISTINCT CASE WHEN contract_amount >= 1000000 THEN id END) AS '重大项目_合同金额',
    SUM(CASE WHEN contract_amount < 1000000 THEN contract_amount END) AS '小型项目_合同金额'
FROM erp_project
GROUP BY IFNULL(status, '__NULL__')
```

#### 场景 B：无 metricCondition（纯分组统计）

**核心方法**: `buildStatisticWrapper(metricColumn, statisticColumn, sort, limit)`

**SQL 生成逻辑**:

```sql
SELECT 
    IFNULL(status, '__NULL__') AS status,
    SUM(contract_amount) AS statistic
FROM erp_project
GROUP BY IFNULL(status, '__NULL__')
ORDER BY statistic DESC
LIMIT 10
```

### 3.3 数据解析原理

#### 解析策略 1：条件为主（majorCondition = "1"）

**方法**: `getConditionMajorStatisticRes` (第 109-179 行)

**返回结构**:
```json
[
  {
    "metricLabel": "重大项目",
    "children": [
      { "metricColumn": "status", "metric": "1", "statistic": 150 }
    ],
    "statistic": 230
  }
]
```

#### 解析策略 2：指标为主（majorCondition = "0"）

**方法**: `getMetricMajorStatisticRes` (第 191-268 行)

**返回结构**:
```json
[
  {
    "metricColumn": "status",
    "metric": "1",
    "metricLabel": "进行中",
    "children": [
      { "metricLabel": "重大项目_合同金额", "statistic": 1000000 }
    ],
    "statistic": 1500000
  }
]
```

#### 解析策略 3：纯分组统计

**方法**: `getStatisticRes` (第 317-336 行)

**返回结构**:
```json
[
  { "metricColumn": "status", "metric": "1", "metricLabel": "进行中", "statistic": 5000000 }
]
```

---

## 4. 功能详解

### 4.1 核心方法清单

| 方法名 | 功能描述 | 行号 | 复杂度 |
|--------|----------|------|--------|
| `statisticByGeneralReq` | 通用统计入口方法 | 33-57 | ⭐⭐ |
| `statisticByAdvancedReq` | 高级统计入口（支持 distinct） | 512-539 | ⭐⭐ |
| `buildStatisticWrapper` (v1) | 构建条件统计 SQL Wrapper | 67-97 | ⭐⭐⭐ |
| `buildStatisticWrapper` (v2) | 构建分组统计 SQL Wrapper | 278-307 | ⭐⭐ |
| `getConditionMajorStatisticRes` | 解析条件为主的统计结果 | 109-179 | ⭐⭐⭐⭐ |
| `getMetricMajorStatisticRes` | 解析指标为主的统计结果 | 191-268 | ⭐⭐⭐⭐ |
| `getStatisticRes` | 解析纯分组统计结果 | 317-336 | ⭐⭐⭐ |
| `buildMetricWrapperByMetricColumn` | 构建 GROUP BY 子句 | 344-353 | ⭐ |
| `fillDictMetric` | 补充字典缺失的统计项 | 363-377 | ⭐⭐ |
| `getConditionStatisticRes` | 对结果集排序 | 386-401 | ⭐ |
| `groupByCondition` | 按条件标签分组结果 | 411-427 | ⭐⭐ |
| `groupChildrenByCondition` | 按条件标签分组子项 | 437-457 | ⭐⭐⭐ |
| `fillMetricMap` | 解析并填充多层指标 | 469-504 | ⭐⭐⭐ |

---

## 5. 样例输入与SQL对应

### 5.1 样例 1：按项目状态统计合同金额（简单分组）

#### 输入请求

```json
{
  "metricColumn": [
    {
      "column": "status",
      "dictMap": {
        "1": "进行中",
        "2": "已完成",
        "3": "已取消"
      }
    }
  ],
  "statisticColumn": [
    { 
      "value": "contract_amount", 
      "label": "合同金额" 
    }
  ],
  "sort": 2
}
```

#### 生成 SQL

```sql
SELECT 
    IFNULL(status, '__NULL__') AS status,
    SUM(contract_amount) AS statistic
FROM erp_project
GROUP BY IFNULL(status, '__NULL__')
ORDER BY statistic DESC
```

#### 返回结果

```json
[
  { 
    "metricColumn": "status",
    "metric": "1", 
    "metricLabel": "进行中", 
    "statistic": 5000000 
  },
  { 
    "metricColumn": "status",
    "metric": "2", 
    "metricLabel": "已完成", 
    "statistic": 3000000 
  },
  { 
    "metricColumn": "status",
    "metric": "3", 
    "metricLabel": "已取消", 
    "statistic": 1000000 
  }
]
```

---

### 5.2 样例 2：按地区统计不同类型项目数量（自定义条件 + 指标为主）

#### 输入请求

```json
{
  "metricColumn": [
    { "column": "region" }
  ],
  "metricCondition": [
    {
      "label": "重大项目",
      "count": "1",
      "distinct": "0",
      "condition": {
        "field": "contract_amount",
        "operator": ">=",
        "value": 1000000
      }
    },
    {
      "label": "小型项目",
      "count": "1",
      "distinct": "0",
      "condition": {
        "field": "contract_amount",
        "operator": "<",
        "value": 1000000
      }
    }
  ],
  "statisticColumn": [
    { "value": "", "label": "项目数" }
  ],
  "majorCondition": "0"
}
```

#### 生成 SQL

```sql
SELECT 
    IFNULL(region, '__NULL__') AS region,
    COUNT(CASE WHEN contract_amount >= 1000000 THEN 1 END) AS '重大项目_项目数',
    COUNT(CASE WHEN contract_amount < 1000000 THEN 1 END) AS '小型项目_项目数'
FROM erp_project
GROUP BY IFNULL(region, '__NULL__')
```

#### 返回结果（指标为主结构）

```json
[
  {
    "metricColumn": "region",
    "metric": "华东",
    "children": [
      { 
        "metricLabel": "重大项目_项目数", 
        "statistic": 15 
      },
      { 
        "metricLabel": "小型项目_项目数", 
        "statistic": 30 
      }
    ],
    "statistic": 45
  },
  {
    "metricColumn": "region",
    "metric": "华北",
    "children": [
      { 
        "metricLabel": "重大项目_项目数", 
        "statistic": 10 
      },
      { 
        "metricLabel": "小型项目_项目数", 
        "statistic": 25 
      }
    ],
    "statistic": 35
  }
]
```

---

### 5.3 样例 3：去重统计不同客户的项目数（自定义条件 + 条件为主）

#### 输入请求

```json
{
  "metricCondition": [
    {
      "label": "活跃客户",
      "count": "1",
      "distinct": "1",
      "condition": {
        "field": "status",
        "operator": "=",
        "value": "1"
      }
    }
  ],
  "statisticColumn": [
    { "value": "customer_id", "label": "客户数" }
  ],
  "majorCondition": "1"
}
```

#### 生成 SQL

```sql
SELECT 
    COUNT(DISTINCT CASE WHEN status = '1' THEN customer_id END) AS '活跃客户_客户数'
FROM erp_project
```

#### 返回结果（条件为主结构）

```json
[
  {
    "metricLabel": "活跃客户",
    "children": [],
    "statistic": 128
  }
]
```

---

## 6. 优化建议

### 🔴 严重问题（必须修复）

#### 6.1 SQL 注入风险

**位置**: 第 76-89 行、第 82-84 行

**问题描述**:
```java
// 直接拼接用户输入的 label，存在 SQL 注入风险
StringUtil.join(metricCondition.getLabel(), statistic.getLabel())
```

**风险等级**: 🔴 高危

**修复建议**:

```java
// 方案 1: 添加白名单验证
private String sanitizeLabel(String label) {
    if (label == null) return "";
    if (!label.matches("[\\u4e00-\\u9fa5a-zA-Z0-9_]+")) {
        throw new IllegalArgumentException("非法标签名: " + label);
    }
    return label;
}

// 方案 2: 使用参数化别名
String alias = "alias_" + Math.abs(Objects.hash(metricCondition.getLabel(), statistic.getLabel()));
```

**优先级**: P0（立即修复）

---

#### 6.2 空指针异常（NPE）风险

**位置 1**: 第 213 行
```java
String metricObj = map.get(metric.getColumn()).toString();  // 可能 NPE
```

**位置 2**: 第 480-481 行
```java
String metricStr = map.get(metric.getColumn()).toString();  // 可能 NPE
```

**修复建议**:

```java
private String getSafeStringValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) {
        return StatisticRes.NULL;
    }
    return value.toString();
}
```

**优先级**: P1（尽快修复）

---

#### 6.3 数字转换异常

**位置**: 第 142-143 行、第 171-172 行

**修复建议**:

```java
private BigDecimal parseToBigDecimal(Object value) {
    if (FuncUtil.isEmpty(value)) {
        return BigDecimal.ZERO;
    }
    try {
        return new BigDecimal(value.toString().trim());
    } catch (NumberFormatException e) {
        log.warn("无法解析数字: {}", value, e);
        return BigDecimal.ZERO;
    }
}
```

**优先级**: P2（计划修复）

---

### 🟡 中等问题（建议优化）

#### 6.4 魔法值硬编码

**优化建议**:

```java
// 使用枚举
public enum YesNo {
    NO("0"), 
    YES("1");
    
    private final String value;
}

// 修改前
if (StringUtil.convertSwitch(metricCondition.getCount())) {

// 修改后
if (YesNo.YES.equals(metricCondition.getCount())) {
```

**优先级**: P2

---

#### 6.5 重复代码过多

**位置**: `getConditionMajorStatisticRes` 和 `getMetricMajorStatisticRes`

**优化建议**: 提取公共方法

```java
private Map<String, StatisticRes> initializeResMap(
        List<MetricCondition> metricCondition,
        List<KeyValueResVO> statisticColumn,
        Metric metric);

private void parseStatisticValue(
        Map<String, Object> map,
        String key,
        StatisticRes res);
```

**优先级**: P2

---

#### 6.6 性能问题 - 大数据量场景

**位置**: 第 112、194、319 行

**问题描述**: 全量加载到内存，可能导致 OOM

**优化建议**:

```java
// 增加分页支持
if (req.getPage() != null && req.getSize() != null) {
    wrapper.last("LIMIT " + req.getSize() + " OFFSET " + 
        ((req.getPage() - 1) * req.getSize()));
}
```

**优先级**: P2（当数据量 > 10 万时变为 P1）

---

#### 6.7 多统计列覆盖 BUG

**位置**: 第 282-289 行

**问题描述**: 多个 statisticColumn 时，statistic 列会被覆盖

**修复建议**:

```java
for (int i = 0; i < statisticColumn.size(); i++) {
    String aliasName = "statistic_" + i;
    wrapper.getSelectColum().add(new SelectString(
        String.format("sum(%s) as %s", statistic.getValue(), aliasName), 
        wrapper.getAlias()));
}
```

**优先级**: P1

---

### 🟢 轻微问题（可选优化）

#### 6.8 方法过长

**位置**: 第 109-179 行、第 191-268 行

**优化建议**: 拆分职责

```java
default List<StatisticRes> getConditionMajorStatisticRes(...) {
    List<Map<String, Object>> maps = executeQuery(wrapper);
    Map<String, StatisticRes> resMap = initializeResMap(...);
    parseDataToResMap(...);
    fillDictIfNeeded(...);
    return groupByCondition(metricCondition, resMap, sort);
}
```

**优先级**: P3

---

#### 6.9 缺少单元测试

**优化建议**:

```java
@SpringBootTest
class AdminStatisticMetricInfTest {
    
    @Test
    void testStatisticByGeneralReq_SimpleGroup() {
        GeneralStatisticReq req = new GeneralStatisticReq();
        // ...
    }
    
    @Test
    void testStatisticByAdvancedReq_WithCondition() {
        AdvancedStatisticReq req = new AdvancedStatisticReq();
        // ...
    }
}
```

**优先级**: P2

---

#### 6.10 增加日志记录

**优化建议**:

```java
@Slf4j
public interface AdminStatisticMetricInf<ENTITY, VO> {
    
    default List<StatisticRes> statisticByGeneralReq(GeneralStatisticReq req) {
        log.debug("开始统计查询, metricColumn={}, metricCondition={}", 
            req.getMetricColumn(), req.getMetricCondition());
        
        long startTime = System.currentTimeMillis();
        List<StatisticRes> result = ...;
        
        log.debug("统计查询完成, 耗时={}ms, 结果数={}", 
            System.currentTimeMillis() - startTime, result.size());
        
        return result;
    }
}
```

**优先级**: P3

---

## 7. 优化优先级总结

| 优先级 | 问题 | 预估工作量 | 建议排期 |
|--------|------|-----------|---------|
| **P0** | SQL 注入风险 | 2小时 | 立即 |
| **P1** | 空指针异常 | 1小时 | 本周 |
| **P1** | 多统计列覆盖 BUG | 3小时 | 本周 |
| **P2** | 数字转换异常 | 1小时 | 下周 |
| **P2** | 魔法值硬编码 | 2小时 | 下周 |
| **P2** | 重复代码重构 | 4小时 | 下周 |
| **P2** | 性能优化（分页） | 3小时 | 下周 |
| **P2** | 单元测试 | 6小时 | 下周 |
| **P3** | 方法过长重构 | 3小时 | 后续 |
| **P3** | 增加日志 | 1小时 | 后续 |

---

## 8. 总结

`AdminStatisticMetricInf` 是一个功能强大的多维度统计接口，具备以下优势：

✅ **灵活性高**：支持多种统计模式和组合  
✅ **扩展性好**：通过接口 default 方法实现，易于继承和扩展  
✅ **自动化强**：自动填充字典缺失数据，保证结果完整性  

但同时也存在一些需要改进的地方：

⚠️ **安全性**：SQL 注入风险需要立即修复  
⚠️ **健壮性**：NPE 和数字转换异常需要处理  
⚠️ **性能**：大数据量场景需要优化  
⚠️ **可维护性**：代码重复度高，方法过长  

建议按照优先级逐步优化，优先解决安全性和稳定性问题。

---

## 9. 前端拼接API实战经验

基于客户仪表盘开发实践，总结出一套前端构建 `statisticByAdvancedReq` 请求体的标准化模式。

### 9.1 请求体核心结构速查

```typescript
{
  // ① GROUP BY 维度（决定按什么字段分组）
  metricColumn: [{ column: '字段名', dictMap?: { 'code': 'label' } }],

  // ② 统计目标（仅用于生成 SQL 别名，实际聚合由 metricCondition 控制）
  statisticColumn: [{ value: '目标字段', label: '显示标签' }],

  // ③ 自定义聚合条件（控制 SELECT 中的 COUNT/SUM + CASE WHEN）
  metricCondition: [{
    count: '1',        // '1'=COUNT, '0'=SUM
    distinct: '1',     // '1'=DISTINCT
    value: '字段名',   // COUNT(DISTINCT 字段名)
    label: '条件标签', // 用于结果集 key 拼接
    condition: { conditionList: [] }  // 空=无CASE WHEN, 直接聚合
  }],

  // ④ 全局 WHERE 过滤条件
  condition: {
    conditionList: [
      { property: '字段', relation: 1, value: ['值'] },  // relation: 1=EQUAL, 3=GREATER, 11=IN
    ]
  },

  // ⑤ 其他控制参数
  distinct: '1',                    // wrapper.distinct()
  majorCondition: '1',              // '1'/空=条件为主, '0'=指标为主
  selectColumnCondition: { category: 0, mode: 2 }  // 控制查询视图切换
}
```

### 9.2 参数作用域分层

| 层级 | 参数 | SQL 对应 | 说明 |
|------|------|----------|------|
| 全局过滤 | `condition.conditionList` | `WHERE ...` | 所有行都受此条件过滤 |
| 分组维度 | `metricColumn` | `GROUP BY ...` | 决定结果的分组字段 |
| 聚合函数 | `metricCondition` | `SELECT COUNT/SUM(...)` | 控制统计方式和 CASE WHEN 条件 |
| 视图切换 | `selectColumnCondition` | 前端控制 | `category`=数据类别, `mode`=传统(2)/总包 |

### 9.3 维度切换模式（同一接口不同统计维度）

仅需替换 `metricColumn` 中的 `column` 即可切换统计维度：

```typescript
// 按客户类型分组（饼图）
metricColumn: [{ column: 'customerCategory', dictMap: { '01': '企业', '02': '政府部门', '09': '其他' } }]

// 按省份分组（地图）
metricColumn: [{ column: 'province' }]

// 按年度分组（趋势图）
// 使用 metricCondition 的交叉条件模式，而非 metricColumn
```

### 9.4 metricCondition 的两种用法

#### 用法 A：空条件直接聚合（最常用）

```typescript
metricCondition: [{
  count: '1', distinct: '1', value: 'customerNo',
  label: '客户数',
  condition: { conditionList: [] }  // ← 空条件
}]
```

生成 SQL：`COUNT(DISTINCT customerNo) AS '客户数'`

**适用场景**：分组统计总数（客户数、项目数等）

#### 用法 B：交叉条件（趋势图专用）

```typescript
metricCondition: [
  { label: '2024&&新客户', condition: { conditionList: [
    { property: 'dy', relation: 1, value: ['2024'] },
    { property: 'newCustomer', relation: 1, value: ['1'] }
  ]}},
  { label: '2024&&老客户', condition: { conditionList: [
    { property: 'dy', relation: 1, value: ['2024'] },
    { property: 'newCustomer', relation: 1, value: ['0'] }
  ]}}
]
```

生成 SQL：`COUNT(CASE WHEN dy='2024' AND newCustomer='1' THEN 1 END) AS '2024&&新客户'`

**适用场景**：多维度交叉统计（年 × 新老客户）

### 9.5 响应结构速查

#### 有 metricCondition + majorCondition='1'/空 → 条件为主

```json
[
  {
    "metricLabel": "客户数",
    "statistic": 3953,
    "children": [
      { "metricColumn": "province", "metric": "北京", "statistic": 200 },
      { "metricColumn": "province", "metric": "上海", "statistic": 150 }
    ]
  }
]
```

#### 有 metricCondition + majorCondition='0' → 指标为主

```json
[
  {
    "metricColumn": "province",
    "metric": "北京",
    "statistic": 200,
    "children": [
      { "metricLabel": "客户数", "statistic": 200 }
    ]
  }
]
```

#### 无 metricCondition → 纯分组

```json
[
  { "metricColumn": "province", "metric": "北京", "statistic": 200 }
]
```

### 9.6 前端解析嵌套响应的通用模式

响应结构为多层嵌套树形时，使用递归查找定位目标维度：

```typescript
function findMetricLevel(items: any[], targetColumn: string): any[] {
  for (const item of items) {
    if (item.metricColumn === targetColumn) return items
    if (item.children?.length > 0) {
      const result = findMetricLevel(item.children, targetColumn)
      if (result.length > 0) return result
    }
  }
  return []
}
```

### 9.7 条件增量模式（复用基础请求体）

通过 `extraConditions` 参数实现条件叠加，避免重复构建：

```typescript
function buildProvinceBody(year, mode, extraConditions, area, plate, manageLevel) {
  const conditionList = [
    { property: 'dy', relation: 1, value: [String(year)] },
    { property: 'contractSummary', relation: 3, value: [0] },
    ...extraConditions,  // ← 增量条件插入
  ]
  // ...
}

// 全体客户：extraConditions = []
// 新增客户：extraConditions = [{ property: 'newCustomer', relation: 1, value: ['1'] }]
// 新签合同：extraConditions = [{ property: 'projectSignStatus', relation: 11, value: ['01','02'] }]
```

### 9.8 relation 枚举速查

| relation | 含义 | SQL | 示例 |
|----------|------|-----|------|
| 1 | EQUAL | `= value` | `dy = '2026'` |
| 3 | GREATER | `> value` | `contractSummary > 0` |
| 5 | LESS | `< value` | |
| 11 | IN | `IN (v1, v2)` | `projectSignStatus IN ('01','02')` |
| 13 | BETWEEN | `BETWEEN v1 AND v2` | |

---

## 10. 图表点击穿透 Portal Table 条件构造指南

### 10.1 核心原理

当用户点击图表中的某个数据项时，需要弹出 Portal Table 展示该数据项对应的明细记录。
**条件构造的核心思路**：将图表请求体中的全局筛选条件 + 点击项的维度条件合并，作为 Portal Table 的 `advanceCondition`。

```
图表请求体 (buildXxxBody)
    │
    ├── condition.conditionList  ──→  Portal 全局筛选条件（原样提取）
    ├── selectColumnCondition.mode ──→  Portal 视图切换条件（提取 mode）
    └── 点击项的维度字段         ──→  Portal 穿透维度条件（手动追加）
```

### 10.2 通用构造步骤

**Step 1**：在 API 文件的 `buildXxxDrillConfig` 函数中，调用对应的请求体构建函数生成 body

**Step 2**：从 body 中提取全局条件
```typescript
const globalConditionList = body?.condition?.conditionList ?? []
const mode = body?.selectColumnCondition?.mode ?? 2
```

**Step 3**：根据点击项构造维度条件
```typescript
// 示例：点击饼图的 "企业" 分类
const drillConditionList = [
  { property: 'customerCategory', relation: 1, value: ['01'] }
]
```

**Step 4**：合并为 `CustomerDrillingConfig`
```typescript
return {
  type: CustomerDrillingType.CATEGORY_ALL,
  globalConditionList,     // 全局筛选
  drillConditionList,      // 维度条件
  mode,                    // 视图切换
  title: '客户类型分布 - 企业',
}
```

**Step 5**：`CustomerDrilling.vue` 内部将两者合并为 Portal 的 `advanceCondition`
```typescript
// buildDrillingCondition 工具函数
const conditionList = [...globalConditionList, ...drillConditionList]
return { conditionList, andOr: '0' }  // AND 连接
```

### 10.3 各图表类型穿透条件速查表

| 图表 | 点击项 | 维度条件 (drillConditionList) | 说明 |
|------|--------|-------------------------------|------|
| **生命周期卡片** | 潜在/种子/正式客户 | `{ property: 'customerStatus', relation: 1, value: [status] }` | status: '2'=潜在, '3'=种子, '4'=正式 |
| **生命周期卡片** | 新增客户 | `[]`（空） | becomeOfficeDt 已在全局条件中 |
| **生命周期卡片** | 新成单用户 | `[]`（空） | contractAmt>0 + becomeOfficeDt 已在全局条件中 |
| **客户排行榜** | 某客户 | `{ property: 'customerNo', relation: 1, value: [customerId] }` | 优先用 customerId，回退到 customerName |
| **客户类型饼图** | 某分类 | `{ property: 'customerCategory', relation: 1, value: [code] }` | code: '01'=企业, '02'=政府, '09'=其他 |
| **客户趋势图** | 某年某类 | `[{property:'dy', value:[year]}, {property:'newCustomer', value:['1'/'0']}]` | 新客户='1', 老客户='0' |
| **地域分布地图** | 某省份 | `{ property: 'customerProvince', relation: 1, value: [provinceCode] }` | 需用行政区编码，如 '110000' |

### 10.4 关键设计原则

#### 原则 1：全局条件原样提取，不重新构建

```typescript
// ✅ 正确：从 body 中直接提取
const globalConditionList = extractGlobalConditionList(body)

// ❌ 错误：手动重新构建全局条件
const globalConditionList = [
  { property: 'dy', relation: 1, value: [year] },
  { property: 'area', relation: 1, value: [area] },
  // ...
]
```

**原因**：body 中已包含完整的全局条件（dy、area、plate、manageLevel、contractSummary 等），
直接提取可保证穿透表格的筛选范围与图表数据完全一致。

#### 原则 2：维度条件只追加点击项的字段

```typescript
// ✅ 正确：仅追加点击维度
// 点击饼图 "企业" → 仅追加 customerCategory
drillConditionList: [{ property: 'customerCategory', relation: 1, value: ['01'] }]

// ❌ 错误：重复追加已在全局条件中的字段
// 趋势图全局条件已有 projectSignStatus，不需要再追加
```

#### 原则 3：condition 计算逻辑下沉到 API 层

每个 API 文件导出独立的 `buildXxxDrillConfig` 函数，视图层仅保留一行调用：

```typescript
// API 层 (apis/customerMap.ts)
export function buildMapDrillConfig(provinceName, activeTab, queryCondition) {
  const body = buildXxxBody(...)
  return {
    type: CustomerDrillingType.MAP_ALL,
    globalConditionList: extractGlobalConditionList(body),
    drillConditionList: [{ property: 'customerProvince', relation: 1, value: [code] }],
    mode: extractModeFromBody(body),
    title: `客户地域分布 - ${provinceName}`,
  }
}

// 视图层 (index.vue) —— 一行调用
chart.on('click', (params) => {
  const config = buildMapDrillConfig(params.name, mapActiveTab.value, queryCondition)
  if (config) openCustomerDrilling(config)
})
```

#### 原则 4：selectColumnCondition.mode 必须同步传递

Portal Table 的查询视图由 `selectColumnCondition` 控制（传统/总包），
穿透时必须从原始请求体中提取 mode 并传给 Portal，否则视图可能不匹配。

```typescript
// 提取 mode
const mode = body?.selectColumnCondition?.mode ?? 2

// CustomerDrilling.vue 中构建 Map 传给 Portal
const selectColumnCondition = new Map()
selectColumnCondition.set('category', 0)
selectColumnCondition.set('mode', config.mode)
```

### 10.5 特殊场景处理

#### 场景 A：全局条件中已包含维度字段（无需追加 drillConditionList）

新增客户卡片：`buildNewCustomerBody` 的全局条件已包含 `becomeOfficeDt BETWEEN`，
穿透时无需额外追加 `drillConditionList`，设为空数组即可。

#### 场景 B：不同 Tab 对应不同全局条件（需在函数内分支构建 body）

地图模块有 3 个 Tab（存量/新增/新签），每个 Tab 的全局条件不同：
```typescript
const body = activeTab === 'all'
  ? buildAllCustomerByProvinceBody(...)     // 无额外条件
  : activeTab === 'new'
    ? buildNewCustomerByProvinceBody(...)   // + newCustomer = '1'
    : buildContractCustomerByProvinceBody(...) // + projectSignStatus IN ('01','02')
```

穿透时全局条件从对应 Tab 的 body 中提取，保证筛选范围与图表数据一致。

#### 场景 C：图表使用独立 API（非 statistic 接口）

合作往来分析使用 `getCustomerRetention`（非 statistic 接口），
其条件结构完全不同，需要单独实现 `buildXxxDrillConfig`，
不能复用 `extractGlobalConditionList` 等工具函数。

#### 场景 D：同一接口不同 tableId（需按类型路由）

生命周期中：
- 潜在/种子/正式/新增客户 → tableId = `DimOmCustomerDyf`
- 新成单用户 → tableId = `OmCustomerSelf`

通过 `CustomerDrillingType` 枚举区分，在 `getDrillingTableId` 中路由到正确的表。

---

## 7. 前端通用穿透工具函数

### 7.1 函数定义

**`buildDrillConditionFromStatistic(body, drillMetric)`**

位置: `erp-view/src/framework/components/common/Portal/utils.ts`

这是一个通用工具函数，将 statistic 请求体 + 点击项标识（drillMetric）自动合并为 Portal Table 所需的 `advanceCondition`，消除各模块手写条件拼装逻辑的重复代码。

### 7.2 工作原理

```
输入: body (statistic 请求体) + drillMetric (点击项标识)
                         │
     ┌───────────────────┼───────────────────┐
     ▼                   ▼                   ▼
 全局条件提取        条件匹配            分组等值
 body.condition     metricCondition      metricColumn + metric
 .conditionList     匹配 conditionLabel  → WHERE col = 'val'
                    提取其 condition
     │                   ▼                   │
     └───────────────────┬───────────────────┘
                         ▼
         ConditionListType (advanceCondition)
         { conditionList: [全局..., 维度条件...], andOr: '0' }
```

### 7.3 drillMetric 参数对照

| 场景 | drillMetric | 自动追加的条件 |
|------|-------------|---------------|
| 纯条件穿透（点击卡片/列表项） | `{ conditionLabel: '正式客户' }` | 从 body.metricCondition 匹配 label，提取其 condition 对象 |
| 纯分组穿透（饼图扇区/柱图柱子） | `{ metricColumn: 'province', metric: '北京' }` | 构建 `WHERE province = '北京'` |
| 分组 + 条件（趋势子项） | 三者都传 | AND 合并两种条件 |
| NULL 值分组 | `metric: 'NULL'` | 构建 `WHERE column IS NULL` |

### 7.4 使用示例

```typescript
import { buildDrillConditionFromStatistic } from '@/framework/components/common/Portal/utils'

// 生命周期模块：点击"正式客户"卡片
const config = {
  body,                          // 原始 statistic body
  drillMetric: { conditionLabel: '正式客户' },
}

// CustomerDrilling.vue 中自动调用
const advanceCondition = buildDrillConditionFromStatistic(
  config.body,
  config.drillMetric
)
// 结果: { conditionList: [area='', plate='', dy='2026', customerStatus='4'], andOr: '0' }
```

### 7.5 接入规范

新增统计模块时，按以下步骤即可实现穿透：

1. **构建 statistic body** — 复用已有的 `buildXxxBody` 函数
2. **指定 drillMetric** — 根据点击场景设置 `conditionLabel` / `metricColumn` + `metric`
3. **输出配置** — 返回包含 `body` + `drillMetric` 的 `CustomerDrillingConfig`
4. **Portal 渲染** — `CustomerDrilling.vue` 自动调用 `buildDrillConditionFromStatistic` 生成 advanceCondition

无需手动提取 `conditionList`、无需手动拼装维度条件。
