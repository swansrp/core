# Portal框架JDBC驱动扩展改造文档

## 一、改造概述

### 1.1 改造目标
在不破坏现有 `BasePortalService`/`BaseAdminTreeController` 通用查询框架的前提下，扩展一条"按 Portal 配置直接走 JDBC"的数据访问通路，支持两种表格定义方式：

- **Matrix 模式**：基于 `SysMatrix` + `SysMatrixColumn` 的实体表配置，支持完整 CRUD + 树结构
- **Dataset 模式**：基于 `SysPortalDataset` + `SysPortalDatasetColumn` 的多表联接视图，支持只读查询

### 1.2 核心设计原则
- **接口层**：在 kernel 模块定义驱动核心接口（`PortalDataDriver`）
- **实现层**：在 forge 模块实现具体驱动（`MatrixDriver`/`DatasetDriver`）
- **集成层**：在 admin 模块的 `BasePortalService` 负责驱动分发
- **对外 API**：保持现有 Portal API 不变，内部根据 `dataMode` 切换数据访问路径
- **安全性**：所有 SQL 使用命名参数化，列/表名仅来自元数据白名单

## 二、架构设计

### 2.1 模块职责划分

```
kernel 模块（接口定义）
├── PortalDataDriver        # 数据驱动接口
├── PortalDataMode          # 数据模式枚举（ENTITY/MATRIX/DATASET）
└── DriverCapability        # 驱动能力声明（readable/writable/treeSupport）

forge 模块（驱动实现）
├── SqlBuilder              # SQL 构建器接口
├── DatasetSqlBuilder       # Dataset 模式 SQL 构建器
├── MatrixSqlBuilder        # Matrix 模式 SQL 构建器
├── DatasetDriver           # Dataset 驱动实现（只读）
└── MatrixDriver            # Matrix 驱动实现（完整 CRUD + 树）

admin 模块（驱动集成）
├── SysPortal               # Portal 实体（新增 dataMode/tableId 字段）
└── BasePortalService       # Portal 服务（集成驱动分发逻辑）
```

### 2.2 数据流向

```
前端请求 
  ↓
Portal Controller（BaseAdminController/BaseAdminTreeController）
  ↓
BasePortalService.advancedQuery()
  ↓
  ├─ dataMode=ENTITY → 走现有 MPJ Wrapper 路径
  ├─ dataMode=MATRIX → MatrixDriver（JDBC 单表）
  └─ dataMode=DATASET → DatasetDriver（JDBC 多表联接）
       ↓
       SqlBuilder 构建 SQL → NamedParameterJdbcTemplate 执行
       ↓
       结果集按 aliasMap 映射到 VO
       ↓
       返回统一格式响应
```

## 三、核心接口与实现

### 3.1 驱动核心接口（kernel 模块）

#### PortalDataMode 枚举
```java
public enum PortalDataMode {
    ENTITY,   // 实体模式（MPJ Wrapper）
    MATRIX,   // 矩阵模式（JDBC 单表）
    DATASET   // 数据集模式（JDBC 多表视图）
}
```

#### DriverCapability 能力声明
```java
@Data
public class DriverCapability {
    private Boolean readable;      // 是否支持读取
    private Boolean writable;      // 是否支持写入
    private Boolean treeSupport;   // 是否支持树结构
}
```

#### PortalDataDriver 接口
```java
public interface PortalDataDriver<VO> {
    DriverCapability getCapability();
    PortalDataMode getDataMode();
    
    // 别名映射
    Map<String, String> buildAliasMap(String portalName, String roleId);
    
    // 查询接口
    Page<VO> queryPage(AdvancedQueryReq req, String portalName, String roleId);
    List<VO> queryList(AdvancedQueryReq req, String portalName, String roleId);
    VO queryOne(AdvancedQueryReq req, String portalName, String roleId);
    Long count(AdvancedQueryReq req, String portalName, String roleId);
    List<VO> getAllData(String portalName, String roleId);
    
    // DML 接口
    int insert(VO data, String portalName, String roleId);
    int batchInsert(List<VO> dataList, String portalName, String roleId);
    int update(VO data, String portalName, String roleId);
    int batchUpdate(List<VO> dataList, String portalName, String roleId);
    int delete(Object id, String portalName, String roleId);
    int batchDelete(List<Object> ids, String portalName, String roleId);
}
```

### 3.2 SQL 构建器（forge 模块）

#### SqlBuilder 接口
```java
public interface SqlBuilder {
    String buildSelect(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters);
    String buildCount(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters);
    String buildInsert(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters);
    String buildUpdate(Map<String, Object> data, Map<String, String> aliasMap, Map<String, Object> parameters);
    String buildDelete(Object id, Map<String, Object> parameters);
}
```

#### DatasetSqlBuilder 实现要点
- **主表识别**：`JOIN_TYPE` 为 NULL 的记录作为主表
- **SELECT 列**：使用 `SysPortalDatasetColumn.columnSql AS columnAlias`
- **支持复杂表达式**：如 `CASE WHEN ... ELSE ... END`
- **聚合查询**：`isAggregate=YES` 的字段走 HAVING 子句，非聚合字段自动 GROUP BY
- **FROM+JOIN**：按 `dataset_order` 排序构建 JOIN 链

