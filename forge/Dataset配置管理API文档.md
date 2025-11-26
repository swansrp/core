# Dataset配置管理接口文档

## 功能概述

Dataset配置管理接口提供了通过SQL自动生成Dataset配置的功能，以及对列配置的增删改查操作。

## 基础URL

```
/web/dataset/config
```

---

## 接口列表

### 1. SQL解析与配置生成

#### 1.1 解析SQL（预览，不保存）

**接口**: `POST /web/dataset/config/parse`

**说明**: 将完整的SELECT SQL解析为Dataset配置，但不保存到数据库，用于预览。

**请求示例**:
```json
{
  "tableId": "order_summary",
  "dataSource": "default",
  "sql": "SELECT o.id, o.order_no AS orderNo, o.amount, c.name AS customerName, SUM(oi.quantity) AS totalQty FROM t_order o LEFT JOIN t_customer c ON o.customer_id = c.id LEFT JOIN t_order_item oi ON o.id = oi.order_id WHERE o.status = 1 GROUP BY o.id, o.order_no, o.amount, c.name"
}
```

**响应示例**:
```json
{
  "tableId": "order_summary",
  "datasets": [
    {
      "tableId": "order_summary",
      "datasetOrder": 1,
      "datasetSql": "t_order",
      "datasetAlias": "o",
      "joinType": null,
      "joinCondition": null,
      "remark": "主表"
    },
    {
      "tableId": "order_summary",
      "datasetOrder": 2,
      "datasetSql": "t_customer",
      "datasetAlias": "c",
      "joinType": "LEFT",
      "joinCondition": "o.customer_id = c.id",
      "remark": "关联表"
    },
    {
      "tableId": "order_summary",
      "datasetOrder": 3,
      "datasetSql": "t_order_item",
      "datasetAlias": "oi",
      "joinType": "LEFT",
      "joinCondition": "o.id = oi.order_id",
      "remark": "关联表"
    }
  ],
  "columns": [
    {
      "tableId": "order_summary",
      "columnSql": "o.id",
      "columnAlias": "id",
      "isAggregate": "N",
      "displayOrder": 1,
      "isVisible": "Y"
    },
    {
      "tableId": "order_summary",
      "columnSql": "o.order_no",
      "columnAlias": "orderNo",
      "isAggregate": "N",
      "displayOrder": 2,
      "isVisible": "Y"
    },
    {
      "tableId": "order_summary",
      "columnSql": "SUM(oi.quantity)",
      "columnAlias": "totalQty",
      "isAggregate": "Y",
      "displayOrder": 5,
      "isVisible": "Y"
    }
  ]
}
```

---

#### 1.2 解析SQL并替换配置

**接口**: `POST /web/dataset/config/replace`

**说明**: 将SQL解析为Dataset配置并保存到数据库，会删除并替换该tableId的原有配置。

**请求**: 同上

**响应**: 同上

---

### 2. 配置查询

#### 2.1 获取完整配置

**接口**: `GET /web/dataset/config?tableId={tableId}`

**说明**: 获取指定tableId的完整Dataset配置（包括datasets和columns）。

**响应示例**: 同上

---

#### 2.2 获取所有列配置

**接口**: `GET /web/dataset/config/column/list?tableId={tableId}`

**说明**: 仅获取指定tableId的列配置列表。

**响应示例**:
```json
[
  {
    "id": 1,
    "tableId": "order_summary",
    "columnSql": "o.id",
    "columnAlias": "id",
    "isAggregate": "N",
    "displayOrder": 1,
    "isVisible": "Y"
  }
]
```

---

#### 2.3 根据ID获取单个列配置

**接口**: `GET /web/dataset/config/column?id={id}`

**响应示例**:
```json
{
  "id": 1,
  "tableId": "order_summary",
  "columnSql": "o.id",
  "columnAlias": "id",
  "isAggregate": "N",
  "displayOrder": 1,
  "isVisible": "Y"
}
```

---

### 3. 列配置管理

#### 3.1 新增列配置

**接口**: `POST /web/dataset/config/column/add`

**请求示例**:
```json
{
  "tableId": "order_summary",
  "columnSql": "o.status",
  "columnAlias": "orderStatus",
  "isAggregate": "N",
  "displayOrder": 10,
  "isVisible": "Y",
  "remark": "订单状态"
}
```

---

#### 3.2 更新列配置

