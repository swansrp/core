# Portal配置生成功能说明

## 功能概述

为 **Matrix（动态矩阵）** 和 **Dataset（数据集）** 自动生成对应的 **Portal配置**，实现快速后台管理界面的自动化创建。

**时间**：2025-11-25  
**作者**：sharp

---

## 接口设计

### 1. 为Matrix生成Portal配置

**接口路径**：`POST /web/portal/generate/matrix`

**请求参数**：
```json
{
  "portalName": "sysUserPortal",          // Portal名称（必填，唯一标识）
  "matrixId": 1,                           // Matrix ID（必填）
  "displayName": "用户管理",               // 显示名称（可选，默认使用Matrix的tableComment）
  "url": "/web/user",                      // URL路径（可选，默认 /web/{portalName}）
  "bean": "userPortalController"           // Bean名称（可选，默认 {portalName}PortalController）
}
```

**功能说明**：
1. 根据 `matrixId` 查询Matrix配置及其字段配置
2. 创建 `SysPortal` 记录，配置项包括：
   - `dataMode`: 设置为 `MATRIX`
   - `referenceId`: 关联Matrix的ID
   - `idColumn/orderColumn/pidColumn/nameColumn`: 根据Matrix字段的特殊标记自动设置
3. 根据Matrix的字段配置（`SysMatrixColumn`）批量创建 `SysPortalColumn` 记录
4. 字段映射规则：
   - `columnName` → `property`（下划线转驼峰）、`dbField`
   - `columnComment` → `displayName`
   - `fieldType` → `fieldType`（直接映射）
   - 主键字段不允许新增和编辑

**返回结果**：
```json
{
  "code": 0,
  "msg": "Matrix Portal配置生成成功",
  "data": 123  // 生成的Portal ID
}
```

---

### 2. 为Dataset生成Portal配置

**接口路径**：`POST /web/portal/generate/dataset`

**请求参数**：
```json
{
  "portalName": "orderDatasetPortal",      // Portal名称（必填，唯一标识）
  "datasetId": 10,                         // Dataset ID（必填）
  "displayName": "订单数据集",             // 显示名称（可选，默认使用Dataset的datasetName）
  "url": "/web/dataset/order",             // URL路径（可选，默认 /web/{portalName}）
  "bean": "orderDatasetPortalController"   // Bean名称（可选，默认 {portalName}PortalController）
}
```

**功能说明**：
1. 根据 `datasetId` 查询Dataset配置及其字段配置
2. 创建 `SysPortal` 记录，配置项包括：
   - `dataMode`: 设置为 `DATASET`
   - `referenceId`: 关联Dataset的ID
   - `readOnly`: 设置为 `1`（Dataset默认只读）
3. 根据Dataset的字段配置（`SysDatasetColumn`）批量创建 `SysPortalColumn` 记录
4. 字段映射规则：
   - `columnAlias` → `property`（下划线转驼峰）、`dbField`、`displayName`
   - `fieldType`: 固定为 `01`（文本类型）
   - 只处理 `isVisible=1` 的字段
   - 所有字段只读，不允许新增和编辑
5. 更新Dataset的 `referenceId` 字段为生成的Portal ID

**返回结果**：
```json
{
  "code": 0,
  "msg": "Dataset Portal配置生成成功",
  "data": 124  // 生成的Portal ID
}
```

---

## 核心逻辑说明

### Portal配置默认值

| 配置项 | Matrix模式 | Dataset模式 | 说明 |
|--------|-----------|------------|------|
| `size` | small | small | 表格大小 |
| `readOnly` | 0 | 1 | Dataset默认只读 |
| `summary` | 0 | 0 | 不启用总结栏 |
| `advanced` | 1 | 1 | 启用高级查询 |
| `treeDrag` | 0 | 0 | 不支持树形拖拽 |
| `tableDrag` | 0 | 0 | 不支持表格拖拽 |
| `addWidth` | 800 | 800 | 新增弹窗宽度 |
| `editWidth` | 800 | 800 | 编辑弹窗宽度 |
| `detailWidth` | 800 | 800 | 详情弹窗宽度 |
| `descriptionCount` | 2 | 2 | 弹窗每行显示2个字段 |
| `exportAble` | 1 | 1 | 支持导出 |
| `importAble` | 0 | 0 | 不支持导入 |

### PortalColumn配置默认值

| 配置项 | Matrix模式 | Dataset模式 | 说明 |
|--------|-----------|------------|------|
| `displayOrder` | 按字段顺序递增 | 按字段顺序递增 | 显示顺序 |
| `align` | center | center | 对齐方式 |
| `width` | 150 | 150 | 列宽度 |
| `fixed` | 0 | 0 | 不固定列 |
| `tooltip` | 1 | 1 | 显示tooltip |
| `enable` | 1 | 1 | 字段启用 |
| `show` | 1 | 1 | 列表显示 |
| `filterAble` | 1 | 1 | 可筛选 |
| `sortAble` | 1 | 1 | 可排序 |
| `summaryAble` | 0 | 0 | 不汇总 |
| `editAble` | 0 | 0 | 不支持表格内编辑 |
| `detailShow` | 1 | 1 | 详情显示 |
| `addShow` | 主键:0 其他:1 | 0 | Dataset不支持新增 |
| `editShow` | 主键:0 其他:1 | 0 | Dataset不支持编辑 |
| `required` | 根据nullable判断 | 0 | 必填验证 |

