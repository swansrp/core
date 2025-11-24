# Forge 模块功能说明文档

## 模块概述

**Forge** 是一个**低代码数据建模与表单配置引擎**，提供通过可视化配置方式动态创建数据库表、配置表单和实现业务逻辑的能力。无需编写代码即可实现完整的数据管理功能。

### 核心价值

- 🚀 **快速开发** - 通过配置而非编码，大幅缩短开发周期
- 🔄 **灵活变更** - 支持表结构和表单在线调整，无需发版
- 🎨 **可视化配置** - 所见即所得的表单设计体验
- 🔗 **智能联动** - JavaScript脚本实现复杂的字段联动逻辑
- 📊 **数据追踪** - 完整的表结构变更历史记录

---

## 功能架构

```
Forge 模块
├── 矩阵管理 (Matrix Management)
│   ├── 矩阵配置 - 定义数据库表的元信息
│   ├── 物理建表 - 根据配置创建真实数据库表
│   ├── 结构同步 - 自动同步表结构变更
│   └── 变更追踪 - 记录所有DDL操作历史
│
├── 字段配置 (Column Configuration)
│   ├── 字段定义 - 配置字段类型、长度、约束
│   ├── 索引管理 - 配置普通索引和唯一索引
│   ├── 顺序调整 - 灵活调整字段在表中的位置
│   └── 数据校验 - 删除前检查字段是否有数据
│
├── 表单配置 (Form Configuration)
│   ├── 字段渲染 - 配置表单字段的展示方式
│   ├── 验证规则 - 正则表达式、范围等验证
│   ├── 布局设计 - 可视化拖拽布局
│   └── 字段属性 - 必填、只读、默认值等
│
├── 联动配置 (Linkage Configuration)
│   ├── 触发条件 - 定义何时触发联动
│   ├── 脚本执行 - JavaScript脚本计算逻辑
│   ├── 目标更新 - 自动更新关联字段
│   └── 优先级管理 - 控制多个联动的执行顺序
│
└── 数据操作 (Data Operation)
    ├── 增删改查 - 动态表的标准CRUD操作
    ├── 条件查询 - 灵活的数据筛选
    └── 批量操作 - 支持批量数据处理
```

---

## 核心功能详解

### 1. 矩阵管理 (SysMatrix)

**功能描述**：矩阵是动态表的配置中心，定义了数据库表的所有元信息。

#### 1.1 主要能力

| 能力 | 说明 | 使用场景 |
|------|------|----------|
| **创建矩阵** | 定义表名、注释、引擎等基础信息 | 新建业务数据表 |
| **物理建表** | 根据字段配置在数据库中创建真实的表 | 配置完成后一键建表 |
| **同步表结构** | 自动检测配置变更并同步到数据库 | 字段调整后同步 |
| **修改表注释** | 更新矩阵注释时自动同步到数据库 | 修改表说明、优化文档 |
| **清空数据** | Truncate表数据(超级管理员权限) | 测试环境重置数据 |
| **DDL导出** | 导出矩阵的CREATE TABLE语句 | 跨环境迁移、备份表结构 |
| **DDL导入** | 解析DDL语句快速创建矩阵配置 | 快速复制表结构、批量导入 |
| **变更日志导出** | 导出矩阵的完整变更历史和配置 | 跨环境配置同步 |
| **变更日志导入** | 导入变更历史并应用到目标环境 | 生产环境同步预生产配置 |
| **删除保护** | 有数据时禁止删除矩阵配置 | 防止误删重要配置 |
| **变更记录** | 记录所有DDL操作的执行历史 | 审计和问题排查 |

#### 1.2 核心字段

```java
SysMatrix {
    Long id;                    // 主键
    String tableName;           // 表名（物理表名）
    String tableComment;        // 表注释
    String dataSource;          // 数据源（支持多数据源）
    String primaryKey;          // 主键字段名（默认id）
    String engine;              // 存储引擎（InnoDB/MyISAM）
    String charset;             // 字符集（utf8mb4）
    String status;              // 状态（0未创建/1已创建/2已同步/3待同步）
    Integer sort;               // 排序
}
```

#### 1.3 状态流转

```
未创建(0)
   ↓ [创建物理表] createPhysicalTable()
已创建(1)
   ↓ [同步表结构] syncTableStructure()
已同步(2)
   ↓ [修改字段配置] afterAdd/afterUpdate/afterDelete
待同步(3)
   ↓ [再次同步] syncTableStructure()
已同步(2)
```

#### 1.4 特殊功能

**自动创建审计字段**：
每个矩阵表自动包含5个审计字段（不存入SysMatrixColumn配置）：
- `create_by` - 创建者
- `create_at` - 创建时间
- `update_by` - 更新者
- `update_at` - 更新时间（自动创建索引 `idx_update_at`）
- `valid` - 有效性（软删除标记）

**唯一索引包含valid字段**：
表名唯一索引为 `uk_table_name_valid(table_name, valid)`，支持软删除后重建同名表。

**自动创建ID主键**：
新建矩阵时自动创建主键列：
- 字段名：`id`
- 类型：`BIGINT(20)`
- 属性：`NOT NULL AUTO_INCREMENT PRIMARY KEY`
- 排序：`sort=0`（第一位）

---

### 2. 字段配置 (SysMatrixColumn)

**功能描述**：定义矩阵表的字段结构，包括数据类型、约束、索引等。

#### 2.1 主要能力

| 能力 | 说明 | 同步行为 |
|------|------|---------|
| **添加字段** | 配置新字段的所有属性 | ✅ 同步时执行 `ALTER TABLE ADD COLUMN` |
| **调整顺序** | 修改字段的sort值调整位置 | ✅ 同步时执行 `MODIFY COLUMN ... AFTER` |
| **索引管理** | 配置普通索引和唯一索引 | ✅ 同步时自动创建/删除索引 |
| **删除字段** | 删除字段配置 | ✅ 同步时执行 `DROP COLUMN` |
| **数据检查** | 删除前检查字段是否有数据 | ⚠️ 有数据则阻止删除 |
| **状态联动** | 字段变更自动标记矩阵为待同步 | 🔄 触发状态流转 |

#### 2.2 核心字段

