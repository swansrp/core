# SQL代码生成指南

## 概述

本文档说明如何从数据库SQL DDL语句生成完整的Java代码文件集。

### 普通表代码生成（8个文件）

**完整代码生成包含8个文件**：

1. **Entity** - 数据库实体类
2. **Mapper接口** - MyBatis数据访问层
3. **Mapper XML** - MyBatis映射文件
4. **Repository Service** - 数据仓库服务（仅包含业务逻辑，不包含DDL）
5. **Schema Service** - 数据库初始化服务（包含DDL定义和升级脚本）
6. **VO** - 值对象（前端交互）
7. **Portal Service** - 门户业务服务
8. **Portal Controller** - 门户控制器（REST API）

### 多对多关系表代码生成（6个文件）

**对于联合主键的多对多关系表，生成6个文件**：

1. **Entity** - 数据库实体类（使用`@MppMultiId`标注联合主键）
2. **Mapper接口** - MyBatis数据访问层
3. **Mapper XML** - MyBatis映射文件
4. **Repository Service** - 数据仓库服务（仅包含业务逻辑）
5. **Schema Service** - 数据库初始化服务（包含DDL定义）
6. **BindController** - 绑定关系控制器（管理多对多关系的绑定/解绑操作）

**注意**：多对多关系表**不生成**PortalService、PortalController和VO，因为使用两个关联实体的VO。

## 目录结构

```
${module_name}/src/main/java/com/bidr/*/
├── dao/
│   ├── entity/          # 实体类
│   ├── mapper/          # Mapper接口与XML
│   ├── repository/      # Repository Service（业务逻辑）
│   └── schema/          # Schema Service（数据库初始化）
├── vo/                  # 值对象（VO）
├── controller/          # Portal Controller
└── service/             # Portal Service
```

## 生成步骤

### 1. Entity类生成

#### 1.1 普通主键实体

根据SQL表结构生成实体类：

**SQL示例**：

```sql
CREATE TABLE IF NOT EXISTS `schema_module`
(
    `id`
    bigint
(
    20
) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `production_id` bigint
(
    20
) NOT NULL COMMENT '产品id',
    `title` varchar
(
    20
) NOT NULL COMMENT '名称',
    `description` varchar
(
    50
) NOT NULL COMMENT '描述',
    `multi` varchar
(
    20
) NOT NULL COMMENT '是否多组数据',
    `sort` int
(
    11
) NOT NULL DEFAULT '0' COMMENT '顺序',
    `create_by` varchar
(
    50
) DEFAULT NULL COMMENT '创建者',
    `create_at` datetime
(
    3
) DEFAULT CURRENT_TIMESTAMP
(
    3
) COMMENT '创建时间',
    `update_by` varchar
(
    50
) DEFAULT NULL COMMENT '更新者',
    `update_at` datetime
(
    3
) DEFAULT CURRENT_TIMESTAMP
(
    3
) ON UPDATE CURRENT_TIMESTAMP
(
    3
) COMMENT '更新时间',
    `valid` char
(
    1
) DEFAULT '1' COMMENT '有效性',
    PRIMARY KEY
(
    `id`
),
    KEY `production_id`
(
    `production_id`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块';
```

**生成规则**：

- 包名：`com.bidr.mpbe.dao.entity`
- 类名：采用驼峰命名（如：`schema_module` → `SchemaModule`）
- 主键注解：单主键使用 `@TableId(value = "id", type = IdType.AUTO)`
- 字段注解：`@TableField(value = "字段名")` 或含字段填充配置
- API文档：`@ApiModel(description="表注释")` 和 `@ApiModelProperty(value="字段注释")`
- 表名注解：`@TableName(value = "mpbe.schema_module")`（包含schema名称）
- 类注解：如需自动填充审计字段，添加 `@AccountContextFill` 注解
- **审计字段配置**（create_by、create_at、update_by、update_at）需配置字段填充

**生成代码**：

```java
package com.bidr.mpbe.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

import lombok.Data;

@ApiModel(description = "模块")
@Data
@AccountContextFill
@TableName(value = "mpbe.schema_module")
public class SchemaModule {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @TableField(value = "production_id")
    @ApiModelProperty(value = "产品id")
    private Long productionId;

    @TableField(value = "title")
    @ApiModelProperty(value = "名称")
    private String title;

    @TableField(value = "description")
    @ApiModelProperty(value = "描述")
    private String description;

    @TableField(value = "multi")
    @ApiModelProperty(value = "是否多组数据")
    private String multi;

    @TableField(value = "sort")
    @ApiModelProperty(value = "顺序")
    private Integer sort;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
```

#### 1.2 联合主键实体

**SQL示例**：

