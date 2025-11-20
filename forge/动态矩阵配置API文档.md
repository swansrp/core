# 动态矩阵配置功能 - 前端对接文档

## 功能概述

动态矩阵配置功能支持通过界面配置的方式动态创建数据库表和表单，无需编写代码即可实现数据的增删改查和表单联动。

### 核心能力

1. **矩阵管理** - 动态创建和管理数据库表
2. **字段配置** - 灵活配置表字段类型、索引、验证规则
3. **表单配置** - 可视化配置表单展示和验证
4. **联动逻辑** - 支持JavaScript脚本实现字段间的联动计算
5. **数据操作** - 对配置的矩阵进行增删改查

## API接口清单

### 1. 矩阵管理接口

#### 1.1 查询矩阵列表

```http
POST /web/forge/matrix/query
Content-Type: application/json

{
  "pageNum": 1,
  "pageSize": 10
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "total": 5,
    "list": [
      {
        "id": 1,
        "tableName": "eval_user_info",
        "tableComment": "用户评估信息",
        "dataSource": "master",
        "primaryKey": "id",
        "engine": "InnoDB",
        "charset": "utf8mb4",
        "status": "1",
        "sort": 1,
        "createAt": "2025-11-20",
        "createName": "张三"
      }
    ]
  }
}
```

#### 1.2 创建矩阵

```http
POST /web/forge/matrix/save
Content-Type: application/json

{
  "tableName": "eval_user_info",
  "tableComment": "用户评估信息",
  "dataSource": "master",
  "primaryKey": "id",
  "engine": "InnoDB",
  "charset": "utf8mb4",
  "sort": 1
}
```

#### 1.3 更新矩阵

```http
POST /web/forge/matrix/update
Content-Type: application/json

{
  "id": 1,
  "tableComment": "用户评估信息表",
  "sort": 2
}
```

#### 1.4 删除矩阵

```http
POST /web/forge/matrix/delete
Content-Type: application/json

{
  "ids": [1, 2, 3]
}
```

#### 1.5 创建物理表

```http
POST /web/forge/matrix/create-table?id=1
```

**说明**：根据配置的字段信息，在数据库中创建真实的表。

**创建内容**：
- 按照字段sort顺序创建所有字段
- 创建主键
- 创建配置的索引（普通索引、唯一索引）

**前置条件**：
- 矩阵状态必须为"未创建"（status=0）
- 至少配置一个字段

#### 1.6 同步表结构

```http
POST /web/forge/matrix/sync-table?id=1
```

**说明**：同步表结构变更，支持添加字段、调整字段顺序、管理索引。

**同步能力**：
- ✅ **添加新字段** - 按照sort顺序插入到正确位置
- ✅ **调整字段顺序** - 修改字段sort后，字段会移动到新位置
- ✅ **添加索引** - 为字段添加普通索引或唯一索引
- ✅ **删除索引** - 移除字段的索引配置
- ✅ **索引类型转换** - 普通索引和唯一索引相互转换

**前置条件**：
- 矩阵状态必须为"已创建"或"已同步"（status=1或2）

**安全保证**：
- 不会删除原有数据
- 不会删除已存在的字段
- 只添加/调整字段和索引

**操作示例**：

*示例1：添加新字段*
```javascript
// 1. 添加字段配置
POST /web/forge/matrix-column/save
{
  "matrixId": 1,
  "columnName": "phone",
  "sort": 3
}

// 2. 同步表结构
POST /web/forge/matrix/sync-table?id=1

// 结果：phone字段被添加到第3个位置
```

*示例2：调整字段顺序*
```javascript
// 1. 修改字段sort值
POST /web/forge/matrix-column/update
{
  "id": 2,
  "sort": 1  // 原来是3，改为1
}

// 2. 同步表结构
POST /web/forge/matrix/sync-table?id=1

// 结果：字段被移动到第1个位置
```

*示例3：为字段添加索引*
```javascript
// 1. 修改字段配置
POST /web/forge/matrix-column/update
{
  "id": 2,
  "isIndex": "1"  // 添加普通索引
}

// 2. 同步表结构
POST /web/forge/matrix/sync-table?id=1

// 结果：自动创建索引 idx_字段名
```