```java
SysMatrixColumn {
    Long id;                    // 主键
    Long matrixId;              // 所属矩阵ID
    String columnName;          // 字段名（下划线命名）
    String columnComment;       // 字段注释
    String columnType;          // 数据库类型（VARCHAR/INT/DECIMAL等）
    String fieldType;           // 表单字段类型（参考PortalFieldDict）
    Integer columnLength;       // 字段长度
    Integer decimalPlaces;      // 小数位数（DECIMAL专用）
    String isNullable;          // 是否可空（0否/1是）
    String isPrimaryKey;        // 是否主键（0否/1是）
    String isIndex;             // 是否普通索引（0否/1是）
    String isUnique;            // 是否唯一索引（0否/1是）
    String defaultValue;        // 默认值（主键为序列时存序列名）
    Integer sort;               // 字段顺序（数字越小越靠前）
}
```

#### 2.3 数据类型映射

| 业务场景 | columnType | columnLength | decimalPlaces | 示例 |
|---------|-----------|--------------|---------------|------|
| 姓名/编号 | VARCHAR | 50 | - | `user_name VARCHAR(50)` |
| 长文本 | TEXT | - | - | `description TEXT` |
| 年龄/数量 | INT | - | - | `age INT` |
| 主键ID | BIGINT | 20 | - | `id BIGINT(20)` |
| 金额 | DECIMAL | 19 | 2 | `amount DECIMAL(19,2)` |
| 百分比 | DECIMAL | 5 | 2 | `rate DECIMAL(5,2)` |
| 日期时间 | DATETIME | - | - | `create_at DATETIME` |
| 日期 | DATE | - | - | `birth_date DATE` |

#### 2.4 主键类型处理

**数字自增主键**（推荐）：
```java
// 配置
columnType = "BIGINT"
isPrimaryKey = "1"
defaultValue = null

// 生成DDL
`id` BIGINT(20) NOT NULL AUTO_INCREMENT
```

**字符串序列主键**：
```java
// 配置
columnType = "VARCHAR"
columnLength = 50
isPrimaryKey = "1"
defaultValue = "SEQ_USER_ID"  // 序列名

// 生成DDL（不添加AUTO_INCREMENT）
`id` VARCHAR(50) NOT NULL

// 插入数据时
String seq = saSequenceService.getSeq("SEQ_USER_ID");
// 使用seq作为主键值
```

#### 2.5 索引命名规范

| 索引类型 | 配置 | 命名规则 | 示例 |
|---------|------|---------|------|
| 主键索引 | isPrimaryKey="1" | PRIMARY | `PRIMARY KEY (id)` |
| 普通索引 | isIndex="1" | idx_字段名 | `KEY idx_user_name (user_name)` |
| 唯一索引 | isUnique="1" | uk_字段名 | `UNIQUE KEY uk_email (email)` |
| 审计索引 | 自动创建 | idx_update_at | `KEY idx_update_at (update_at)` |

---

### 3. 表单配置 (SysFormConfig)

**功能描述**：定义字段在表单中的展示方式、验证规则和布局。

#### 3.1 主要能力

| 能力 | 说明 | 配置项 |
|------|------|--------|
| **字段渲染** | 配置表单组件类型 | fieldType（18种类型） |
| **验证规则** | 正则表达式验证 | validationRule |
| **范围限制** | 数字、日期范围 | minValue, maxValue |
| **布局设计** | 可视化拖拽布局 | positionX, positionY, width, height |
| **字段属性** | 必填、只读、显示隐藏 | isRequired, readonly, hidden |
| **字典关联** | 下拉选择数据源 | dict |
| **单位转换** | 货币、百分比转换 | unit |

#### 3.2 核心字段

```java
SysFormConfig {
    Long id;                    // 主键
    Long matrixId;              // 所属矩阵ID
    Long columnId;              // 关联字段ID
    String label;               // 显示标签
    String description;         // 字段说明/提示
    String fieldType;           // 字段类型（参考下方字典）
    String isRequired;          // 是否必填（0否/1是）
    String readonly;            // 是否只读（0否/1是）
    String hidden;              // 是否隐藏（0否/1是）
    String validationRule;      // 验证规则（正则表达式）
    String minValue;            // 最小值
    String maxValue;            // 最大值
    String defaultValue;        // 默认值
    String dict;                // 字典数据源
    String unit;                // 单位转换配置
    Integer width;              // 宽度
    Integer height;             // 高度
    Integer positionX;          // X坐标
    Integer positionY;          // Y坐标
    Integer sort;               // 排序
}
```

#### 3.3 字段类型详解 (PortalFieldDict)

| 值 | 类型 | 说明 | 适用场景 | 关键配置 |
|---|------|------|---------|---------|
| 1 | 单行文本 | Input文本框 | 姓名、编号 | validationRule |
| 2 | 真值 | Switch开关 | 是否启用 | - |
| 3 | 数字 | InputNumber | 年龄、数量 | minValue, maxValue |
| 4 | 下拉选择 | Select单选 | 性别、状态 | dict |
| 5 | 树形下拉 | TreeSelect | 组织架构 | dict |
| 6 | 日期 | DatePicker | 出生日期 | - |
| 7 | 日期时间 | DateTimePicker | 创建时间 | - |
| 10 | 多行文本 | Textarea | 备注、描述 | - |
| 12 | 图片 | ImageUpload | 头像、证件 | - |
| 15 | 文件 | FileUpload | 附件 | - |
| 16 | 货币 | MoneyInput | 金额 | unit(元/分转换) |
| 17 | 百分比 | PercentInput | 比率 | unit(小数/百分比) |
| 18 | 下拉多选 | Select多选 | 标签、权限 | dict |

#### 3.4 验证规则示例

``javascript
// 中文姓名（2-10个字符）
validationRule: "^[\\u4e00-\\u9fa5]{2,10}$"

// 手机号
validationRule: "^1[3-9]\\d{9}$"

// 邮箱
validationRule: "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$"

// 身份证号
validationRule: "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$"