```sql
CREATE TABLE IF NOT EXISTS `eval_enterprise_user_rel`
(
    `customer_number`
    varchar
(
    50
) NOT NULL COMMENT '用户编码',
    `enterprise_id` bigint
(
    20
) NOT NULL COMMENT '企业ID',
    PRIMARY KEY
(
    `customer_number`,
    `enterprise_id`
) USING BTREE,
    KEY `enterprise_id`
(
    `enterprise_id`
)
  USING BTREE,
    KEY `product_id`
(
    `customer_number`
)
  USING BTREE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT= DYNAMIC COMMENT='企业产品树';
```

**生成规则**：

- **联合主键字段**使用 `@MppMultiId` 注解标注（来自 mybatisplus-plus）
- **不使用** `@TableId` 注解
- 其他规则与普通实体相同

**生成代码**：
``java
package com.bidr.mpbe.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(description = "企业产品树")
@Data
@TableName(value = "mpbe.eval_enterprise_user_rel")
public class EvalEnterpriseUserRel {
/**

* 用户编码
  */
  @MppMultiId
  @TableField(value = "customer_number")
  @ApiModelProperty(value = "用户编码")
  private String customerNumber;

  /**
    * 企业ID
      */
      @MppMultiId
      @TableField(value = "enterprise_id")
      @ApiModelProperty(value = "企业ID")
      private Long enterpriseId;

}

```

### 2. Mapper接口生成

**位置**：`${module_name}/src/main/java/com/bidr/*/dao/mapper/`

**生成规则**：
- 接口名：`{Entity}Mapper`
- 继承：`MyBaseMapper<Entity>`
- 无需添加额外方法

**Java代码**：
``java
package com.bidr.mpbe.dao.mapper;

import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.mpbe.dao.entity.SchemaModule;

public interface SchemaModuleMapper extends MyBaseMapper<SchemaModule> {
}
```

### 3. Mapper XML生成

**位置**：`${module_name}/src/main/java/com/bidr/*/dao/mapper/`

**生成规则**：

- 文件名：`{Entity}Mapper.xml`
- namespace：对应Mapper接口的完整类名
- resultMap：映射所有字段
- Base_Column_List：包含所有列名（使用反引号包裹SQL关键字，如 \`valid\`）

**XML代码**：

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bidr.mpbe.dao.mapper.SchemaModuleMapper">
  <resultMap id="BaseResultMap" type="com.bidr.mpbe.dao.entity.SchemaModule">
    <id column="id" jdbcType="BIGINT" property="id" />
    <result column="production_id" jdbcType="BIGINT" property="productionId" />
    <result column="title" jdbcType="VARCHAR" property="title" />
    <result column="description" jdbcType="VARCHAR" property="description" />
    <result column="multi" jdbcType="VARCHAR" property="multi" />
    <result column="sort" jdbcType="INTEGER" property="sort" />
    <result column="create_by" jdbcType="VARCHAR" property="createBy" />
    <result column="create_at" jdbcType="TIMESTAMP" property="createAt" />
    <result column="update_by" jdbcType="VARCHAR" property="updateBy" />
    <result column="update_at" jdbcType="TIMESTAMP" property="updateAt" />
    <result column="valid" jdbcType="CHAR" property="valid" />
  </resultMap>
  <sql id="Base_Column_List">
    id, production_id, title, description, multi, sort, create_by, create_at, update_by, 
    update_at, `valid`
  </sql>
</mapper>
```

### 4. Repository Service生成

**位置**：`${module_name}/src/main/java/com/bidr/*/dao/repository/`

**生成规则**：

- 类名：`{Entity}Service`
- 注解：`@Service`
- 继承：`BaseSqlRepo<{Entity}Mapper, {Entity}>`
- 静态块：包含CREATE TABLE DDL语句（直接从SQL复制，保持格式）

**Java代码**：

```
package com.bidr.mpbe.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.mpbe.dao.entity.SchemaModule;
import com.bidr.mpbe.dao.mapper.SchemaModuleMapper;
import org.springframework.stereotype.Service;

@Service
public class SchemaModuleService extends BaseSqlRepo<SchemaModuleMapper, SchemaModule> {
    // 仅包含业务逻辑方法，不包含DDL定义。
}
```

### 5. Schema Service生成

**位置**：`${module_name}/src/main/java/com/bidr/*/dao/schema/`

**职责分离**：

Schema Service是数据库初始化服务层，负责管理数据库DDL定义和升级脚本。与Repository Service分离，遵循单一职责原则：

- **Schema Service**：仅存储数据库实体定义（DDL）和升级脚本
- **Repository Service**：仅包含业务逻辑方法（不包含DDL）

**生成规则**：

- 类名：`{Entity}Schema`
- 注解：`@Service`
- 继承：`BaseMybatisSchema<{Entity}>`
- 静态块：包含**CREATE TABLE DDL语句**与可选的**setUpgradeDDL升级脚本**
- 无需其他业务方法

**Java代码示例**：