*示例4：删除索引*
```javascript
// 1. 修改字段配置
POST /web/forge/matrix-column/update
{
  "id": 2,
  "isIndex": "0"  // 移除索引
}

// 2. 同步表结构
POST /web/forge/matrix/sync-table?id=1

// 结果：删除索引 idx_字段名
```

### 2. 字段配置接口

#### 2.1 查询字段列表

```http
POST /web/forge/matrix-column/query
Content-Type: application/json

{
  "matrixId": 1,
  "pageNum": 1,
  "pageSize": 20
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "total": 10,
    "list": [
      {
        "id": 1,
        "matrixId": 1,
        "columnName": "user_name",
        "columnComment": "用户姓名",
        "columnType": "VARCHAR",
        "fieldType": "1",
        "columnLength": 50,
        "isNullable": "0",
        "isPrimaryKey": "0",
        "isIndex": "1",
        "isUnique": "0",
        "sort": 1
      }
    ]
  }
}
```

#### 2.2 添加字段

```http
POST /web/forge/matrix-column/save
Content-Type: application/json

{
  "matrixId": 1,
  "columnName": "user_name",
  "columnComment": "用户姓名",
  "columnType": "VARCHAR",
  "fieldType": "1",
  "columnLength": 50,
  "isNullable": "0",
  "isPrimaryKey": "0",
  "isIndex": "1",
  "isUnique": "0",
  "sort": 1
}
```

**字段配置说明**：

| 字段 | 说明 | 可选值 |
|------|------|--------|
| columnName | 字段名（英文下划线） | - |
| columnComment | 字段注释（中文说明） | - |
| columnType | 数据库字段类型 | VARCHAR, INT, BIGINT, DECIMAL, TEXT, DATE, DATETIME |
| fieldType | 表单字段类型 | 参考下方字段类型字典 |
| columnLength | 字段长度 | VARCHAR需要，INT/BIGINT不需要 |
| decimalPlaces | 小数位数 | DECIMAL类型需要 |
| isNullable | 是否可空 | 0=不可空, 1=可空 |
| isPrimaryKey | 是否主键 | 0=否, 1=是 |
| isIndex | 是否普通索引 | 0=否, 1=是 |
| isUnique | 是否唯一索引 | 0=否, 1=是 |
| sort | 字段顺序 | 数字，越小越靠前 |

**字段类型映射**：

| 数据库类型 | columnType | columnLength | decimalPlaces | 说明 |
|---------|-----------|--------------|---------------|------|
| 文本 | VARCHAR | 长度(如50) | - | 短文本 |
| 长文本 | TEXT | - | - | 长文本内容 |
| 整数 | INT | - | - | 整数 |
| 长整数 | BIGINT | - | - | 大整数/ID |
| 小数 | DECIMAL | 总位数(如10) | 小数位(如2) | 金额等 |
| 日期时间 | DATETIME | - | - | 日期时间 |
| 日期 | DATE | - | - | 仅日期 |

#### 2.3 批量调整字段顺序

```http
POST /web/forge/matrix-column/order
Content-Type: application/json

{
  "orders": [
    {"id": 1, "sort": 1},
    {"id": 2, "sort": 2},
    {"id": 3, "sort": 3}
  ]
}
```

### 3. 表单配置接口

#### 3.1 查询表单配置

```http
POST /web/forge/form-config/query
Content-Type: application/json

{
  "matrixId": 1,
  "pageNum": 1,
  "pageSize": 50
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "total": 5,
    "list": [
      {
        "id": 1,
        "matrixId": 1,
        "columnId": 1,
        "label": "用户姓名",
        "description": "请输入用户的真实姓名",
        "fieldType": "1",
        "isRequired": "1",
        "readonly": "0",
        "validationRule": "^[\\u4e00-\\u9fa5]{2,10}$",
        "width": 300,
        "positionX": 0,
        "positionY": 0,
        "sort": 1
      }
    ]
  }
}
```

#### 3.2 创建表单配置

```http
POST /web/forge/form-config/save
Content-Type: application/json

{
  "matrixId": 1,
  "columnId": 1,
  "label": "用户姓名",
  "description": "请输入用户的真实姓名",
  "fieldType": "1",
  "isRequired": "1",
  "readonly": "0",
  "validationRule": "^[\\u4e00-\\u9fa5]{2,10}$",
  "minValue": "",
  "maxValue": "",
  "width": 300,
  "height": 40,
  "positionX": 0,
  "positionY": 0,
  "sort": 1
}
```