// 数字范围（通过minValue/maxValue更合适）
validationRule: "^[1-9]\\d*$"  // 正整数
```

---

### 4. 联动配置 (SysFormLinkage)

**功能描述**：通过JavaScript脚本实现字段间的自动计算和联动逻辑。

#### 4.1 主要能力

| 能力 | 说明 | 示例 |
|------|------|------|
| **值监听** | 监听字段值变化 | score1改变时触发 |
| **条件判断** | 满足条件才执行 | `score1 != null && score2 != null` |
| **自动计算** | JavaScript计算逻辑 | `total = score1 + score2` |
| **多字段更新** | 一次更新多个字段 | 同时更新total和average |
| **优先级控制** | 控制执行顺序 | priority值越小越先执行 |
| **启用开关** | 动态启用/禁用联动 | isEnabled切换 |

#### 4.2 核心字段

```java
SysFormLinkage {
    Long id;                    // 主键
    Long formConfigId;          // 所属表单配置ID
    String linkageName;         // 联动名称
    String triggerEvent;        // 触发事件（change/blur/focus）
    String conditionScript;     // 条件脚本（JavaScript）
    String actionScript;        // 执行脚本（JavaScript）
    String targetFields;        // 目标字段（逗号分隔）
    Integer priority;           // 优先级（数字越小越先执行）
    String isEnabled;           // 是否启用（0否/1是）
    Integer sort;               // 排序
}
```

#### 4.3 触发事件

| 事件 | 说明 | 使用场景 |
|------|------|---------|
| change | 值改变时触发 | 实时计算、联动查询 |
| blur | 失去焦点时触发 | 格式化、验证 |
| focus | 获得焦点时触发 | 显示提示、预加载 |

#### 4.4 脚本编写规范

**可用变量**：
- 所有表单字段（通过字段名直接访问）
- 标准JavaScript语法和内置对象

**注意事项**：
- ❌ 不可使用浏览器API（window, document等）
- ❌ 不可访问外部资源（Ajax, Fetch等）
- ✅ 支持Math、Date等内置对象
- ✅ 支持标准运算符和控制结构

#### 4.5 经典场景示例

**场景1：总分计算**
```javascript
// linkageName: "自动计算总分和平均分"
// triggerEvent: "change"

// conditionScript - 所有分数都已填写
score1 != null && score2 != null && score3 != null

// actionScript - 计算总分和平均分
totalScore = parseFloat(score1) + parseFloat(score2) + parseFloat(score3);
avgScore = (totalScore / 3).toFixed(2);

// targetFields - 更新的字段
totalScore,avgScore
```

**场景2：价格折扣计算**
```javascript
// linkageName: "自动计算优惠后价格"
// triggerEvent: "change"

// conditionScript
originalPrice != null && discountRate != null

// actionScript
finalPrice = (originalPrice * (1 - discountRate / 100)).toFixed(2);
discountAmount = (originalPrice - finalPrice).toFixed(2);

// targetFields
finalPrice,discountAmount
```

**场景3：根据年龄判断类别**
```javascript
// linkageName: "年龄段自动分类"
// triggerEvent: "blur"

// conditionScript
age != null && age > 0

// actionScript
if (age < 18) {
    ageGroup = '未成年';
    canVote = '0';
} else if (age < 60) {
    ageGroup = '成年人';
    canVote = '1';
} else {
    ageGroup = '老年人';
    canVote = '1';
}

// targetFields
ageGroup,canVote
```

**场景4：BMI计算**
```javascript
// linkageName: "BMI指数计算"
// triggerEvent: "change"

// conditionScript
height != null && weight != null && height > 0

// actionScript
bmi = (weight / Math.pow(height / 100, 2)).toFixed(2);
if (bmi < 18.5) {
    bmiLevel = '偏瘦';
} else if (bmi < 24) {
    bmiLevel = '正常';
} else if (bmi < 28) {
    bmiLevel = '偏胖';
} else {
    bmiLevel = '肥胖';
}

// targetFields
bmi,bmiLevel
```

**场景5：日期范围验证**
```javascript
// linkageName: "结束日期不能早于开始日期"
// triggerEvent: "change"

// conditionScript
startDate != null && endDate != null

// actionScript
if (new Date(endDate) < new Date(startDate)) {
    endDate = startDate;
    dateError = '结束日期不能早于开始日期';
} else {
    dateError = '';
}

// targetFields
endDate,dateError
```

---

### 5. 变更日志 (SysMatrixChangeLog)

**功能描述**：记录所有表结构变更的DDL操作历史，提供完整的审计追踪。

#### 5.1 核心字段

```java
SysMatrixChangeLog {
    Long id;                    // 主键
    Long matrixId;              // 所属矩阵ID
    Integer version;            // 版本号（自动递增）
    String changeType;          // 变更类型（参考下方字典）
    String changeDesc;          // 变更描述
    String ddlStatement;        // DDL语句
    String affectedColumn;      // 影响的字段名
    String executeStatus;       // 执行状态（0失败/1成功）
    String errorMsg;            // 错误信息
    Integer sort;               // 排序
    String createBy;            // 创建者
    Date createAt;              // 创建时间
}
```

#### 5.2 变更类型字典 (MatrixChangeTypeDict)

| 值 | 类型 | 说明 | 示例DDL |
|---|------|------|---------|
| 1 | CREATE_TABLE | 创建表 | `CREATE TABLE eval_user ...` |
| 2 | ADD_COLUMN | 添加字段 | `ALTER TABLE ... ADD COLUMN age INT` |
| 3 | MODIFY_COLUMN | 调整字段顺序 | `ALTER TABLE ... MODIFY COLUMN age INT AFTER name` |
| 4 | ADD_INDEX | 添加索引 | `ALTER TABLE ... ADD INDEX idx_age (age)` |
| 5 | DROP_INDEX | 删除索引 | `ALTER TABLE ... DROP INDEX idx_age` |
| 6 | DROP_COLUMN | 删除字段 | `ALTER TABLE ... DROP COLUMN age` |
| 7 | MODIFY_TABLE_COMMENT | 修改表注释 | `ALTER TABLE ... COMMENT '新注释'` |
| 7 | MODIFY_TABLE_COMMENT | 修改表注释 | `ALTER TABLE ... COMMENT '新注释'` |

#### 5.3 版本管理

**版本号规则**：
- 每次同步操作分配一个新版本号
- 版本号从1开始自动递增
- 同一版本可能包含多条变更记录

**示例**：
```
Version 1:
  - CREATE_TABLE: 创建表 eval_user
  
Version 2:
  - ADD_COLUMN: 添加字段 phone
  - ADD_COLUMN: 添加字段 email
  - ADD_INDEX: 添加索引 idx_phone
  
Version 3:
  - MODIFY_COLUMN: 调整字段顺序 email
  - DROP_INDEX: 删除索引 idx_phone
  - DROP_COLUMN: 删除字段 phone