```java
package com.bidr.mpbe.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.mpbe.dao.entity.SchemaModule;
import org.springframework.stereotype.Service;

@Service
public class SchemaModuleSchema extends BaseMybatisSchema<SchemaModule> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `schema_module` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `production_id` bigint(20) NOT NULL COMMENT '产品id',\n" +
                "  `title` varchar(20) NOT NULL COMMENT '名称',\n" +
                "  `description` varchar(50) NOT NULL COMMENT '描述',\n" +
                "  `multi` varchar(20) NOT NULL COMMENT '是否多组数据',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '顺序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `production_id` (`production_id`)\n" +
                ") COMMENT='模块';");
        
        // 可选：如果有数据库升级脚本，可以添加setUpgradeDDL
        // setUpgradeDDL(1, "ALTER TABLE `schema_module` ADD COLUMN `new_field` VARCHAR(50) COMMENT '新字段' AFTER `sort`;");
    }
}
```

### 6. Portal Service生成

### 6. Portal Service生成

**位置**：`${module_name}/src/main/java/com/bidr/*/service/`

**生成规则**：

- 类名：`{Entity}PortalService`
- 注解：`@Service`、`@RequiredArgsConstructor`
- 继承：`BasePortalService<{Entity}, {Entity}VO>`（注意：第二个泡类为VO！）
- 需要注入Repository Service并继承

**Java代码**：

```
package com.bidr.mpbe.manage.service.schema;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.mpbe.dao.entity.SchemaModule;
import com.bidr.mpbe.vo.SchemaModuleVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchemaModulePortalService extends BasePortalService<SchemaModule, SchemaModuleVO> {
    // 业务逻辑方法
}
```

### 7. Portal Controller生成

**位置**：`${module_name}/src/main/java/com/bidr/*/dao/controller/`

**生成规则**：

- 类名：`{Entity}PortalController`
- 注解：
    - `@Api(tags = "生物学评价 - 模版配置 - {功能名}")`
    - `@RestController`
    - `@RequiredArgsConstructor`
    - `@RequestMapping(path = {"/web/schema/{entity小写}"})`
- 继承：`BaseAdminOrderController<{Entity}, {Entity}VO>`（注意：第二个泡类为VO！）
- 注入字段：`private final {Entity}PortalService {entity}PortalService;`
- 实现方法：
    - `getPortalService()`: 返回注入的PortalService
    - `id()`: 返回实体ID字段的Lambda表达式
    - `order()`: 返回实体排序字段的Lambda表达式

**Java代码示例1（无排序字段）**：

```java
package com.bidr.mpbe.manage.controller.schema;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.mpbe.dao.entity.SchemaModule;
import com.bidr.mpbe.manage.service.schema.SchemaModulePortalService;
import com.bidr.mpbe.vo.SchemaModuleVO;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块管理控制器
 *
 * @author sharp
 */
@Api(tags = "生物学评价 - 模版配置 - 模块")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/schema/module"})
public class SchemaModulePortalController extends BaseAdminController<SchemaModule, SchemaModuleVO> {

    private final SchemaModulePortalService schemaModulePortalService;

    @Override
    public PortalCommonService<SchemaModule, SchemaModuleVO> getPortalService() {
        return schemaModulePortalService;
    }
}
```

**Java代码示例2（有排序字段）**：

```java
package com.bidr.mpbe.manage.controller.schema;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.mpbe.dao.entity.SchemaModule;
import com.bidr.mpbe.manage.service.schema.SchemaModulePortalService;
import com.bidr.mpbe.vo.SchemaModuleVO;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块管理控制器（支持排序）
 *
 * @author sharp
 */
@Api(tags = "生物学评价 - 模版配置 - 模块")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/schema/module"})
public class SchemaModulePortalController extends BaseAdminOrderController<SchemaModule, SchemaModuleVO> {

    private final SchemaModulePortalService schemaModulePortalService;

    @Override
    public PortalCommonService<SchemaModule, SchemaModuleVO> getPortalService() {
        return schemaModulePortalService;
    }

    @Override
    protected SFunction<SchemaModule, ?> id() {
        return SchemaModule::getId;
    }

    @Override
    protected SFunction<SchemaModule, Integer> order() {
        return SchemaModule::getSort;
    }
}
```

## 字段类型映射规则

### SQL类型 → Java类型

| SQL类型          | Java类型       | 说明   |
|----------------|--------------|------|
| `bigint(20)`   | `Long`       | 长整型  |
| `int(11)`      | `Integer`    | 整型   |
| `varchar(n)`   | `String`     | 字符串  |
| `char(n)`      | `String`     | 字符串  |
| `datetime(3)`  | `Date`       | 日期时间 |
| `text`         | `String`     | 长文本  |
| `decimal(m,n)` | `BigDecimal` | 精确小数 |
| `tinyint(1)`   | `String`     | 字符标识 |