**表单字段类型** (fieldType)：

| 值 | 类型 | 说明 | 配置项 |
|---|------|------|--------|
| 1 | 单行文本 | 普通文本输入 | validationRule |
| 2 | 真值 | 布尔值/开关 | - |
| 3 | 数字 | 数字输入 | minValue, maxValue |
| 4 | 下拉选择 | 单选下拉 | dict(字典) |
| 5 | 树形下拉 | 树形单选 | dict(字典) |
| 6 | 日期 | 日期选择器 | - |
| 7 | 日期时间 | 日期时间选择器 | - |
| 10 | 多行文本 | 文本域 | - |
| 12 | 图片 | 图片上传 | - |
| 15 | 文件 | 文件上传 | - |
| 16 | 货币 | 金额输入 | unit(单位转换) |
| 17 | 百分比 | 百分比输入 | unit(单位转换) |
| 18 | 下拉多选(逗号分隔) | 多选下拉 | dict(字典) |

### 4. 表单联动配置接口

#### 4.1 查询联动配置

```http
POST /web/forge/form-linkage/query
Content-Type: application/json

{
  "formConfigId": 1,
  "pageNum": 1,
  "pageSize": 20
}
```

#### 4.2 创建联动配置

```http
POST /web/forge/form-linkage/save
Content-Type: application/json

{
  "formConfigId": 1,
  "linkageName": "自动计算总分",
  "triggerEvent": "change",
  "conditionScript": "score1 != null && score2 != null",
  "actionScript": "totalScore = parseFloat(score1) + parseFloat(score2);",
  "targetFields": "totalScore",
  "priority": 1,
  "isEnabled": "1",
  "sort": 1
}
```

**触发事件类型**：
- `change` - 值改变时触发
- `blur` - 失去焦点时触发
- `focus` - 获得焦点时触发

**JavaScript脚本示例**：

```javascript
// 示例1：计算总分
// conditionScript
score1 != null && score2 != null && score3 != null

// actionScript
totalScore = parseFloat(score1) + parseFloat(score2) + parseFloat(score3);
avgScore = totalScore / 3;

// 示例2：根据年龄判断类别
// conditionScript
age != null

// actionScript
if (age < 18) {
  ageGroup = '未成年';
} else if (age < 60) {
  ageGroup = '成年人';
} else {
  ageGroup = '老年人';
}

// 示例3：价格折扣计算
// conditionScript
originalPrice != null && discountRate != null

// actionScript
finalPrice = originalPrice * (1 - discountRate / 100);
discountAmount = originalPrice - finalPrice;
```

#### 4.3 执行表单联动

```http
POST /web/forge/form-linkage/execute/1
Content-Type: application/json

{
  "score1": 85,
  "score2": 90,
  "score3": 88,
  "totalScore": 0,
  "avgScore": 0
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "score1": 85,
    "score2": 90,
    "score3": 88,
    "totalScore": 263,
    "avgScore": 87.67
  }
}
```

### 5. 矩阵数据操作接口

#### 5.1 插入数据

```http
POST /web/forge/matrix-data/insert/1
Content-Type: application/json

{
  "user_name": "张三",
  "age": 25,
  "email": "zhangsan@example.com",
  "score": 85.5
}
```

**响应**：
```json
{
  "code": 200,
  "data": 1,
  "message": "操作成功"
}
```

#### 5.2 更新数据

```http
POST /web/forge/matrix-data/update/1/123
Content-Type: application/json

{
  "user_name": "李四",
  "score": 90
}
```

**说明**：路径中 `1` 是矩阵ID，`123` 是记录的主键值。

#### 5.3 删除数据

```http
DELETE /web/forge/matrix-data/delete/1/123
```

#### 5.4 查询单条数据

```http
GET /web/forge/matrix-data/select/1/123
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "id": 123,
    "user_name": "张三",
    "age": 25,
    "email": "zhangsan@example.com",
    "score": 85.5,
    "create_at": "2025-11-20 10:30:00",
    "create_by": "admin"
  }
}
```

#### 5.5 查询列表

```http
GET /web/forge/matrix-data/select-list/1
```