```

#### 5.4 执行状态

| 状态 | 说明 | 处理建议 |
|------|------|---------|
| 0 | 失败 | 查看errorMsg，修正配置后重试 |
| 1 | 成功 | 正常，无需处理 |

---

### 6. 数据操作 (MatrixDataService)

**功能描述**：对动态创建的矩阵表进行标准CRUD操作。

#### 6.1 主要接口

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| insert | 插入数据 | matrixId, data | 影响行数 |
| update | 更新数据 | matrixId, id, data | 影响行数 |
| delete | 删除数据 | matrixId, id | 影响行数 |
| selectById | 查询单条 | matrixId, id | 数据Map |
| selectList | 查询列表 | matrixId | 数据List |
| selectByCondition | 条件查询 | matrixId, condition | 数据List |

#### 6.2 状态检查

所有数据操作前都会检查矩阵状态：
```java
// 允许操作的状态
MatrixStatusDict.CREATED      // 已创建(1)
MatrixStatusDict.SYNCED       // 已同步(2)
MatrixStatusDict.PENDING_SYNC // 待同步(3)

// 禁止操作的状态
MatrixStatusDict.NOT_CREATED  // 未创建(0) - 抛出异常
```

#### 6.3 多数据源支持

- 支持切换到配置的数据源执行操作
- 操作完成后自动重置回默认数据源
- 保证数据源隔离和安全性

---

## 技术实现

### 1. 项目结构

```
core/forge/
├── constant/dict/              # 字典枚举
│   ├── MatrixStatusDict.java      # 矩阵状态字典
│   ├── MatrixChangeTypeDict.java  # 变更类型字典
│   └── JoinTypeDict.java          # 关联类型字典
│
├── controller/                 # 控制器层
│   ├── MatrixDataController.java          # 数据操作控制器
│   ├── matrix/                            # 矩阵相关控制器
│   │   ├── SysMatrixPortalController.java
│   │   ├── SysMatrixColumnPortalController.java
│   │   └── SysMatrixChangeLogPortalController.java
│   ├── form/                              # 表单相关控制器
│   │   ├── SysFormConfigPortalController.java
│   │   └── SysFormLinkagePortalController.java
│   └── dataset/                           # 数据集相关控制器
│
├── service/                    # 服务层
│   ├── MatrixDataService.java             # 数据操作服务
│   ├── martix/                            # 矩阵相关服务
│   │   ├── SysMatrixPortalService.java        # 矩阵管理核心服务
│   │   ├── SysMatrixColumnPortalService.java  # 字段配置服务
│   │   ├── SysMatrixChangeLogPortalService.java # 变更日志服务
│   │   └── SysMatrixDDLSerivce.java          # DDL操作独立服务
│   ├── form/                              # 表单相关服务
│   │   ├── SysFormConfigPortalService.java    # 表单配置服务
│   │   └── SysFormLinkagePortalService.java   # 联动配置服务
│   └── dataset/                           # 数据集相关服务
│
├── dao/                        # 数据访问层
│   ├── entity/                     # 实体类
│   │   ├── SysMatrix.java
│   │   ├── SysMatrixColumn.java
│   │   ├── SysFormConfig.java
│   │   ├── SysFormLinkage.java
│   │   ├── SysMatrixChangeLog.java
│   │   ├── SysPortalDataset.java
│   │   └── SysPortalDatasetColumn.java
│   │
│   ├── mapper/                     # MyBatis Mapper
│   │   ├── SysMatrixMapper.java & .xml
│   │   ├── SysMatrixColumnMapper.java & .xml
│   │   ├── SysFormConfigMapper.java & .xml
│   │   ├── SysFormLinkageMapper.java & .xml
│   │   └── SysMatrixChangeLogMapper.java & .xml
│   │
│   ├── repository/                 # Repository服务
│   │   ├── SysMatrixService.java
│   │   ├── SysMatrixColumnService.java
│   │   ├── SysFormConfigService.java
│   │   ├── SysFormLinkageService.java
│   │   └── SysMatrixChangeLogService.java
│   │
│   └── schema/                     # 表结构定义
│       ├── SysMatrixSchema.java
│       ├── SysMatrixColumnSchema.java
│       ├── SysFormConfigSchema.java
│       ├── SysFormLinkageSchema.java
│       └── SysMatrixChangeLogSchema.java
│
└── vo/                         # 视图对象
    ├── SysMatrixVO.java
    ├── SysMatrixColumnVO.java
    ├── SysFormConfigVO.java
    ├── SysFormLinkageVO.java
    └── SysMatrixChangeLogVO.java
```

### 2. 核心算法

#### 2.1 表结构同步算法

```java
syncTableStructure(matrixId) {
    1. 查询配置的字段列表 (configuredColumns)
    2. 查询表中已存在的字段 (existingColumns)
    
    // 处理字段
    for each column in configuredColumns {
        if (!existingColumns.contains(column)) {
            // 新字段：添加
            执行 ALTER TABLE ADD COLUMN ... AFTER
            记录变更日志 (type=2)
        } else {
            // 已存在字段：调整顺序
            执行 ALTER TABLE MODIFY COLUMN ... AFTER
            记录变更日志 (type=3)
        }
    }
    
    // 删除多余字段
    for each existingColumn in existingColumns {
        if (!configuredColumns.contains(existingColumn)) {
            // 需删除的字段
            执行 ALTER TABLE DROP COLUMN
            记录变更日志 (type=6)
        }
    }
    
    // 同步索引
    syncIndexes(matrix, columns)
    
    // 更新状态
    matrix.status = SYNCED
}
```

#### 2.2 索引同步算法

```java
syncIndexes(matrix, columns) {
    1. 查询表中已存在的索引 (existingIndexes)
    
    for each column in columns {
        // 处理普通索引
        if (column.isIndex == "1") {
            if (!existingIndexes.contains("idx_" + columnName)) {
                执行 ALTER TABLE ADD INDEX idx_xxx
                记录变更日志 (type=4)
            }
        } else {
            if (existingIndexes.contains("idx_" + columnName)) {
                执行 ALTER TABLE DROP INDEX idx_xxx
                记录变更日志 (type=5)
            }
        }
        
        // 处理唯一索引（同上）
        ...
    }
}
```

### 3. 关键技术点

#### 3.1 JdbcConnectService 统一数据访问

**设计原则**：
- 所有动态表的SQL执行统一通过 [`JdbcConnectService`](file://D:\workspace\mpbe\mpbe-api\core\forge\src\main\java\com\bidr\forge\config\jdbc\JdbcConnectService.java) 进行
- 优先使用 `NamedParameterJdbcTemplate` 实现命名参数化查询
- 统一记录 trace 级别日志，便于SQL调试和性能分析
- 支持多数据源动态切换

**核心API**：

```java
// 查询列表
List<Map<String, Object>> query(String sql, Map<String, Object> parameters)

