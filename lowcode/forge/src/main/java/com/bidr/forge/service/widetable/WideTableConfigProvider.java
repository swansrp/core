package com.bidr.forge.service.widetable;

import java.util.List;

/**
 * 宽表配置提供者接口
 * <p>
 * 由业务层实现，向框架层提供宽表的固定业务列定义。
 * 框架层根据这些定义生成 DDL、Portal 列配置和数据收集 INSERT 语句，
 * 从而实现框架与业务解耦。
 * <p>
 * 使用方式：业务模块实现此接口，通过 {@code WideTableContextInjection}
 * 或直接 Spring 注入到 {@link FormWideTableManager} 中。
 *
 * @author sharp
 */
public interface WideTableConfigProvider {

    /**
     * 获取宽表的固定业务列定义
     * <p>
     * 固定列是除动态表单字段外的业务维度字段，如企业名称、产品名称、审批状态等。
     * 每个列定义包含物理列名、类型、显示名、字典关联和上下文取值键。
     *
     * @return 固定列定义列表
     */
    List<WideTableFixedColumn> getFixedColumns();
}