### MyBatis jdbcType映射

| SQL类型      | jdbcType      |
|------------|---------------|
| `bigint`   | `BIGINT`      |
| `int`      | `INTEGER`     |
| `varchar`  | `VARCHAR`     |
| `char`     | `CHAR`        |
| `datetime` | `TIMESTAMP`   |
| `text`     | `LONGVARCHAR` |
| `decimal`  | `DECIMAL`     |

## 命名规则

### 1. 下划线转驼峰

- **表名**：`schema_module` → `SchemaModule`
- **字段名**：`production_id` → `productionId`

### 2. RequestMapping路径

- 格式：`/web/schema/{entity小写}`
- 示例：`SchemaModule` → `/web/schema/module`

### 3. Swagger标签

- 格式：`{项目名称} - {模块名称} - {功能名}`
- 示例：`@Api(tags = "生物学评价 - 模版配置 - 模块")`

## 特殊处理

### 1. SQL关键字字段

对于SQL关键字（如 `valid`、`name` 等），需要特殊处理：

**Entity注解**（@TableField中使用反引号）：

```
// 对于关键字字段name
@TableField(value = "`name`")
private String name;

// 对于关键字字段valid
@TableField(value = "`valid`")
private String valid;
```

**Mapper XML中的Base_Column_List**（SQL中使用反引号）：

```
<sql id="Base_Column_List">
    id, `name`, short_name, enterprise_number, credit_code, legal_person, established_at, 
    registered_capital, economic_type, industry, business_scope, address, contact_name, 
    contact_phone, contact_email, website, status, confirm_by, confirm_at, create_by, 
    create_at, update_by, update_at, `valid`
</sql>
```

**常见MySQL关键字列表**：
`name`, `order`, `group`, `status`, `valid`, `key`, `value`, `type`, `date`, `index`, `select`, `from`, `where`, `insert`, `update`, `delete`

- 静态块：**不包含**DDL定义（仅包含业务逻辑方法）

### 2. 联合主键处理

- 使用 `@TableField` 替代 `@TableId`
- 添加 `@MppMultiId`
- 在 `resultMap` 中，所有主键字段仍使用 `<id>` 标签

### 3. 自增主键

```
@TableId(value = "id", type = IdType.AUTO)
```

### 4. 非自增主键

```
@TableId(value = "id", type = IdType.INPUT)
```

## 文件清单

为SchemaModule实体生成的完整文件列表（共7个文件）：

1. **Entity**: `${module_name}/src/main/java/com/bidr/*/dao/entity/SchemaModule.java`
2. **Mapper接口**: `${module_name}/src/main/java/com/bidr/*/dao/mapper/SchemaModuleMapper.java`
3. **Mapper XML**: `${module_name}/src/main/java/com/bidr/*/dao/mapper/SchemaModuleMapper.xml`
4. **Repository Service**: `${module_name}/src/main/java/com/bidr/*/dao/repository/SchemaModuleService.java` (
   仅包含业务逻辑)
5. **Schema Service**: `${module_name}/src/main/java/com/bidr/*/dao/schema/SchemaModuleSchema.java` (包含DDL和升级脚本)
6. **VO值对象**: `${module_name}/src/main/java/com/bidr/*/vo/SchemaModuleVO.java`
7. **Portal Service**: `${module_name}/src/main/java/com/bidr/*/service/schema/SchemaModulePortalService.java`
8. **Portal Controller**: `${module_name}/src/main/java/com/bidr/*/controller/SchemaModulePortalController.java`

## 注意事项

1. **包路径一致性**：确保所有文件的包路径遵循项目约定
2. **JavaDoc注释**：
    - 每个类、接口必须有JavaDoc 其中 @author 是 `sharp` 不要写 @date
    - 如果知道当前时间则写@since 当前日期 + 时间 如果不知道 就不要写@since
    - 每个public方法必须有JavaDoc描述
    - 记录参数、返回值、异常等信息
3. **导入语句完整性**：确保所有必需的import语句都已添加
4. **注释保留**：从SQL的COMMENT字段提取注释并生成Javadoc
5. **Schema名称**：`@TableName` 注解必须包含schema名（如 `mpbe.schema_module`）
6. **DDL不再嵌入Repository Service**：仅正常存放Schema Service中。
7. **Schema Service指责**：Repository Service的Schema子类，管理数据库DDL定义和升级脚本（接收地定义前司詳売点序号功能）ィンターフェース序号没迍加詳売点。
    - **Entity类**需添加 `@AccountContextFill`
      注解，用于自动填充审计信息（导入：`import com.bidr.authorization.mybatis.anno.AccountContextFill`）
    - **create_by** 字段：`@TableField(value = "create_by", fill = FieldFill.INSERT)`
    - **create_at** 字段：`@TableField(value = "create_at", fill = FieldFill.INSERT)`
    - **update_by** 字段：`@TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)`
    - **update_at** 字段：`@TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)`
    - **valid** 字段：`@TableField(value = "`valid`")`（处理SQL关键字）