// 查询单条
Map<String, Object> queryOne(String sql, Map<String, Object> parameters)

// 查询单值
<T> T queryForObject(String sql, Map<String, Object> parameters, Class<T> clazz)

// 更新操作（INSERT/UPDATE/DELETE）
int update(String sql, Map<String, Object> parameters)

// 数据源切换
void switchDataSource(String dataSourceName)
void resetToDefaultDataSource()
```

**命名参数化SQL示例**：

```java
// 传统拼接SQL（已废弃）
String sql = "SELECT * FROM user WHERE name = '" + name + "' AND age = " + age;
jdbcConnectService.executeQuery(sql);  // ❌ 不推荐

// 命名参数化SQL（推荐）
String sql = "SELECT * FROM user WHERE name = :name AND age = :age";
Map<String, Object> params = new HashMap<>();
params.put("name", name);
params.put("age", age);
jdbcConnectService.query(sql, params);  // ✅ 推荐
```

**无参数SQL调用规范**：

当执行由 SqlBuilder 生成的拼接SQL时，仍需传入空参数Map以保持API一致性和日志记录：

```java
// 错误用法
String sql = sqlBuilder.buildSelectSql(req);
List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql);  // ❌ 旧API

// 正确用法
String sql = sqlBuilder.buildSelectSql(req);
List<Map<String, Object>> result = jdbcConnectService.query(sql, new HashMap<>());  // ✅ 新API
```

**Trace日志示例**：

```log
2025-11-24 10:23:45.123 TRACE [JdbcConnectService] ==> 查询SQL: SELECT * FROM eval_user WHERE status = :status AND score >= :minScore
2025-11-24 10:23:45.124 TRACE [JdbcConnectService] ==> 参数: {status=1, minScore=80}
2025-11-24 10:23:45.156 TRACE [JdbcConnectService] <== 查询结果行数: 25
```

**多数据源使用示例**：

```java
// 切换到指定数据源
if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
    jdbcConnectService.switchDataSource(matrix.getDataSource());
}

try {
    // 执行数据库操作
    String sql = "SELECT * FROM " + matrix.getTableName();
    List<Map<String, Object>> result = jdbcConnectService.query(sql, new HashMap<>());
    return result;
} finally {
    // 重置回默认数据源（重要！）
    if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
        jdbcConnectService.resetToDefaultDataSource();
    }
}
```

#### 3.2 动态DDL生成

**CREATE TABLE**：
```java
StringBuilder ddl = new StringBuilder();
ddl.append("CREATE TABLE `").append(tableName).append("` (\n");

// 配置的字段
for (SysMatrixColumn column : columns) {
    ddl.append("  `").append(column.getColumnName()).append("` ");
    ddl.append(column.getColumnType());
    
    // 主键处理：只有数字类型才AUTO_INCREMENT
    if (isPrimaryKey && isNumericType) {
        ddl.append(" AUTO_INCREMENT");
    }
    // ... 其他属性
}

// 自动添加审计字段
ddl.append("  `create_by` varchar(50) DEFAULT NULL,\n");
ddl.append("  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),\n");
// ...

// 主键和索引
ddl.append("  PRIMARY KEY (`").append(primaryKey).append("`),\n");
ddl.append("  KEY `idx_update_at` (`update_at`)");
```

**ALTER TABLE ADD COLUMN**：
```java
StringBuilder ddl = new StringBuilder();
ddl.append("ALTER TABLE `").append(tableName).append("` ADD COLUMN ");
ddl.append(buildColumnDefinition(column));

// 位置控制
if (index == 0) {
    ddl.append(" FIRST");
} else {
    ddl.append(" AFTER `").append(prevColumn.getColumnName()).append("`");
}
```

#### 3.3 审计字段过滤

```java
// 审计字段不参与同步
Set<String> auditFields = Set.of(
    "create_by", "create_at", 
    "update_by", "update_at", 
    "valid"
);

List<String> existingColumns = queryColumns(tableName)
    .stream()
    .filter(col -> !auditFields.contains(col))
    .collect(Collectors.toList());
```

#### 3.4 生命周期钩子

**SysMatrixPortalService**：
```java
@Override
public void afterAdd(SysMatrix matrix) {
    // 自动创建ID主键字段
    SysMatrixColumn idColumn = new SysMatrixColumn();
    idColumn.setMatrixId(matrix.getId());
    idColumn.setColumnName("id");
    idColumn.setColumnType("BIGINT");
    idColumn.setIsPrimaryKey("1");
    idColumn.setSort(0);
    sysMatrixColumnService.save(idColumn);
}

@Override
public void beforeDelete(IdReqVO vo) {
    // 检查表中是否有数据
    SysMatrix matrix = getById(vo.getId());
    if (hasTableData(matrix)) {
        throw new RuntimeException("表中存在数据，无法删除");
    }
}
```

**SysMatrixColumnPortalService**：
```java
@Override
public void beforeDelete(IdReqVO vo) {
    // 检查字段是否有数据
    SysMatrixColumn column = getById(vo.getId());
    if (hasColumnData(column)) {
        throw new RuntimeException("字段中存在数据，无法删除");
    }
}

@Override
public void afterAdd(SysMatrixColumn column) {
    // 标记矩阵为待同步
    markPendingSync(column.getMatrixId());
}

@Override
public void afterUpdate(SysMatrixColumn column) {
    // 标记矩阵为待同步
    markPendingSync(column.getMatrixId());
}