**响应**：
```json
{
  "code": 200,
  "data": [
    {
      "id": 123,
      "user_name": "张三",
      "age": 25
    },
    {
      "id": 124,
      "user_name": "李四",
      "age": 26
    }
  ]
}
```

#### 5.6 条件查询

```http
POST /web/forge/matrix-data/select-by-condition/1
Content-Type: application/json

{
  "age": 25,
  "user_name": "张三"
}
```

## 前端实现流程

### 流程1：创建新矩阵

```
1. 用户填写矩阵基本信息
   ↓
2. 调用 /web/forge/matrix/save 创建矩阵
   ↓
3. 配置字段列表（调用 /web/forge/matrix-column/save 添加字段）
   ↓
4. 调用 /web/forge/matrix/create-table/{id} 创建物理表
   ↓
5. 完成
```

### 流程2：配置表单

```
1. 选择已创建的矩阵
   ↓
2. 查询矩阵的字段列表 (/web/forge/matrix-column/query)
   ↓
3. 为每个字段创建表单配置 (/web/forge/form-config/save)
   - 设置显示标签
   - 设置字段类型
   - 设置验证规则
   - 设置布局位置
   ↓
4. 配置字段间的联动关系 (/web/forge/form-linkage/save)
   ↓
5. 完成
```

### 流程3：使用表单填写数据

```
1. 加载表单配置 (/web/forge/form-config/query)
   ↓
2. 渲染动态表单界面
   ↓
3. 用户填写表单，触发联动事件
   ↓
4. 调用 /web/forge/form-linkage/execute/{formConfigId} 执行联动计算
   ↓
5. 更新表单字段值
   ↓
6. 提交表单，调用 /web/forge/matrix-data/insert/{matrixId}
   ↓
7. 完成
```

## 界面设计建议

### 1. 矩阵管理界面

**功能模块**：
- 矩阵列表展示（表格）
- 新建矩阵按钮
- 编辑/删除操作
- 字段配置入口
- 创建物理表按钮
- 状态标识（未创建/已创建/已同步）

**列表字段**：
- 表名
- 表注释
- 字段数量
- 状态
- 创建时间
- 操作按钮

### 2. 字段配置界面

**功能模块**：
- 字段列表（可拖拽排序）
- 添加字段表单
- 字段类型选择器
- 索引配置
- 预览DDL语句

**表单项**：
- 字段名（英文，下划线命名）
- 字段注释（中文说明）
- 字段类型（下拉选择）
- 字段长度（根据类型显示）
- 是否可空
- 是否主键
- 是否索引
- 是否唯一
- 默认值

### 3. 表单配置界面

**功能模块**：
- 可视化表单设计器
- 字段拖拽布局
- 属性配置面板
- 实时预览

**配置面板**：
- 显示标签
- 字段类型（自动映射或手动选择）
- 是否必填
- 是否只读
- 验证规则
- 最大值/最小值
- 默认值
- 布局位置（x, y坐标）
- 尺寸（width, height）

### 4. 联动配置界面

**功能模块**：
- 联动规则列表
- 联动规则编辑器
- JavaScript代码编辑器（带语法高亮）
- 测试工具

**编辑器**：
- 联动名称
- 触发字段选择
- 触发事件选择
- 条件脚本编辑器
- 执行脚本编辑器
- 目标字段选择（多选）
- 优先级设置
- 启用/禁用开关

### 5. 数据管理界面

**功能模块**：
- 动态表格（根据字段配置生成）
- 搜索/筛选
- 新增/编辑/删除
- 批量操作
- 导入/导出

**动态表单**：
- 根据表单配置自动渲染
- 支持所有字段类型
- 实时联动计算
- 验证提示

## 数据字典

### 矩阵状态 (status)

| 值 | 说明 |
|---|------|
| 0 | 未创建（仅配置） |
| 1 | 已创建（物理表已创建） |
| 2 | 已同步（字段已同步） |

### 是否标识 (is_*)

| 值 | 说明 |
|---|------|
| 0 | 否 |
| 1 | 是 |

### 字段类型字典

参考 `PortalFieldDict` 枚举：