## VO值对象生成

**位置**：`${module_name}/src/main/java/com/bidr/*/vo/`

**生成规则**：

- 类名：`{Entity}VO`（如：`SchemaModule` → `SchemaModuleVO`）
- 继承：`BaseVO`
- 使用 `@EqualsAndHashCode(callSuper = true)` 注解
- **主键字段id**：使用 `@PortalIdField` 注解替代Entity中的 `@TableId`
- **名称字段**（title/name/displayName/label）：使用 `@PortalNameField` 注解
- **排序字段**（sort/order/displayOrder）：使用 `@PortalOrderField` 注解
- **父ID字段**（pid/parentId）：使用 `@PortalPidField` 注解（仅限树形结构）
- **文本域字段**（text/longtext类型）：使用 `@PortalTextAreaField` 注解
- **金钱字段**（price/amount/money/cost/fee等）：使用 `@PortalMoneyField` 注解
- **百分比字段**（rate/ratio/percent等）：使用 `@PortalPercentField` 注解
- 包含所有Entity字段（可选：根据前端需求选择性传递字段）
- 添加**JavaDoc注释**

**Java代码示例**：

```java
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.config.PortalPidField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模块VO - 前端交互
 *
 * @author sharp
 * @since YYYY-MM-DD
 */
@ApiModel(description = "模块")
@Data
@EqualsAndHashCode(callSuper = true)
public class SchemaModuleVO extends BaseVO {
    /**
     * 主键ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty("产品id")
    private Long productionId;

    /**
     * 模块名称
     */
    @PortalNameField
    @ApiModelProperty("模块名称")
    private String title;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;

    // ... 其他字段
}
```

**树形结构VO示例**（包含pid字段）：

```java
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.config.PortalPidField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品VO - 树形结构
 *
 * @author sharp
 * @since YYYY-MM-DD
 */
@ApiModel(description = "产品")
@Data
@EqualsAndHashCode(callSuper = true)
public class SchemaProductionVO extends BaseVO {
    /**
     * 主键ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 父ID
     */
    @PortalPidField
    @ApiModelProperty(value = "父ID")
    private Long pid;

    /**
     * 产品名称
     */
    @PortalNameField
    @ApiModelProperty("产品名称")
    private String title;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty("排序")
    private Integer sort;

    // ... 其他字段
}
```

**@PortalIdField注解说明**：

- 位置：`com.bidr.admin.config.PortalIdField`
- 作用：标记VO中的主键字段，用于Portal层识别实体的唯一标识
- 使用规则：
    - 每个VO类必须有且仅有一个被 `@PortalIdField` 标注的字段（通常是 `id`）
    - Entity中使用 `@TableId` 注解，VO中使用 `@PortalIdField` 注解
    - 这样实现了数据库层和展示层的分离

**@PortalNameField注解说明**：

- 位置：`com.bidr.admin.config.PortalNameField`
- 作用：标记VO中表述名字的字段，用于Portal层显示实体名称
- 适用字段：`name`、`title`、`displayName`、`label` 等名称类字段
- 使用规则：
    - 每个VO类建议有且仅有一个被 `@PortalNameField` 标注的字段
    - 优先选择：`title` > `name` > `displayName` > `label`

**@PortalOrderField注解说明**：

- 位置：`com.bidr.admin.config.PortalOrderField`
- 作用：标记VO中表述顺序的字段，用于Portal层排序显示
- 适用字段：`sort`、`order`、`displayOrder`、`display_order` 等排序类字段
- 使用规则：
    - 每个VO类建议有且仅有一个被 `@PortalOrderField` 标注的字段
    - 优先选择：`sort` > `order` > `displayOrder`

**@PortalPidField注解说明**：

- 位置：`com.bidr.admin.config.PortalPidField`
- 作用：标记VO中表述父ID的字段，用于Portal层树形结构显示
- 适用字段：`pid`、`parentId`、`parent_id` 等父节点关联字段
- 使用规则：
    - 仅在有树形结构的VO类中使用
    - 每个树形结构VO类必须有且仅有一个被 `@PortalPidField` 标注的字段
    - 优先选择：`pid` > `parentId`

**@PortalTextAreaField注解说明**：

- 位置：`com.bidr.admin.config.PortalTextAreaField`
- 作用：标记VO中的文本域字段，用于Portal层使用多行文本输入框显示
- 适用字段：数据库中的`text`、`longtext`类型字段
- 常见字段名：`description`、`content`、`remark`、`comment`、`message` 等
- 使用规则：
    - 当字段内容较长或需要多行显示时使用
    - 自动在前端渲染为多行文本输入框（textarea）

