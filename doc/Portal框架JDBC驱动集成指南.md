# Portal框架JDBC驱动集成指南

## 一、当前完成状态

### 1.1 已完成模块

✅ **kernel 模块**（驱动接口定义）
- `PortalDataDriver` 接口
- `PortalDataMode` 枚举（ENTITY/MATRIX/DATASET）
- `DriverCapability` 能力声明

✅ **forge 模块**（驱动实现）
- `SqlBuilder` 接口
- `DatasetSqlBuilder` 实现（支持多表 JOIN、复杂表达式、聚合查询）
- `MatrixSqlBuilder` 实现（支持单表 CRUD）
- `DatasetDriver` 驱动（只读查询）
- `MatrixDriver` 驱动（完整 CRUD + 树结构）

✅ **admin 模块**（Portal 实体扩展）
- `SysPortal` 新增 `dataMode` 和 `tableId` 字段

### 1.2 待完成内容

⚠️ **数据库变更**（需要执行 DDL）
- 在 `sys_portal` 表添加 `data_mode` 和 `table_id` 列

⚠️ **BasePortalService 集成**（需要手动编码）
- 驱动分发逻辑
- aliasMap 构建切换
- 查询/DML 接口适配
- 导出/Excel 接口适配

## 二、数据库变更步骤

### 2.1 执行 DDL 脚本

在 `sys_portal` 表添加两个新字段：

```sql
ALTER TABLE sys_portal 
  ADD COLUMN `data_mode` VARCHAR(20) DEFAULT 'ENTITY' COMMENT '数据模式:ENTITY/MATRIX/DATASET',
  ADD COLUMN `table_id` VARCHAR(100) COMMENT '关联表格ID(MATRIX模式关联SysMatrix.id或tableName,DATASET模式关联SysPortalDataset.tableId)';

-- 创建索引以提升查询性能
CREATE INDEX idx_portal_datamode ON sys_portal(data_mode);
CREATE INDEX idx_portal_tableid ON sys_portal(table_id);
```

### 2.2 验证变更

```sql
-- 检查字段是否添加成功
DESC sys_portal;

-- 验证默认值
SELECT id, name, data_mode, table_id FROM sys_portal LIMIT 10;
```

## 三、BasePortalService 集成步骤

### 3.1 注入驱动 Bean

在 `BasePortalService` 中注入两个驱动：

```java
@Resource
protected MatrixDriver matrixDriver;

@Resource
protected DatasetDriver datasetDriver;
```

### 3.2 新增驱动分发方法

```java
/**
 * 根据Portal配置选择驱动
 * 
 * @param portalName Portal名称
 * @return 驱动实例（可能为null，表示使用默认ENTITY模式）
 */
@SuppressWarnings("unchecked")
protected PortalDataDriver<Map<String, Object>> getDriver(String portalName) {
    SysPortal portal = sysPortalService.getByName(portalName, PortalConfigContext.getPortalConfigRoleId());
    if (portal == null || FuncUtil.isEmpty(portal.getDataMode())) {
        return null; // 默认ENTITY模式
    }
    
    PortalDataMode dataMode = PortalDataMode.valueOf(portal.getDataMode());
    switch (dataMode) {
        case MATRIX:
            return (PortalDataDriver<Map<String, Object>>) (Object) matrixDriver;
        case DATASET:
            return (PortalDataDriver<Map<String, Object>>) (Object) datasetDriver;
        case ENTITY:
        default:
            return null;
    }
}

/**
 * 获取驱动对应的tableId
 */
protected String getTableId(String portalName) {
    SysPortal portal = sysPortalService.getByName(portalName, PortalConfigContext.getPortalConfigRoleId());
    return portal != null ? portal.getTableId() : portalName;
}
```

### 3.3 改造查询方法（示例）

在 `BasePortalService` 中重写 `queryByAdvancedReq` 方法：

```java
@Override
public Page<VO> queryByAdvancedReq(AdvancedQueryReq req) {
    // 尝试获取驱动
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        // 走JDBC驱动路径
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        Page<Map<String, Object>> driverPage = driver.queryPage(req, tableId, roleId);
        
        // 转换为VO类型（如果VO不是Map，需要做映射）
        if (getVoClass().equals(Map.class)) {
            return (Page<VO>) driverPage;
        } else {
            // 将Map转为VO（根据aliasMap映射）
            Page<VO> voPage = new Page<>(driverPage.getCurrent(), driverPage.getSize());
            voPage.setTotal(driverPage.getTotal());
            List<VO> voList = driverPage.getRecords().stream()
                .map(map -> ReflectionUtil.copyMapToBean(map, getVoClass()))
                .collect(Collectors.toList());
            voPage.setRecords(voList);
            return voPage;
        }
    } else {
        // 默认走现有MPJ路径
        return super.queryByAdvancedReq(req);
    }
}
```

### 3.4 改造列表查询方法

