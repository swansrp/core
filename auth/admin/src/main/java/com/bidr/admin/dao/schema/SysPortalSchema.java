package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SysPortalSchema
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/11/24 20:31
 */
@Service
public class SysPortalSchema extends BaseMybatisSchema<SysPortal> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `role_id` bigint(20) NOT NULL COMMENT '对应角色id',\n" +
                "  `name` varchar(50) NOT NULL COMMENT '英文名',\n" +
                "  `display_name` varchar(50) NOT NULL DEFAULT '' COMMENT '中文名',\n" +
                "  `url` varchar(50) NOT NULL DEFAULT '' COMMENT 'api地址',\n" +
                "  `bean` varchar(200) NOT NULL DEFAULT '' COMMENT '接口bean',\n" +
                "  `size` varchar(50) NOT NULL DEFAULT 'small' COMMENT '表格大小PORTAL_TABLE_SIZE_DICT',\n" +
                "  `read_only` varchar(1) NOT NULL DEFAULT '0' COMMENT '只读',\n" +
                "  `summary` varchar(1) NOT NULL DEFAULT '0' COMMENT '总结栏',\n" +
                "  `advanced` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否支持高级查询',\n" +
                "  `id_column` varchar(50) DEFAULT NULL COMMENT '行id字段名',\n" +
                "  `pid_column` varchar(50) DEFAULT NULL COMMENT '父id字段名',\n" +
                "  `tree_drag` varchar(1) NOT NULL DEFAULT '1' COMMENT '树形结构下是否支持拖拽修改',\n" +
                "  `name_column` varchar(50) DEFAULT NULL COMMENT '名称字段名',\n" +
                "  `order_column` varchar(50) DEFAULT NULL COMMENT '排序字段名',\n" +
                "  `table_drag` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格拖拽改变顺序',\n" +
                "  `add_width` int(11) NOT NULL DEFAULT '60' COMMENT '新增弹框宽度',\n" +
                "  `edit_width` int(11) NOT NULL DEFAULT '60' COMMENT '编辑弹框宽度',\n" +
                "  `detail_width` int(11) NOT NULL DEFAULT '60' COMMENT '详情弹框宽度',\n" +
                "  `description_count` int(11) NOT NULL DEFAULT '2' COMMENT '弹框每行显示个数',\n" +
                "  `export_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '支持导出',\n" +
                "  `import_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '支持导入',\n" +
                "  `default_condition` json DEFAULT NULL COMMENT '默认搜索条件',\n" +
                "  `default_sort` json DEFAULT NULL COMMENT '默认排序字段',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `role_id_name` (`role_id`,`name`),\n" +
                "  KEY `bean` (`bean`),\n" +
                "  KEY `role_id` (`role_id`),\n" +
                "  KEY `name` (`name`)\n" +
                ") COMMENT='后台管理表';\n");

        // 添加数据模式相关字段的升级脚本
        setUpgradeDDL(1, "ALTER TABLE `sys_portal` " +
                "ADD COLUMN `data_mode` VARCHAR(20) DEFAULT NULL COMMENT '数据模式' AFTER `default_sort`, " +
                "ADD COLUMN `reference_id` VARCHAR(50) COMMENT '关联表格ID' AFTER `data_mode`, " +
                "ADD INDEX `idx_portal_datamode` (`data_mode`), " +
                "ADD INDEX `idx_portal_referenceid` (`reference_id`);");


        // 基础 Portal 行（AcUser/AcDept/SaObjectStorage/SysConfig/SysDict/SysDictType）不再由 initData 硬编码：
        // PortalConfigService 启动扫描 @AdminPortal 时自动注册（url 从 @RequestMapping 现读，不会漂移；
        // id_column/name_column/pid_column/import_able 等原种子硬值改由 VO 的 @Portal* 注解声明，
        // 见 UserRes/DepartmentRes/SysConfigRes/SysDict/SysDictType/OssRes）。
        // 原 6 行曾把 id 钉死 1–6 供 SysPortalColumnSchema 的列种子按 portal_id 硬引用；列种子已摘除，
        //   id 不再需要可控（2026-10-17）。SaObjectStorage 行 url 停在 OssController 改挂 /web/oss 之前的
        //   旧值且"行存在即不覆盖"永驻，是 2026-10 对象存储页 404 的根因——正是这类漂移的实证。
    }
}
