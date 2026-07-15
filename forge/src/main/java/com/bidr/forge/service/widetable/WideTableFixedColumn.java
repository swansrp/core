package com.bidr.forge.service.widetable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 宽表固定列定义（由业务层通过 WideTableConfigProvider 提供）
 * <p>
 * 描述宽表中除动态字段外的业务固定列，如企业名称、产品名称、审批状态等。
 * 框架层根据此定义生成 DDL、Portal 列配置和 INSERT 语句。
 *
 * @author sharp
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WideTableFixedColumn {

    /**
     * 物理列名（如 enterprise_name）
     */
    private String columnName;

    /**
     * MySQL 列类型（如 varchar(200)、datetime）
     */
    private String columnType;

    /**
     * 列显示名（如 企业名称）
     */
    private String columnLabel;

    /**
     * Portal 字段类型（PortalFieldDict 的 value，如 "1"=STRING, "7"=DATETIME, "4"=ENUM）
     */
    private String portalFieldType;

    /**
     * 关联字典 ID（仅 portalFieldType=ENUM 时使用，如 APPROVAL_DICT）
     */
    private String dictId;

    /**
     * 上下文取值键（对应 WideTableBusinessContext 中的字段名，如 enterpriseName）
     */
    private String contextKey;

    /**
     * 列宽（Portal 显示宽度，默认 150）
     */
    private int width = 150;
}