@Override
public void afterDelete(IdReqVO vo) {
    // 标记矩阵为待同步
    SysMatrixColumn column = getById(vo.getId());
    markPendingSync(column.getMatrixId());
}
```

#### 3.5 表注释同步机制

**功能描述**：当通过update接口修改矩阵的 `tableComment` 字段时，系统会自动执行DDL同步表注释到数据库。

**实现原理**：

```java
// SysMatrixPortalService.afterUpdate()
@Override
public void afterUpdate(SysMatrix sysMatrix) {
    super.afterUpdate(sysMatrix);
    
    // 获取更新前的数据（从ThreadLocal）
    SysMatrix oldMatrix = beforeUpdateMatrix.get();
    
    // 检查表注释是否发生变更
    if (!Objects.equals(oldMatrix.getTableComment(), sysMatrix.getTableComment())) {
        // 只有已创建状态的表才执行DDL
        if (MatrixStatusDict.CREATED.getValue().equals(sysMatrix.getStatus()) ||
            MatrixStatusDict.SYNCED.getValue().equals(sysMatrix.getStatus()) ||
            MatrixStatusDict.PENDING_SYNC.getValue().equals(sysMatrix.getStatus())) {
            
            // 执行ALTER TABLE修改表注释
            executeModifyTableComment(sysMatrix);
        }
    }
}
```

**DDL语句生成**：

```java
private void executeModifyTableComment(SysMatrix matrix) {
    // 构建DDL语句
    String ddl = "ALTER TABLE `" + matrix.getTableName() + "` COMMENT '" + 
                 matrix.getTableComment() + "'";
    
    // 切换数据源
    if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
        jdbcConnectService.switchDataSource(matrix.getDataSource());
    }
    
    try {
        // 执行DDL
        jdbcConnectService.update(ddl, new HashMap<>());
        
        // 记录变更日志
        logTableCommentChange(matrix, ddl);
        
    } finally {
        // 重置数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }
}
```

**变更日志记录**：

```java
private void logTableCommentChange(SysMatrix matrix, String ddl) {
    // 获取当前最大版本号
    Integer maxVersion = getCurrentMaxVersion(matrix.getId());
    
    // 创建变更日志
    SysMatrixChangeLog log = new SysMatrixChangeLog();
    log.setMatrixId(matrix.getId());
    log.setVersion(maxVersion + 1);
    log.setChangeType(MatrixChangeTypeDict.MODIFY_TABLE_COMMENT.getValue());  // "7"
    log.setChangeDesc("修改表注释: " + matrix.getTableComment());
    log.setDdlStatement(ddl);
    log.setAffectedColumn(null);
    log.setExecuteStatus(CommonConst.YES);  // "1"
    log.setSort(maxVersion + 1);
    
    sysMatrixChangeLogService.save(log);
}
```

**使用示例**：

```java
// 场景：更新矩阵表注释
SysMatrix matrix = sysMatrixService.getById(matrixId);
matrix.setTableComment("用户评分表（已优化）");  // 修改注释
sysMatrixService.updateById(matrix);

// 系统自动执行：
// 1. 执行 DDL: ALTER TABLE `eval_user_score` COMMENT '用户评分表（已优化）'
// 2. 记录变更日志（version递增，changeType=7）
// 3. Trace日志输出SQL执行情况
```

**日志示例**：

```log
2025-11-24 10:30:12.456 TRACE [JdbcConnectService] ==> 更新SQL: ALTER TABLE `eval_user_score` COMMENT '用户评分表（已优化）'
2025-11-24 10:30:12.457 TRACE [JdbcConnectService] ==> 参数: {}
2025-11-24 10:30:12.489 TRACE [JdbcConnectService] <== 影响行数: 0
2025-11-24 10:30:12.490 INFO  [SysMatrixPortalService] 矩阵 [eval_user_score] 表注释已同步到数据库
```

**注意事项**：
- ✅ 只有已创建状态的表才会执行DDL同步
- ✅ 未创建状态的表只更新配置，建表时会使用新注释
- ✅ 每次注释修改都会生成新版本的变更日志
- ✅ 注释中的单引号会自动转义为双单引号
- ⚠️ 表注释长度不超过2048字符（MySQL限制）

---

## 使用场景

### 场景1：快速创建业务表

**需求**：创建一个用户评估信息表

**步骤**：
1. 创建矩阵 `eval_user_info`
2. 配置字段：姓名、部门、评估分数、评级
3. 点击"创建物理表"
4. 完成 ✅

**传统方式**：编写SQL、创建Entity、Mapper、Service、Controller等十几个文件  
**Forge方式**：3分钟配置完成

---

### 场景2：表单快速调整

**需求**：临时增加一个"联系电话"字段

**步骤**：
1. 在字段配置中添加 `phone` 字段
2. 在表单配置中设置显示标签和验证规则
3. 点击"同步表结构"
4. 完成 ✅

**传统方式**：修改DDL、Entity、VO、前端表单等多处  
**Forge方式**：1分钟配置完成，无需发版

---

### 场景3：复杂联动计算

**需求**：员工绩效自动计算总分和等级

**配置**：
```javascript
// 联动1：计算总分
conditionScript: baseScore != null && bonusScore != null
actionScript: totalScore = baseScore + bonusScore

// 联动2：判断等级
conditionScript: totalScore != null
actionScript: 
  if (totalScore >= 90) { level = 'A'; }
  else if (totalScore >= 80) { level = 'B'; }
  else if (totalScore >= 60) { level = 'C'; }
  else { level = 'D'; }
```

**传统方式**：前端JS + 后端Java双重实现  
**Forge方式**：5分钟配置完成，服务端统一计算

---

### 场景4:多环境数据隔离

**需求**:测试环境和生产环境使用不同数据库

**配置**:
```java
// 测试矩阵
matrix.dataSource = "test_db"

// 生产矩阵
matrix.dataSource = "prod_db"
```

**效果**:自动切换数据源,数据完全隔离

---

### 场景5:快速复制表结构

**需求**:将测试环境的表结构迁移到生产环境

**步骤**:
1. 在测试环境找到目标矩阵
2. 点击“导出DDL”获取CREATE TABLE语句
3. 切换到生产环境
4. 点击“导入DDL”粘贴语句
5. 自动创建矩阵配置和所有字段
6. 完成 ✅

**传统方式**:手动配置几十个字段,容易出错  
**Forge方式**:1分钟一键迁移,准确无误

---

### 场景6:跨环境配置同步（重点推荐）

**需求**:在预生产环境进行表结构调整和配置，然后一键同步到生产环境

**场景描述**:
- 预生产环境已创建矩阵 `eval_user_score`，版本为v1
- 经过5次迭代，共执行了20次变更，当前版本v6
- 需要将这些变更同步到生产环境的同名表

**步骤**:

#### 第一步:预生产环境导出变更日志
```
1. 找到矩阵 eval_user_score
2. 在变更日志管理页面,点击"导出变更日志"
3. 系统返回JSON数据：
   - 矩阵基本信息（表名、注释、数据源等）
   - 全部变更日志（v1-v6，共20条记录）
   - 当前字段配置