### Matrix特殊字段识别

Portal配置会自动识别Matrix字段的特殊标记：

```java
// 主键字段 → idColumn
if (column.getIsPrimaryKey() == '1') {
    portal.setIdColumn(column.getColumnName());
}

// 排序字段 → orderColumn
if (column.getIsOrderField() == '1') {
    portal.setOrderColumn(column.getColumnName());
}

// 父节点字段 → pidColumn（树形结构）
if (column.getIsPidField() == '1') {
    portal.setPidColumn(column.getColumnName());
}

// 名称字段 → nameColumn
if (column.getIsDisplayNameField() == '1') {
    portal.setNameColumn(column.getColumnName());
}
```

---

## 使用场景

### 场景1：为Matrix动态表生成管理界面

1. 通过Matrix功能创建动态表（如 `sys_custom_table`）
2. 配置表字段（包括主键、排序字段等）
3. 调用生成接口：
   ```bash
   POST /web/portal/generate/matrix
   {
     "portalName": "customTablePortal",
     "matrixId": 5,
     "displayName": "自定义表管理"
   }
   ```
4. 生成后即可使用Portal框架进行CRUD操作

### 场景2：为Dataset数据集生成查询界面

1. 通过Dataset功能创建数据集（如订单统计视图）
2. 配置数据集字段（包括字段别名、显示顺序等）
3. 调用生成接口：
   ```bash
   POST /web/portal/generate/dataset
   {
     "portalName": "orderStatsPortal",
     "datasetId": 10,
     "displayName": "订单统计"
   }
   ```
4. 生成后即可使用Portal框架进行只读查询和导出

---

## 注意事项

1. **Portal名称唯一性**：同一roleId下，`portalName` 必须唯一，重复调用会报错
2. **字段必须配置**：Matrix或Dataset必须至少配置一个字段，否则无法生成
3. **Matrix主键限制**：主键字段自动设置为不可新增、不可编辑
4. **Dataset只读限制**：Dataset生成的Portal默认只读，所有字段不可新增、不可编辑
5. **字段类型映射**：
   - Matrix：直接使用 `fieldType`（需提前配置正确）
   - Dataset：统一使用 `01`（文本类型），需后续手动调整
6. **默认roleId**：生成的Portal配置使用默认角色ID（`PortalConfigService.DEFAULT_CONFIG_ROLE_ID`）

---

## 后续扩展建议

1. **字段类型智能推断**：
   - Dataset可根据字段SQL表达式推断字段类型
   - 例如：`COUNT(*)`、`SUM()` 推断为数字类型

2. **支持更多配置项**：
   - 自定义默认查询条件
   - 自定义默认排序
   - 自定义导入导出模板

3. **批量生成**：
   - 支持一次为多个Matrix/Dataset生成Portal配置
   - 支持根据模板批量生成

4. **Portal配置更新**：
   - 当Matrix/Dataset字段变更时，自动同步Portal字段配置
   - 提供差异对比功能

---

## 相关文件

### 新增文件
- `GeneratePortalReq.java` - 生成请求VO
- `PortalGenerateService.java` - Portal生成服务
- `PortalGenerateController.java` - Portal生成接口

### 依赖文件
- `SysPortalService.java` - Portal配置服务
- `SysPortalColumnService.java` - Portal字段配置服务
- `SysMatrixService.java` - Matrix配置服务
- `SysMatrixColumnService.java` - Matrix字段配置服务
- `SysDatasetService.java` - Dataset配置服务
- `SysDatasetColumnService.java` - Dataset字段配置服务

---

## API示例

### 示例1：为用户表Matrix生成Portal

**请求**：
```bash
curl -X POST http://localhost:8080/web/portal/generate/matrix \
  -H "Content-Type: application/json" \
  -d '{
    "portalName": "sysUserPortal",
    "matrixId": 1,
    "displayName": "用户管理",
    "url": "/web/user"
  }'
```

**响应**：
```json
{
  "code": 0,
  "msg": "Matrix Portal配置生成成功",
  "data": 100
}
```

### 示例2：为订单数据集生成Portal

**请求**：
```bash
curl -X POST http://localhost:8080/web/portal/generate/dataset \
  -H "Content-Type: application/json" \
  -d '{
    "portalName": "orderDatasetPortal",
    "datasetId": 10,
    "displayName": "订单统计查询"
  }'
```

**响应**：
```json
{
  "code": 0,
  "msg": "Dataset Portal配置生成成功",
  "data": 101
}
```

---

## 总结

Portal配置生成功能实现了从底层数据模型（Matrix/Dataset）到前端管理界面（Portal）的自动化映射，大幅提升了开发效率。通过合理配置Matrix字段或Dataset字段，可以快速生成功能完整的管理界面，减少重复性的配置工作。