#### MatrixSqlBuilder 实现要点
- **FROM 子句**：`SysMatrix.tableName`
- **SELECT 列**：按 `SysMatrixColumn.column_name` 映射
- **主键处理**：支持单主键和联合主键
- **树字段识别**：`isPidField`/`isOrderField`/`isDisplayNameField`
- **DML 校验**：必填/长度/唯一性校验

### 3.3 驱动实现（forge 模块）

#### DatasetDriver 能力
```java
DriverCapability.readOnly()
// readable=true, writable=false, treeSupport=false
```

- **只读查询**：支持分页/列表/单条查询
- **数据源切换**：根据 `SysPortalDataset.dataSource` 调用 `JdbcConnectService.switchDataSource()`
- **aliasMap 构建**：每个 `SysPortalDatasetColumn.columnAlias` 映射为 VO 字段
- **DML 操作**：抛出 `UnsupportedOperationException`

#### MatrixDriver 能力
```java
DriverCapability.fullSupport()
// readable=true, writable=true, treeSupport=true
```

- **完整 CRUD**：INSERT/UPDATE/DELETE 带数据校验
- **树结构支持**：通过 `getAllData()` 返回包含 id/pid/name/order 的数据
- **主键策略**：
  - 单主键且为数字类型：跳过（依赖自增）
  - 序列主键：需外部调用 `saSequenceService.getSeq()` 填充
  - 联合主键：DELETE 需传入 Map 类型 ID
- **数据校验**：
  - INSERT：必填校验（排除主键）、长度校验
  - UPDATE：主键存在性校验、长度校验

## 四、字段映射规则

### 4.1 Dataset 模式
- **VO 字段集合**：完全由 `SysPortalDatasetColumn` 决定
- **aliasMap 映射**：
  ```
  VO字段名（columnAlias） → SQL表达式（columnSql）
  ```
- **示例**：
  ```java
  columnSql = "CASE WHEN t1.status='1' THEN '启用' ELSE '禁用' END"
  columnAlias = "statusLabel"
  → aliasMap.put("statusLabel", "CASE WHEN ...")
  ```

### 4.2 Matrix 模式
- **VO 字段集合**：由 `PortalColumn` + `SysMatrixColumn` 决定
- **aliasMap 映射**：
  ```
  VO字段名（PortalColumn.property） → 数据库列名（SysMatrixColumn.column_name）
  ```
- **示例**：
  ```java
  SysMatrixColumn.columnName = "user_name"
  PortalColumn.property = "userName"
  → aliasMap.put("userName", "user_name")
  ```

## 五、Portal 配置扩展

### 5.1 SysPortal 新增字段

| 字段名 | 类型 | 长度 | 说明 |
|--------|------|------|------|
| `data_mode` | VARCHAR | 20 | 数据模式：ENTITY/MATRIX/DATASET |
| `table_id` | VARCHAR | 100 | 关联表格ID：<br>- MATRIX 模式：SysMatrix.id 或 tableName<br>- DATASET 模式：SysPortalDataset.tableId |

### 5.2 DDL 变更脚本

```sql
ALTER TABLE sys_portal ADD COLUMN `data_mode` VARCHAR(20) DEFAULT 'ENTITY' COMMENT '数据模式:ENTITY/MATRIX/DATASET';
ALTER TABLE sys_portal ADD COLUMN `table_id` VARCHAR(100) COMMENT '关联表格ID';
```

## 六、BasePortalService 集成逻辑（待实现）

### 6.1 驱动分发流程

```java
public Page<VO> advancedQuery(AdvancedQueryReq req) {
    SysPortal portal = getPortal(portalName);
    
    if (PortalDataMode.MATRIX.name().equals(portal.getDataMode())) {
        // 走 MatrixDriver
        return matrixDriver.queryPage(req, portal.getTableId(), roleId);
    } else if (PortalDataMode.DATASET.name().equals(portal.getDataMode())) {
        // 走 DatasetDriver
        return datasetDriver.queryPage(req, portal.getTableId(), roleId);
    } else {
        // 默认走现有 MPJ 路径
        return super.advancedQuery(req);
    }
}
```

### 6.2 aliasMap 替换

- **ENTITY 模式**：保持现有基于 VO 注解（`@PortalEntityField`/`@PortalSelect`）的解析
- **MATRIX/DATASET 模式**：调用 `driver.buildAliasMap()` 覆盖 `aliasMap`

### 6.3 导出与 Excel 兼容

- **export()**：调用 `driver.queryList()` 获取数据，后续导出逻辑不变
- **templateExport()**：根据 `PortalColumn` 生成模板（与 dataMode 无关）
- **readExcelForInsert()**：
  - MATRIX 模式：解析后调用 `driver.batchInsert()`
  - DATASET 模式：抛出异常（不支持写入）

## 七、安全与性能