4. 复制JSON数据
```

**接口**: `GET /web/forge/matrix-changelog/export-changelog?id={matrixId}`  
**Controller**: `SysMatrixChangeLogPortalController.exportChangeLog()`

**导出JSON示例**:
```json
{
  "tableName": "eval_user_score",
  "tableComment": "用户评分表",
  "dataSource": null,
  "engine": "InnoDB",
  "charset": "utf8mb4",
  "changeLogs": [
    {
      "version": 1,
      "changeType": "1",
      "changeDesc": "创建表 eval_user_score",
      "ddlStatement": "CREATE TABLE `eval_user_score` (...)",
      "affectedColumn": null
    },
    {
      "version": 2,
      "changeType": "2",
      "changeDesc": "添加字段 total_score",
      "ddlStatement": "ALTER TABLE `eval_user_score` ADD COLUMN `total_score` DECIMAL(10,2) ...",
      "affectedColumn": "total_score"
    },
    // ... 更多变更记录
  ],
  "columns": [
    {
      "columnName": "id",
      "columnComment": "主键ID",
      "columnType": "BIGINT",
      "isPrimaryKey": "1",
      // ... 其他字段配置
    },
    // ... 更多字段
  ]
}
```

#### 第二步:生产环境准备
```
1. 登录生产环境
2. 确保已存在同名矩阵 eval_user_score
3. 检查矩阵状态：必须是“已创建”或“已同步”
4. 备份生产数据库（重要！）
```

#### 第三步:导入变更日志
```
1. 在变更日志管理页面,点击"导入变更日志"
2. 粘贴预生产环境导出的JSON数据
3. 点击确认
```

**接口**: `POST /web/forge/matrix-changelog/import-changelog`  
**Controller**: `SysMatrixChangeLogPortalController.importChangeLog()`

#### 系统自动执行的操作:
```
1. 解析JSON数据
2. 根据表名查找生产环境的矩阵
3. 获取生产环境当前最大版本号（如v3）
4. 筛选出需要执行的变更（v4-v6的记录）
5. 逐条执行DDL语句：
   - 跳过CREATE TABLE（表已存在）
   - 执行ADD COLUMN
   - 执行MODIFY COLUMN
   - 执行ADD INDEX
   - 执行DROP COLUMN
6. 同步字段配置：
   - 新增不存在的字段配置
   - 更新已存在的字段配置
   - 删除多余的字段配置
7. 记录所有变更日志到生产环境
8. 更新矩阵状态为“已同步”
```

#### 执行结果:
```
✅ 生产环境物理表结构与预生产环境完全一致
✅ 生产环境字段配置与预生产环境完全一致
✅ 生产环境变更日志包含所有变更历史
✅ 生产环境矩阵版本号更新为v6
✅ 原有生产数据完好无损
```

**优势对比**:

| 方面 | 传统方式 | Forge变更日志同步 |
|------|---------|------------------|
| 操作复杂度 | 高（手动执行每条DDL） | 低（一键导入） |
| 出错风险 | 高（SQL顺序错误、漏执行） | 低（自动按版本执行） |
| 配置同步 | 需手动同步 | 自动同步 |
| 变更追溯 | 难（需手动记录） | 易（完整历史） |
| 时间成本 | 30分钟+ | 2分钟 |
| 回滚难度 | 高 | 中（有历史记录） |

**注意事项**:
- ⚠️ 导入前必须备份生产数据库
- ⚠️ 目标环境必须已存在同名矩阵
- ⚠️ 建议在业务低峰期执行
- ✅ 系统会自动跳过已执行的版本
- ✅ 某些失败（如索引已存在）会自动忽略

---

## 最佳实践

### 1. 表设计规范

✅ **推荐**：
- 表名使用 `业务模块_功能` 格式，如 `eval_user_score`
- 字段名使用下划线命名，如 `user_name`
- 必须有主键，推荐使用自增BIGINT
- 常用查询字段添加索引
- 字符串字段合理设置长度

❌ **不推荐**：
- 使用MySQL保留字作为表名/字段名
- 过长的表名或字段名（超过30字符）
- 所有字段都建索引（浪费空间）
- VARCHAR不设置长度

### 2. 字段配置建议

| 业务类型 | 推荐配置 |
|---------|---------|
| 主键ID | BIGINT(20), AUTO_INCREMENT |
| 用户姓名 | VARCHAR(50), 索引 |
| 手机号 | VARCHAR(11), 唯一索引 |
| 身份证号 | VARCHAR(18), 唯一索引 |
| 邮箱 | VARCHAR(100), 唯一索引 |
| 金额 | DECIMAL(19,2) |
| 百分比 | DECIMAL(5,2) |
| 状态标识 | VARCHAR(10) 或 CHAR(1) |
| 长文本 | TEXT |
| 日期时间 | DATETIME |

### 3. 索引优化建议

✅ **应该添加索引的字段**：
- 主键字段（自动创建）
- 外键字段
- 经常用于WHERE条件的字段
- 经常用于ORDER BY的字段
- 经常用于JOIN的字段

❌ **不应该添加索引的字段**：
- TEXT/BLOB等大字段
- 更新频繁的字段
- 区分度很低的字段（如性别）
- 表数据量很小（<1000行）

### 4. 表结构变更流程

**安全的变更流程**：
```
1. 在测试环境配置并测试
   ↓
2. 验证同步功能正常
   ↓
3. 备份生产数据库
   ↓
4. 选择业务低峰期
   ↓
5. 执行表结构同步
   ↓
6. 验证数据完整性
   ↓