```java
@Override
public List<VO> select(AdvancedQueryReq req) {
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        List<Map<String, Object>> driverList = driver.queryList(req, tableId, roleId);
        
        if (getVoClass().equals(Map.class)) {
            return (List<VO>) driverList;
        } else {
            return driverList.stream()
                .map(map -> ReflectionUtil.copyMapToBean(map, getVoClass()))
                .collect(Collectors.toList());
        }
    } else {
        return super.select(req);
    }
}
```

### 3.5 改造 getAllData（用于树结构）

```java
@Override
public List<ENTITY> getAllData() {
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null && driver.getCapability().getTreeSupport()) {
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        List<Map<String, Object>> driverList = driver.getAllData(tableId, roleId);
        
        // 转为ENTITY
        return driverList.stream()
            .map(map -> ReflectionUtil.copyMapToBean(map, getEntityClass()))
            .collect(Collectors.toList());
    } else {
        return super.getAllData();
    }
}
```

### 3.6 改造 DML 方法（INSERT）

```java
@Override
public void add(ENTITY entity) {
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        // 检查驱动是否支持写入
        if (!driver.getCapability().getWritable()) {
            throw new UnsupportedOperationException("当前数据模式不支持新增操作");
        }
        
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        // 将ENTITY转为Map
        Map<String, Object> dataMap = ReflectionUtil.getHashMap(entity);
        
        // 调用驱动插入
        driver.insert(dataMap, tableId, roleId);
    } else {
        super.add(entity);
    }
}
```

### 3.7 改造 DML 方法（UPDATE）

```java
@Override
public void update(ENTITY entity) {
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        if (!driver.getCapability().getWritable()) {
            throw new UnsupportedOperationException("当前数据模式不支持更新操作");
        }
        
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        Map<String, Object> dataMap = ReflectionUtil.getHashMap(entity);
        driver.update(dataMap, tableId, roleId);
    } else {
        super.update(entity);
    }
}
```

### 3.8 改造 DML 方法（DELETE）

```java
@Override
public void delete(IdReqVO req) {
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        if (!driver.getCapability().getWritable()) {
            throw new UnsupportedOperationException("当前数据模式不支持删除操作");
        }
        
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        
        driver.delete(req.getId(), tableId, roleId);
    } else {
        super.delete(req);
    }
}
```

### 3.9 改造导出方法

```java
@Override
public byte[] export(List<VO> dataList, String portalName) {
    PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
    
    if (driver != null) {
        // 如果dataList为空，需要从驱动查询所有数据
        if (FuncUtil.isEmpty(dataList)) {
            String tableId = getTableId(portalName);
            String roleId = PortalConfigContext.getPortalConfigRoleId();
            
            List<Map<String, Object>> driverList = driver.getAllData(tableId, roleId);
            
            if (getVoClass().equals(Map.class)) {
                dataList = (List<VO>) driverList;
            } else {
                dataList = driverList.stream()
                    .map(map -> ReflectionUtil.copyMapToBean(map, getVoClass()))
                    .collect(Collectors.toList());
            }
        }
    }
    
    // 后续导出逻辑保持不变
    return super.export(dataList, portalName);
}
```

### 3.10 改造 aliasMap 构建（可选优化）

在 `run()` 方法中，根据 Portal 的 dataMode 决定是否走驱动的 aliasMap：

```java
@Override
public void run(String... args) {
    // 尝试获取驱动
    PortalDataDriver<Map<String, Object>> driver = getDriver(getPortalName());
    
    if (driver != null) {
        // 使用驱动构建aliasMap
        String tableId = getTableId(getPortalName());
        String roleId = PortalConfigContext.getPortalConfigRoleId();
        this.aliasMap = driver.buildAliasMap(tableId, roleId);
        this.summaryAliasMap = this.aliasMap; // Dataset/Matrix模式下二者一致
        this.havingFields = new HashSet<>(); // 根据需要填充
    } else {
        // 默认走现有基于VO注解的解析
        for (Field field : ReflectionUtil.getFields(getVoClass())) {
            setAlias(field, aliasMap);
            setSummaryAlias(field, summaryAliasMap);
            setHavingField(field, havingFields);
            setDynamicColumnField(field, selectApplyMap);
        }
    }
}
```

**注意**：`getPortalName()` 方法需要在具体的 PortalService 实现类中提供，或者通过其他方式获取当前 Portal 的名称。

## 四、使用示例

### 4.1 配置 Matrix 模式的 Portal

```sql
-- 假设已有一个 SysMatrix 配置（table_name='user_info'）
INSERT INTO sys_portal (role_id, name, display_name, url, bean, size, read_only, summary, advanced, 
                        tree_drag, table_drag, export_able, import_able, data_mode, table_id)
VALUES (1, 'userInfoPortal', '用户信息', '/api/user/info', 'userInfoController', 'small', 
        'N', 'N', 'Y', 'N', 'N', 'Y', 'Y', 'MATRIX', 'user_info');
```

### 4.2 配置 Dataset 模式的 Portal