**@PortalMoneyField注解说明**：

- 位置：`com.bidr.admin.config.PortalMoneyField`
- 作用：标记VO中的金钱类型字段，用于Portal层格式化显示和输入金额
- 适用字段：数据库中存储金额的字段（通常为`DECIMAL`或`BIGINT`类型）
- 常见字段名：`price`、`amount`、`money`、`cost`、`fee`、`salary` 等
- 注解参数：
    - `unit`：显示单位转换，默认`10000`（万元）
    - `fix`：小数位数，默认`2`
- 使用规则：
    - **数据库永远存储元**
    - **VO中默认不用写参数**，直接使用 `@PortalMoneyField`
    - 如果前端需要以**万元**显示，设置 `unit = 10000`
    - 如果前端需要以**元**显示，设置 `unit = 1` 或不设置参数使用默认值
    - 自动在前端显示为货币格式（如￥12,345.67）

**@PortalPercentField注解说明**：

- 位置：`com.bidr.admin.config.PortalPercentField`
- 作用：标记VO中的百分比类型字段，用于Portal层格式化显示和输入百分比
- 适用字段：数据库中存储百分比的字段（通常为`DECIMAL`或`INT`类型）
- 常见字段名：`rate`、`ratio`、`percent`、`percentage`、`discount` 等
- 注解参数：
    - `unit`：单位转换，默认`100`（百分比）
    - `fix`：小数位数，默认`2`
- 使用规则：
    - **VO中默认不用写参数**，直接使用 `@PortalPercentField`
    - 自动在前端显示为百分比格式（如 85.50%）
    - 支持单位转换（如数据库存储 0.8550，显示为 85.50%）

## Portal Service与Portal Controller的VO配置

**重点规范**：Portal Service和Portal Controller的**第二个泛型参数必须是VO类**，不是Entity！

### VO类定义需求

**VO类必须包含：**

1. 继承 `BaseVO`
2. 使用 `@EqualsAndHashCode(callSuper = true)` 注解（与Lombok的@Data注解配合，确保正確处理父类字段）
3. 主键字段id使用 `@PortalIdField` 注解（源于`com.bidr.admin.config.PortalIdField`）
4. 名称字段（title/name/displayName/label）使用 `@PortalNameField` 注解（源于`com.bidr.admin.config.PortalNameField`）
5. 排序字段（sort/order/displayOrder）使用 `@PortalOrderField` 注解（源于`com.bidr.admin.config.PortalOrderField`）
6. **树形结构**：父ID字段（pid/parentId）使用 `@PortalPidField` 注解（源于`com.bidr.admin.config.PortalPidField`）
7. **文本域字段**：数据库text/longtext类型字段使用 `@PortalTextAreaField` 注解（源于`com.bidr.admin.config.PortalTextAreaField`）
8. **金钱字段**：金额类型字段使用 `@PortalMoneyField` 注解（源于`com.bidr.admin.config.PortalMoneyField`）
9. **百分比字段**：百分比类型字段使用 `@PortalPercentField` 注解（源于`com.bidr.admin.config.PortalPercentField`）
10. 其他字段使用 `@ApiModelProperty` 注解
11. 添加JavaDoc注释（@author sharp，@since 当前日期）

```java
// Portal Service
@Service
public class SchemaModulePortalService extends BasePortalService<SchemaModule, SchemaModuleVO> {
    // ...
}

// Portal Controller
@RestController
public class SchemaModulePortalController extends BaseAdminController<SchemaModule, SchemaModuleVO> {
    // ...
}
```

## Portal Controller的继承规则

**关键规则**：

| 字段情况                                | 继承的Controller                                | 必须实现的方法                      | 说明                                                  |
|-------------------------------------|----------------------------------------------|------------------------------|-----------------------------------------------------|
| 无 `sort`/`order` 字段且无 `pid` 字段       | `BaseAdminController<Entity, EntityVO>`      | `getPortalService()`         | 基础CRUD功能，无排序无树形结构                                   |
| 有 `sort`/`order`/`display_order` 字段 | `BaseAdminOrderController<Entity, EntityVO>` | `getPortalService()`<br>`id()`<br>`order()` | 支持排序功能，返回排序字段：`Entity::getSort` 或 `Entity::getOrder` |
| 有 `pid` 字段（树形结构）                    | `BaseAdminTreeController<Entity, EntityVO>`  | `getPortalService()`<br>`id()`<br>`pid()`<br>`name()`<br>`order()` | 树形结构，返回父ID字段：`Entity::getPid`                        |

**方法实现说明**：

### 1. BaseAdminController（基础控制器）

**必须实现的方法**：

```java
/**
 * 获取Portal业务服务
 */
@Override
public PortalCommonService<SchemaModule, SchemaModuleVO> getPortalService() {
    return schemaModulePortalService;
}
```