7. 监控系统运行
```

**高危操作注意**：
- ⚠️ 调整字段顺序会锁表，大表慎用
- ⚠️ 添加唯一索引前检查数据是否有重复
- ⚠️ 删除字段前确认数据已迁移或无用

### 5. 性能优化建议

**大表操作**：
- 在低峰期执行DDL操作
- 考虑使用pt-online-schema-change等工具
- 提前评估锁表时间

**索引策略**：
- 优先使用覆盖索引
- 避免过多的复合索引
- 定期分析索引使用情况

**数据查询**：
- 善用条件查询减少数据量
- 大数据量使用分页
- 避免SELECT *

---

## 常见问题

### Q1：创建表时提示"表已存在"？
**A**：检查数据库中是否已有同名表。如果是软删除的矩阵，可以重新启用；如果是手动创建的表，需要先删除或改名。

### Q2：同步表结构时提示"表未创建"？
**A**：矩阵状态必须为"已创建"、"已同步"或"待同步"。如果是新矩阵，先点击"创建物理表"。

### Q3：字段顺序调整后，前端显示顺序不对？
**A**：
1. 检查是否执行了"同步表结构"
2. 前端需要按照 `sort` 字段排序显示
3. 清除浏览器缓存重新加载

### Q4：联动脚本不生效？
**A**：
1. 检查 `isEnabled` 是否为"1"
2. 检查 `conditionScript` 是否满足
3. 查看后台日志是否有脚本错误
4. 确认 `targetFields` 配置正确

### Q5：删除字段时提示"字段中存在数据"？
**A**：这是安全保护机制。如需删除：
1. 确认数据可以丢弃
2. 手动清空该字段数据：`UPDATE table SET field = NULL`
3. 重新删除字段配置

### Q6：能否修改已创建表的字段类型？
**A**：当前版本不支持。需要：
1. 创建新字段
2. 迁移数据
3. 删除旧字段

### Q7：如何删除整个矩阵？
**A**：
1. 必须先清空表数据（超级管理员使用"清空数据"功能）
2. 删除矩阵配置
3. 物理表仍保留，需手动DROP TABLE

### Q8：多数据源如何配置？
**A**：
1. 在数据源配置中添加新数据源
2. 矩阵配置中 `dataSource` 字段填写数据源名称
3. 所有操作自动切换到指定数据源

### Q9：如何实现字段级权限控制？
**A**：在表单配置中：
- `readonly="1"` - 只读
- `hidden="1"` - 隐藏
- 结合用户角色动态控制

### Q10:表结构同步会影响现有数据吗?
**A**:不会!同步操作:
- ✅ 只添加新字段
- ✅ 只调整字段位置
- ✅ 只管理索引
- ❌ 不修改字段类型
- ❌ 不删除数据

### Q11:DDL导出导入有什么限制?
**A**:
- ✅ 支持标准MySQL CREATE TABLE语法
- ✅ 自动解析字段类型、长度、注释
- ✅ 自动识别主键、索引、唯一索引
- ⚠️ 不支持外键约束
- ⚠️ 不支持分区表
- ⚠️ 导入时会创建新矩阵,不会覆盖现有配置

### Q12:如何批量迁移多个表?
**A**:
1. 逐个导出DDL语句
2. 保存到文本文件
3. 在目标环境逐个导入
4. 验证配置正确性
5. 创建物理表

### Q13:跨环境同步失败怎么办？
**A**:
1. 查看错误信息，确定失败原因
2. 检查目标环境是否存在同名矩阵
3. 确认目标矩阵状态是否正确（已创建/已同步）
4. 检查是否有权限执行DDL语句
5. 查看变更日志表，确认哪些变更已执行
6. 必要时从备份恢复

### Q14:如何回滚跨环境同步？
**A**:
当前版本不支持自动回滚。如需回滚：
1. 从数据库备份恢复
2. 或手动执行反向DDL（如DROP COLUMN、DROP INDEX）
3. 删除相应的变更日志记录
4. 更新矩阵版本号

### Q15:跨环境同步和DDL导入有什么区别？
**A**:

| 功能 | DDL导入 | 跨环境同步 |
|------|---------|-------------|
| 使用场景 | 创建新矩阵 | 同步现有矩阵 |
| 目标表 | 不存在 | 已存在 |
| 导入内容 | DDL语句 | 变更日志+配置 |
| 版本管理 | 无 | 有（增量同步） |
| 配置同步 | 否 | 是 |
| 变更历史 | 不保留 | 完整保留 |

**总结**:
- DDL导入：用于快速创建新表
- 跨环境同步：用于现有表的配置和结构同步

### Q16:跨环境同步会删除数据吗？
**A**:
不会！跨环境同步只处理表结构：
- ✅ 添加字段（原有数据保留）
- ✅ 调整字段位置（原有数据保留）
- ✅ 管理索引（不影响数据）
- ✅ 删除字段（会删除该字段数据）
- ❌ 不会TRUNCATE表
- ❌ 不会DELETE数据

**重要提醒**:删除字段会导致该字段数据丢失，请提前备份！

---

## 版本历史

### v1.1.0 (2025-11-23)
**新增功能**:
- ✨ 跨环境配置同步（重点功能）
  - 导出矩阵变更日志
  - 导入变更日志并自动同步
  - 版本增量同步，只执行未同步的变更
  - 自动同步字段配置
  - 完整的变更历史追溯

**使用场景**:
- 预生产环境配置同步到生产环境
- 测试环境配置同步到预生产环境
- 多环境一致性保证
- 迭代式表结构调整

**技术实现**:
- 基于JSON导出/导入
- 智能版本对比
- 自动DDL执行
- 配置自动同步
- 错误容错处理

---

### v1.0.0 (2025-11-20)
**核心功能**:
- ✅ 矩阵管理(创建、同步、删除)
- ✅ 字段配置(18种字段类型)
- ✅ 表单配置(可视化布局)
- ✅ 联动配置(JavaScript脚本)
- ✅ 数据操作(CRUD接口)
- ✅ 变更日志(完整追踪)
- ✅ DDL导入导出(快速迁移)

**特性**:
- ✅ 自动创建审计字段
- ✅ 自动创建ID主键
- ✅ 支持序列主键
- ✅ 索引自动管理
- ✅ 字段顺序调整
- ✅ 删除前数据检查
- ✅ 多数据源支持
- ✅ 状态自动流转
- ✅ 软删除支持
- ✅ 超级管理员清空数据
- ✅ DDL语句智能解析
- ✅ 跨环境表结构迁移

---

## 扩展规划

### 短期计划（v1.1）
- [ ] 字段类型修改支持
- [ ] 批量导入数据
- [ ] 数据导出Excel
- [ ] 表单模板库
- [ ] 常用联动脚本库

### 中期计划（v1.2）
- [ ] 可视化表单设计器
- [ ] 字段级权限控制
- [ ] 数据版本管理
- [ ] 审批流程配置
- [ ] 移动端表单适配

### 长期计划（v2.0）
- [ ] 多表关联查询
- [ ] 复杂报表配置
- [ ] 工作流引擎
- [ ] BI数据分析
- [ ] AI智能推荐

---

## 相关文档

- [动态矩阵配置API文档](./动态矩阵配置API文档.md) - 前端对接完整指南
- [SQL代码生成指南](../doc/SQL代码生成指南.md) - 数据库设计参考

---

## 技术支持

**开发团队**：Forge Team  
**模块位置**：`core/forge`  
**联系方式**：请通过项目Issue反馈问题

---

*最后更新:2025-11-22*