```sql
-- 假设已有一个 SysPortalDataset 配置（table_id='order_stats_view'）
INSERT INTO sys_portal (role_id, name, display_name, url, bean, size, read_only, summary, advanced, 
                        tree_drag, table_drag, export_able, import_able, data_mode, table_id)
VALUES (1, 'orderStatsPortal', '订单统计', '/api/order/stats', 'orderStatsController', 'default', 
        'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'DATASET', 'order_stats_view');
```

### 4.3 前端调用（无需改动）

前端仍然调用原有的 Portal API 接口，后端根据 `data_mode` 自动路由到不同的驱动：

```javascript
// 查询
axios.post('/api/user/info/query', {
  currentPage: 1,
  pageSize: 10,
  condition: {
    fieldName: 'status',
    operator: '=',
    value: '1'
  }
});

// 新增（仅MATRIX模式支持）
axios.post('/api/user/info/add', {
  userName: 'test',
  userAge: 25
});

// 导出
axios.get('/api/user/info/export');
```

## 五、验证清单

### 5.1 Matrix 模式验证

- [ ] 创建一个 Matrix 配置（包含主键、普通字段、树字段）
- [ ] 在 sys_portal 中配置 `data_mode=MATRIX`，`table_id=<table_name>`
- [ ] 验证分页查询返回正确数据
- [ ] 验证高级查询（WHERE 条件、ORDER BY）
- [ ] 验证 INSERT 操作（自增主键/序列主键）
- [ ] 验证 UPDATE 操作（单主键/联合主键）
- [ ] 验证 DELETE 操作
- [ ] 验证树结构查询（getAllData）
- [ ] 验证导出功能

### 5.2 Dataset 模式验证

- [ ] 创建一个 Dataset 配置（2 表 LEFT JOIN）
- [ ] 配置 Dataset 列（包含复杂表达式如 CASE WHEN）
- [ ] 在 sys_portal 中配置 `data_mode=DATASET`，`table_id=<dataset_table_id>`
- [ ] 验证分页查询返回正确数据
- [ ] 验证高级查询（WHERE、HAVING、GROUP BY）
- [ ] 验证聚合字段（COUNT、SUM）正确走 HAVING
- [ ] 验证 DML 操作正确抛出异常
- [ ] 验证导出功能

### 5.3 兼容性验证

- [ ] 验证现有 ENTITY 模式的 Portal 不受影响
- [ ] 验证多数据源切换正确（通过 `data_source` 字段）
- [ ] 验证事务一致性（批量 DML）
- [ ] 验证 SQL 参数化（无注入风险）

## 六、常见问题

### 6.1 如何获取 portalName？

在 `BasePortalService` 的子类中，通常通过以下方式获取：

```java
protected String getPortalName() {
    // 方式1：通过当前类名推断（如 UserInfoPortalService -> userInfo）
    String className = this.getClass().getSimpleName();
    return Introspector.decapitalize(className.replace("PortalService", ""));
    
    // 方式2：通过配置文件或注解指定
    // return this.portalConfigName;
}
```

### 6.2 Dataset 的 DML 如何支持？

当前 Dataset 模式设计为只读。如果需要支持写入，可以：

1. **扩展 DatasetDriver**：增加"可写表映射"配置，指定哪些字段对应哪张表
2. **生成多条 SQL**：UPDATE/INSERT 按映射拆分到多张表，在同一事务内执行
3. **权限控制**：只允许更新主表字段

### 6.3 如何处理序列主键？

Matrix 模式下，如果主键字段配置了 `sequence`（序列名），需要在 `beforeAdd` 钩子中填充：

```java
@Override
public void beforeAdd(ENTITY entity) {
    // 假设主键字段为 id，序列名为 SEQ_USER_ID
    if (FuncUtil.isEmpty(LambdaUtil.getValue(entity, ENTITY::getId))) {
        String seqValue = saSequenceService.getSeq("SEQ_USER_ID");
        LambdaUtil.setValue(entity, ENTITY::setId, seqValue);
    }
    super.beforeAdd(entity);
}
```

### 6.4 如何处理树结构的 pid 更新？

在 Matrix 模式下，通过 `MatrixDriver.update()` 更新 pid 字段后，需要重新计算 order：

```java
@Override
public void afterUpdate(ENTITY entity) {
    // 如果更新了pid，重新排序
    if (pidChanged(entity)) {
        reorderChildren(entity.getPid());
    }
    super.afterUpdate(entity);
}
```

## 七、后续优化建议

1. **缓存优化**：对频繁查询的 Matrix/Dataset 元数据增加本地缓存
2. **批量 DML 优化**：将循环单条改为一次性生成批量 SQL
3. **SQL 监控**：集成慢查询日志与执行计划分析
4. **动态 VO 生成**：对于 Dataset 模式，可以根据列配置动态生成 VO 类
5. **视图物化**：对复杂 Dataset 提供物化视图创建能力

---

**文档版本**：v1.0  
**更新日期**：2025-11-24  
**适用版本**：mpbe-api v2.0+