```java
STRING("1", "单行文本")
BOOLEAN("2", "真值")
NUMBER("3", "数字")
ENUM("4", "下拉选择")
TREE("5", "树形下拉选择")
DATE("6", "日期")
DATETIME("7", "日期时间")
LINK("8", "超链接")
HTML("9", "富文本")
TEXT("10", "多行文本")
ENTITY("11", "关联实体")
IMAGE("12", "图片")
VIDEO("13", "视频")
AUDIO("14", "音频")
FILE("15", "文件")
MONEY("16", "货币")
PERCENT("17", "百分比")
ENUM_MULTI_IN_ONE("18", "下拉选择(逗号隔开)")
TREE_MULTI_IN_ONE("19", "树形下拉选择(逗号隔开)")
ENTITY_CONDITION("20", "实体条件")
```

## 注意事项

### 1. 表名规范
- 使用小写字母和下划线
- 建议格式：`业务模块_功能描述`
- 示例：`eval_user_info`, `order_detail`

### 2. 字段名规范
- 使用小写字母和下划线
- 避免使用MySQL关键字（如：name, order, group等）
- 如必须使用关键字，系统会自动添加反引号

### 3. 字段顺序（sort）
- **创建表时**：字段按sort值顺序创建
- **同步表时**：修改sort值后，字段会移动到新位置
- **性能影响**：`MODIFY COLUMN`会锁表，大表操作较慢
- **建议**：在低峰期执行字段重排序

### 4. 索引配置

#### 4.1 索引类型
- **普通索引**：`isIndex="1"` - 命名规则：`idx_字段名`
- **唯一索引**：`isUnique="1"` - 命名规则：`uk_字段名`
- **主键索引**：`PRIMARY` - 创建表时指定，不参与同步

#### 4.2 索引同步
- **添加索引**：设置`isIndex="1"`或`isUnique="1"`，同步后自动创建
- **删除索引**：设置`isIndex="0"`或`isUnique="0"`，同步后自动删除
- **类型转换**：修改索引类型，同步时先删旧索引再建新索引

#### 4.3 索引注意事项
- ✅ **安全操作**：删除索引不影响数据，可放心操作
- ⚠️ **性能影响**：删除索引可能影响查询性能
- ⚠️ **唯一约束**：添加唯一索引前，确保字段数据没有重复值
- 🚫 **禁止**：不要手动创建`idx_*`或`uk_*`命名的索引，由系统自动管理

### 5. 同步表结构安全性

#### 5.1 会执行的操作
- ✅ 添加新字段（`ALTER TABLE ADD COLUMN`）
- ✅ 调整字段顺序（`ALTER TABLE MODIFY COLUMN ... AFTER`）
- ✅ 添加索引（`ALTER TABLE ADD INDEX`）
- ✅ 删除索引（`ALTER TABLE DROP INDEX`）

#### 5.2 不会执行的操作
- ❌ 删除字段
- ❌ 修改字段类型
- ❌ 删除数据
- ❌ 清空表

#### 5.3 数据安全保证
- 🔒 **原有数据完全保留**：不会丢失任何数据
- 🔒 **已存在字段保留**：不会删除已有字段
- 🔒 **只增不减**：只添加新功能，不破坏现有功能

### 6. 联动脚本
- 使用标准JavaScript语法
- 可访问表单中所有字段（通过字段名）
- 避免使用浏览器API（如window, document等）
- 脚本在服务端执行，确保安全性

### 7. 数据类型选择
- 主键建议使用 BIGINT + 自增
- 金额建议使用 DECIMAL(19,2)
- 百分比建议使用 DECIMAL(5,2)
- 时间戳建议使用 DATETIME

### 8. 性能建议
- 为常用查询字段添加索引
- 避免在大文本字段上创建索引
- 合理设置字符串字段长度
- 定期同步表结构（添加新字段时）
- 大表操作建议在低峰期执行

## 错误码说明

| 错误码 | 说明 | 处理方式 |
|-------|------|---------|
| 400 | 参数错误 | 检查请求参数格式 |
| 404 | 资源不存在 | 检查矩阵ID或字段ID |
| 500 | 服务器错误 | 联系后端排查 |
| 1001 | 矩阵配置不存在 | 检查矩阵ID是否正确 |
| 1002 | 表已创建，无法重复创建 | 使用同步功能而非创建 |
| 1003 | 表未创建，请先创建表 | 先调用创建物理表接口 |
| 1004 | 请先配置表字段 | 至少添加一个字段后再创建表 |
| 2001 | 联动脚本执行失败 | 检查JavaScript语法 |

## 完整示例