**无需实现其他方法**。

### 2. BaseAdminOrderController（排序控制器）

**必须实现的方法**：

```java
/**
 * 获取Portal业务服务
 */
@Override
public PortalCommonService<SchemaModule, SchemaModuleVO> getPortalService() {
    return schemaModulePortalService;
}

/**
 * 获取实体主键（用于排序功能）
 */
@Override
protected SFunction<SchemaModule, ?> id() {
    return SchemaModule::getId;
}

/**
 * 获取排序字段
 */
@Override
protected SFunction<SchemaModule, Integer> order() {
    return SchemaModule::getSort;  // 或 getOrder / getDisplayOrder
}
```

### 3. BaseAdminTreeController（树形控制器）

**必须实现的方法**：

```java
/**
 * 获取Portal业务服务
 */
@Override
public PortalCommonService<SchemaModule, SchemaModuleVO> getPortalService() {
    return schemaModulePortalService;
}

/**
 * 获取实体主键（继承自BaseAdminOrderController）
 */
@Override
protected SFunction<SchemaModule, ?> id() {
    return SchemaModule::getId;
}

/**
 * 获取排序字段（继承自BaseAdminOrderController）
 */
@Override
protected SFunction<SchemaModule, Integer> order() {
    return SchemaModule::getSort;  // 或 getOrder / getDisplayOrder
}

/**
 * 获取父ID字段（树形结构专用）
 */
@Override
protected SFunction<SchemaModule, ?> pid() {
    return SchemaModule::getPid;
}

/**
 * 获取名称字段（树形结构专用）
 * 对应字段通常是：name 或 title
 */
@Override
protected SFunction<SchemaModule, String> name() {
    return SchemaModule::getTitle;  // 或 getName
}
```

## 使用方法

当提供SQL DDL语句时，请按照以下格式：

```
CREATE TABLE IF NOT EXISTS `table_name` (
  字段定义...
) COMMENT='表注释';
```

AI将根据此指南自动生成相应文件。

## 多对多关系表（联合主键表）生成规则

### 识别多对多关系表

多对多关系表通常具有以下特征：
- 使用**联合主键**（由两个或多个外键组成）
- 表名通常包含两个实体名（如`das_exam_team`连接`das_exam`和`das_team`）
- 主要用于描述两个实体之间的关系

**SQL示例**：

```sql
CREATE TABLE IF NOT EXISTS `das_exam_team` (
  `team_id` bigint(20) NOT NULL COMMENT '队伍id',
  `exam_id` bigint(20) NOT NULL COMMENT '考试id',
  `team_leader` varchar(20) DEFAULT NULL COMMENT '领队姓名',
  `phone_number` varchar(20) DEFAULT NULL COMMENT '联系方式',
  PRIMARY KEY (`team_id`,`exam_id`),
  KEY `team_id` (`team_id`),
  KEY `exam_id` (`exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试队伍报名';
```

### 生成文件清单（6个文件）

对于多对多关系表（如`das_exam_team`），生成**6个文件**：

1. **Entity** - `DasExamTeam.java`
2. **Mapper接口** - `DasExamTeamMapper.java`
3. **Mapper XML** - `DasExamTeamMapper.xml`
4. **Repository Service** - `DasExamTeamService.java`
5. **Schema Service** - `DasExamTeamSchema.java`
6. **BindController** - `DasExamTeamBindController.java`

**不生成的文件**：
- ✗ PortalService
- ✗ PortalController
- ✗ VO（使用两个关联实体的VO）

### BindController生成规则

**位置**：`${module_name}/src/main/java/com/bidr/*/controller/`

**生成规则**：

- 类名：`{Entity1}{Entity2}BindController`（如：`DasExamTeamBindController`）
- 注解：
  - `@Api(tags = "功能模块 - 功能名称")`
  - `@RestController`
  - `@RequiredArgsConstructor`
  - `@RequestMapping(path = {"/web/{Entity1Portal}/{Entity2Portal}"})`
- 继承：`BaseBindController<ENTITY1, BIND, ENTITY2, ENTITY1_VO, ENTITY2_VO>`
- 必须实现4个方法：
  - `bindAttachId()` - 关联表中指向第二个实体的外键
  - `attachId()` - 第二个实体的主键
  - `bindEntityId()` - 关联表中指向第一个实体的外键
  - `entityId()` - 第一个实体的主键

**Java代码**：

```java
package com.bidr.das.controller.enrollment;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.das.dao.entity.DasExam;
import com.bidr.das.dao.entity.DasExamTeam;
import com.bidr.das.dao.entity.DasTeam;
import com.bidr.das.vo.enrollment.TeamVO;
import com.bidr.das.vo.exam.ExamVO;
import com.bidr.kernel.controller.BaseBindController;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考试队伍报名绑定控制器
 *
 * @author sharp
 * @since 2025-11-08
 */
