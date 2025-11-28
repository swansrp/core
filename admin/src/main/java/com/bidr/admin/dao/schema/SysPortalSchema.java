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

        // 初始化基础Portal数据
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(1, 0, 'AcUser', '系统-用户信息表', 'portal/user', 'adminUserController', 'small', '0', '0', 'userId', '', '1', 'name', NULL, '0', 60, 60, 60, 2, '1', '1');");
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(2, 0, 'AcDept', '系统-部门表', 'portal/department', 'adminDeptController', 'small', '0', '0', 'deptId', 'pid', '1', 'name', 'showOrder', '0', 60, 60, 60, 2, '1', '0');");
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(3, 0, 'SaObjectStorage', '系统-对象存储记录(默认)', 'SaObjectStorage', 'ossController', 'small', '0', '0', NULL, '', '1', NULL, NULL, '0', 60, 60, 60, 2, '1', '0');");
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(4, 0, 'SysConfig', '系统-参数配置表', 'config/admin', 'adminConfigController', 'small', '0', '0', 'configId', '', '1', 'configName', NULL, '0', 60, 60, 60, 2, '1', '0');");
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(5, 0, 'SysDict', '系统-字典数据表', 'dict/item/admin', 'adminDictController', 'small', '0', '0', 'dictId', '', '1', 'dictItem', NULL, '0', 60, 60, 60, 2, '1', '0');");
        setInitData("INSERT INTO `sys_portal` (`id`, `role_id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES " +
                "(6, 0, 'SysDictType', '系统-字典类型表', 'dict/admin', 'adminDictTypeController', 'small', '0', '0', 'dictName', '', '1', 'dictTitle', NULL, '0', 60, 60, 60, 2, '1', '0');");
    }
}