### 示例：创建用户评估表单

#### Step 1: 创建矩阵

```javascript
// POST /web/forge/matrix/save
{
  "tableName": "eval_user_score",
  "tableComment": "用户评估得分表",
  "primaryKey": "id",
  "engine": "InnoDB",
  "charset": "utf8mb4",
  "sort": 1
}
```

#### Step 2: 添加字段

```javascript
// 添加主键字段
// POST /web/forge/matrix-column/save
{
  "matrixId": 1,
  "columnName": "id",
  "columnType": "BIGINT",
  "isPrimaryKey": "1",
  "isNullable": "0",
  "sort": 1
}

// 添加用户名字段
{
  "matrixId": 1,
  "columnName": "user_name",
  "columnComment": "用户姓名",
  "columnType": "VARCHAR",
  "fieldType": "1",
  "columnLength": 50,
  "isNullable": "0",
  "isIndex": "1",
  "sort": 2
}

// 添加分数字段
{
  "matrixId": 1,
  "columnName": "score1",
  "columnComment": "分数1",
  "columnType": "DECIMAL",
  "columnLength": 10,
  "decimalPlaces": 2,
  "fieldType": "3",
  "sort": 3
}

{
  "matrixId": 1,
  "columnName": "score2",
  "columnComment": "分数2",
  "columnType": "DECIMAL",
  "columnLength": 10,
  "decimalPlaces": 2,
  "fieldType": "3",
  "sort": 4
}

{
  "matrixId": 1,
  "columnName": "total_score",
  "columnComment": "总分",
  "columnType": "DECIMAL",
  "columnLength": 10,
  "decimalPlaces": 2,
  "fieldType": "3",
  "sort": 5
}
```

#### Step 3: 创建物理表

```javascript
// POST /web/forge/matrix/create-table/1
```

#### Step 4: 配置表单

```javascript
// 配置用户名表单
// POST /web/forge/form-config/save
{
  "matrixId": 1,
  "columnId": 2,
  "label": "用户姓名",
  "fieldType": "1",
  "isRequired": "1",
  "validationRule": "^[\\u4e00-\\u9fa5]{2,10}$",
  "sort": 1
}

// 配置分数1表单
{
  "matrixId": 1,
  "columnId": 3,
  "label": "分数1",
  "fieldType": "3",
  "minValue": "0",
  "maxValue": "100",
  "isRequired": "1",
  "sort": 2
}

// 配置分数2表单
{
  "matrixId": 1,
  "columnId": 4,
  "label": "分数2",
  "fieldType": "3",
  "minValue": "0",
  "maxValue": "100",
  "isRequired": "1",
  "sort": 3
}

// 配置总分表单（只读）
{
  "matrixId": 1,
  "columnId": 5,
  "label": "总分",
  "fieldType": "3",
  "readonly": "1",
  "sort": 4
}
```

#### Step 5: 配置联动

```javascript
// POST /web/forge/form-linkage/save
{
  "formConfigId": 2,  // 分数1的表单配置ID
  "linkageName": "自动计算总分",
  "triggerEvent": "change",
  "conditionScript": "score1 != null && score2 != null",
  "actionScript": "total_score = parseFloat(score1) + parseFloat(score2);",
  "targetFields": "total_score",
  "priority": 1,
  "isEnabled": "1",
  "sort": 1
}
```

#### Step 6: 填写表单并提交

```javascript
// 1. 用户填写表单时触发联动
// POST /web/forge/form-linkage/execute/2
{
  "user_name": "张三",
  "score1": 85,
  "score2": 90,
  "total_score": 0
}

// 响应：
{
  "user_name": "张三",
  "score1": 85,
  "score2": 90,
  "total_score": 175
}

// 2. 提交数据
// POST /web/forge/matrix-data/insert/1
{
  "user_name": "张三",
  "score1": 85,
  "score2": 90,
  "total_score": 175
}
```

## Swagger文档

完整的API文档可通过Swagger访问：

```
http://localhost:8080/swagger-ui.html
```

搜索标签：
- `动态配置 - 矩阵管理`
- `动态配置 - 矩阵字段管理`
- `动态配置 - 动态表单配置`
- `动态配置 - 表单联动配置`
- `动态配置 - 矩阵数据操作`

## 技术支持

如有问题请联系后端开发团队。