**接口**: `POST /web/dataset/config/column/update`

**请求示例**:
```json
{
  "id": 1,
  "tableId": "order_summary",
  "columnSql": "o.status",
  "columnAlias": "status",
  "isAggregate": "N",
  "displayOrder": 5,
  "isVisible": "N"
}
```

---

#### 3.3 删除列配置

**接口**: `POST /web/dataset/config/column/delete`

**请求示例**:
```json
{
  "id": "1"
}
```

---

#### 3.4 批量更新列显示顺序

**接口**: `POST /web/dataset/config/column/update/order`

**说明**: 用于拖拽排序后批量更新displayOrder。

**请求示例**:
```json
[
  {
    "id": "1",
    "showOrder": 3
  },
  {
    "id": "2",
    "showOrder": 1
  },
  {
    "id": "3",
    "showOrder": 2
  }
]
```

---

## 请求参数说明

### DatasetConfigReq (SQL解析请求)

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| tableId | String | 是 | 表格ID | "order_summary" |
| sql | String | 是 | 完整的SELECT SQL | "SELECT a.id FROM..." |
| dataSource | String | 否 | 数据源名称 | "default" |

### DatasetColumnReq (列配置请求)

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| id | Long | 更新时必填 | 主键ID | 1 |
| tableId | String | 是 | 表格ID | "order_summary" |
| columnSql | String | 是 | SQL表达式 | "SUM(amount)" |
| columnAlias | String | 是 | 字段别名 | "totalAmount" |
| isAggregate | String | 否 | 是否聚合字段 | "Y" / "N" |
| displayOrder | Integer | 否 | 显示顺序 | 1 |
| isVisible | String | 否 | 是否显示 | "Y" / "N" |
| remark | String | 否 | 备注 | "总金额" |

### IdOrderReqVO (顺序更新请求)

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| id | String | 是 | 记录ID | "1" |
| showOrder | Integer | 是 | 新的显示顺序 | 1 |

---

## 使用流程

### 方式1: 自动解析SQL创建配置

1. **编写SQL**: 在前端编写完整的SELECT SQL
2. **预览配置**: 调用 `POST /web/dataset/config/parse` 接口查看解析结果
3. **保存配置**: 确认无误后调用 `POST /web/dataset/config/replace` 保存
4. **调整配置**: 根据需要调用列配置管理接口进行微调

### 方式2: 手动创建列配置

1. **先创建Dataset**: 手动创建或通过SQL解析
2. **逐个添加列**: 调用 `POST /web/dataset/config/column/add` 接口逐个添加列
3. **调整顺序**: 调用 `POST /web/dataset/config/column/update/order` 调整显示顺序

---

## 注意事项

1. **SQL格式要求**:
   - 必须是标准的SELECT语句
   - 支持多表JOIN
   - 支持聚合函数（SUM、COUNT、MAX、MIN、AVG）
   - 必须使用表别名

2. **聚合字段识别**:
   - 系统会自动识别包含聚合函数的字段
   - 聚合字段的条件会放入HAVING子句
   - 非聚合字段的条件会放入WHERE子句

3. **配置替换**:
   - `POST /web/dataset/config/replace` 会删除原有配置
   - 如需保留原配置，请先备份

4. **字段别名**:
   - 建议在SQL中显式指定别名（AS xxx）
   - 别名将作为返回结果的字段名

---

## 前端集成示例

```typescript
// 1. SQL解析并保存
const parseSql = async (tableId: string, sql: string) => {
  const res = await post('/web/dataset/config/replace', {
    tableId,
    sql
  });
  console.log('生成配置:', res);
};

// 2. 获取列配置
const getColumns = async (tableId: string) => {
  return await get(`/web/dataset/config/column/list?tableId=${tableId}`);
};

// 3. 更新列配置
const updateColumn = async (column) => {
  await post('/web/dataset/config/column/update', column);
};

// 4. 拖拽排序
const updateOrder = async (columns) => {
  await post('/web/dataset/config/column/update/order', 
    columns.map((c, idx) => ({ id: String(c.id), showOrder: idx + 1 }))
  );
};
```

---

## 错误处理

所有接口在发生错误时都会抛出 `NoticeException`，前端应统一处理。

常见错误：
- "SQL解析失败": SQL语法错误或不支持的语法
- "列配置不存在": 更新或删除时ID不存在
- "tableId不能为空": 必填参数缺失