@Api(tags = "考试管理 - 考试队伍报名")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/Exam/Team"})  // 由两个实体的@AdminPortal值组成
public class DasExamTeamBindController extends BaseBindController<DasExam, DasExamTeam, DasTeam, ExamVO, TeamVO> {

    /**
     * 关联表中指向第二个实体（Team）的外键
     */
    @Override
    protected SFunction<DasExamTeam, ?> bindAttachId() {
        return DasExamTeam::getTeamId;
    }

    /**
     * 第二个实体（Team）的主键
     */
    @Override
    protected SFunction<DasTeam, ?> attachId() {
        return DasTeam::getId;
    }

    /**
     * 关联表中指向第一个实体（Exam）的外键
     */
    @Override
    protected SFunction<DasExamTeam, ?> bindEntityId() {
        return DasExamTeam::getExamId;
    }

    /**
     * 第一个实体（Exam）的主键
     */
    @Override
    protected SFunction<DasExam, ?> entityId() {
        return DasExam::getId;
    }
}
```

### RequestMapping路径规则

BindController的`@RequestMapping`路径由两个关联实体的`@AdminPortal`注解值决定：

**格式**：`/web/{Entity1Portal}/{Entity2Portal}`

**示例**：

```java
// ExamPortalController.java - 第一个实体的Controller
@Api(tags = "DAS - 考试管理 - 考试")
@AdminPortal("Exam")  // ← 第一个路径部分
@RestController
@RequestMapping(path = {"/web/das/exam"})
public class ExamPortalController extends BaseAdminController<DasExam, ExamVO> {
    // ...
}

// DasTeamPortalController.java - 第二个实体的Controller
@Api(tags = "报名管理 - 考试队伍")
@AdminPortal("Team")  // ← 第二个路径部分
@RestController
@RequestMapping(path = {"/web/team"})
public class DasTeamPortalController extends BaseAdminController<DasTeam, TeamVO> {
    // ...
}

// DasExamTeamBindController.java - 多对多关系表的BindController
@RequestMapping(path = {"/web/Exam/Team"})  // ← /web/{Exam}/{Team}
public class DasExamTeamBindController extends BaseBindController<...> {
    // ...
}
```

**重要说明**：
1. 路径中的值保持原样（包括大小写），如`Exam`和`Team`
2. `@AdminPortal`注解标注在**PortalController**上，而不是Entity上
3. 两个关联实体都必须有对应的PortalController且标注了`@AdminPortal`

### BindController提供的API功能

`BaseBindController`自动提供以下RESTful API：

- **绑定操作**：
  - `POST /bind` - 单个绑定
  - `POST /bind/batch` - 批量绑定
  - `POST /bind/all` - 全量绑定
  - `POST /replace` - 替换绑定

- **解绑操作**：
  - `POST /unbind` - 单个解绑
  - `POST /unbind/batch` - 批量解绑
  - `POST /unbind/all` - 全量解绑

- **查询操作**：
  - `GET /bind/list` - 获取已绑定列表
  - `POST /bind/query` - 分页查询已绑定
  - `POST /unbind/query` - 分页查询未绑定

- **信息管理**：
  - `POST /bind/info` - 修改绑定信息
  - `POST /bind/info/list` - 批量修改绑定信息

### 完整示例

以`das_exam_team`表为例，完整的文件生成：

**1. Entity** - `DasExamTeam.java`：使用`@MppMultiId`标注联合主键
**2. Mapper** - `DasExamTeamMapper.java`：标准Mapper接口
**3. Mapper XML** - `DasExamTeamMapper.xml`：所有主键字段使用`<id>`标签
**4. Repository Service** - `DasExamTeamService.java`：业务逻辑服务
**5. Schema Service** - `DasExamTeamSchema.java`：DDL定义
**6. BindController** - `DasExamTeamBindController.java`：绑定关系管理

**所需的前置条件**：
- `DasExam`实体和`ExamPortalController`（标注`@AdminPortal("Exam")`）
- `DasTeam`实体和`DasTeamPortalController`（标注`@AdminPortal("Team")`）
- `ExamVO`和`TeamVO`

### 注意事项

1. **识别多对多表**：通过联合主键和表名命名模式识别
2. **不生成VO**：直接使用两个关联实体的VO
3. **路径规则**：从两个PortalController的`@AdminPortal`注解获取路径部分
4. **注解位置**：`@AdminPortal`标注在Controller上，不是Entity上
5. **类型参数**：`BaseBindController<ENTITY1, BIND, ENTITY2, ENTITY1_VO, ENTITY2_VO>`
   - ENTITY1：第一个关联实体
   - BIND：关联表实体
   - ENTITY2：第二个关联实体
   - ENTITY1_VO：第一个实体的VO
   - ENTITY2_VO：第二个实体的VO