### 7.1 SQL 注入防护
- **命名参数化**：所有参数通过 `NamedParameterJdbcTemplate` 绑定
- **白名单机制**：列名/表名仅来自 `SysMatrixColumn`/`SysPortalDatasetColumn` 元数据
- **操作符白名单**：WHERE 条件的 operator 限定在 `=/>/</>=/<=/<>/LIKE/IN/BETWEEN/IS NULL` 范围内

### 7.2 事务管理
- **单条 DML**：在 driver 内部自动提交
- **批量 DML**：当前实现为循环单条，后续可优化为批量 SQL + 事务包裹

### 7.3 数据源切换
- **切换时机**：在 driver 执行前调用 `JdbcConnectService.switchDataSource(dataSource)`
- **重置时机**：在 `finally` 块调用 `resetToDefaultDataSource()`
- **线程安全**：基于 `DynamicDataSourceContextHolder` 的 ThreadLocal 机制

## 八、树结构支持（Matrix 专属）

### 8.1 字段标识
- `isPidField=YES`：父节点字段
- `isOrderField=YES`：排序字段
- `isDisplayNameField=YES`：名称字段

### 8.2 BaseAdminTreeController 集成
- **getAllData()**：MatrixDriver 返回所有数据（包含 id/pid/name/order）
- **树查询接口**：无需改动，自动调用 `portalService.getAllData()`
- **pid 更新**：通过 `driver.update()` 更新父节点字段并重排 order

## 九、错误处理

### 9.1 配置校验
- Portal 的 `dataMode` 为 MATRIX/DATASET 时，`tableId` 必填
- MATRIX 模式下，`tableId` 必须能匹配到有效的 `SysMatrix` 配置
- DATASET 模式下，`tableId` 必须能查询到 `SysPortalDataset` 和 `SysPortalDatasetColumn`

### 9.2 运行时异常
- **主键缺失**：INSERT/UPDATE/DELETE 前校验主键配置
- **数据校验失败**：抛出 `NoticeException`，带具体字段名和错误描述
- **不支持的操作**：Dataset 的 DML 操作抛出 `UnsupportedOperationException`

## 十、改造影响范围

### 10.1 新增文件
```
kernel/service/driver/
  - PortalDataMode.java
  - DriverCapability.java
  - PortalDataDriver.java

forge/service/driver/builder/
  - SqlBuilder.java
  - DatasetSqlBuilder.java
  - MatrixSqlBuilder.java

forge/service/driver/
  - DatasetDriver.java
  - MatrixDriver.java
```

### 10.2 修改文件
```
admin/dao/entity/SysPortal.java
  - 新增 dataMode 字段
  - 新增 tableId 字段

admin/service/common/BasePortalService.java（待实现）
  - 新增驱动分发逻辑
  - aliasMap 构建切换
  - 查询/DML 接口集成 driver
```

### 10.3 数据库变更
```sql
ALTER TABLE sys_portal 
  ADD COLUMN `data_mode` VARCHAR(20) DEFAULT 'ENTITY' COMMENT '数据模式:ENTITY/MATRIX/DATASET',
  ADD COLUMN `table_id` VARCHAR(100) COMMENT '关联表格ID';
```

## 十一、验证计划

### 11.1 Matrix 模式验证
- [ ] 配置一个 Matrix 表格，绑定到 Portal
- [ ] 验证分页查询
- [ ] 验证高级查询（WHERE/HAVING/聚合）
- [ ] 验证 INSERT/UPDATE/DELETE
- [ ] 验证树结构查询
- [ ] 验证导出功能

### 11.2 Dataset 模式验证
- [ ] 配置一个 Dataset（2 表 LEFT JOIN）
- [ ] 验证分页查询
- [ ] 验证复杂字段表达式（CASE WHEN）
- [ ] 验证聚合查询（COUNT/SUM + GROUP BY）
- [ ] 验证导出功能
- [ ] 验证 DML 操作正确抛出异常

### 11.3 兼容性验证
- [ ] 验证现有 ENTITY 模式 Portal 不受影响
- [ ] 验证 BaseAdminTreeController 在 Matrix 模式下正常工作
- [ ] 验证多数据源切换正确

## 十二、后续优化方向

1. **批量 DML 优化**：当前批量操作为循环单条，可优化为一次性生成批量 SQL
2. **查询缓存**：对频繁查询的 Matrix/Dataset 元数据增加本地缓存
3. **SQL 性能监控**：集成慢查询日志与执行计划分析
4. **Dataset 部分写入**：支持配置"可写表映射"，允许更新主表字段
5. **视图物化**：对复杂 Dataset 提供物化视图创建能力

## 十三、注意事项

1. **数据源一致性**：同一个 Portal 下所有 Dataset 必须使用相同 dataSource
2. **主键策略**：Matrix 的序列主键需外部填充，driver 不自动调用序列服务
3. **字段顺序**：Dataset 的 SELECT 列顺序按 `displayOrder` 升序，Matrix 按 `sort` 升序
4. **聚合字段识别**：Dataset 的 HAVING 条件仅处理 `isAggregate=YES` 的字段
5. **树字段唯一性**：Matrix 表只能有一个 `isPidField`/`isOrderField`/`isDisplayNameField`

---

**改造完成日期**：2025-11-24  
**改造负责人**：Sharp  
**文档版本**：v1.0
