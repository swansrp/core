package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SysPortalColumnSchema
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class SysPortalColumnSchema extends BaseMybatisSchema<SysPortalColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_column` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `role_id` bigint(20) NOT NULL COMMENT '对应角色id',\n" +
                "  `portal_id` bigint(20) NOT NULL COMMENT '表id',\n" +
                "  `property` varchar(50) NOT NULL DEFAULT '' COMMENT '属性名',\n" +
                "  `db_field` varchar(50) NOT NULL DEFAULT '' COMMENT '数据字段名',\n" +
                "  `display_name` varchar(50) NOT NULL DEFAULT '' COMMENT '显示名称',\n" +
                "  `field_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '属性类型PORTAL_FIELD_DICT',\n" +
                "  `reference` varchar(200) DEFAULT NULL COMMENT '字典或者跳转地址',\n" +
                "  `entity_field` varchar(50) DEFAULT NULL COMMENT '相关实体字段',\n" +
                "  `entity_condition` json DEFAULT NULL COMMENT '查询实体关系',\n" +
                "  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',\n" +
                "  `align` varchar(20) DEFAULT 'center' COMMENT '对齐方式',\n" +
                "  `width` int(11) NOT NULL DEFAULT '140' COMMENT '宽度',\n" +
                "  `fixed` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否固定',\n" +
                "  `tooltip` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否显示tooltip',\n" +
                "  `enable` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否有效',\n" +
                "  `show` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否显示',\n" +
                "  `filter_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做筛选项',\n" +
                "  `sort_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做排序项',\n" +
                "  `summary_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否汇总',\n" +
                "  `edit_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格是否可以编辑',\n" +
                "  `display_group_name` varchar(50) DEFAULT NULL COMMENT '字段分组名称',\n" +
                "  `detail_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '详情时是否显示',\n" +
                "  `detail_size` int(11) NOT NULL DEFAULT '1' COMMENT '详情弹框布局大小',\n" +
                "  `detail_padding` int(11) NOT NULL DEFAULT '0' COMMENT '详情布局显示后占位填充',\n" +
                "  `add_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '添加时是否显示',\n" +
                "  `add_size` int(11) NOT NULL DEFAULT '1' COMMENT '新增弹框布局大小',\n" +
                "  `add_padding` int(11) NOT NULL DEFAULT '0' COMMENT '新增弹框布局显示后占位填充',\n" +
                "  `add_disabled` varchar(50) NOT NULL DEFAULT '0' COMMENT '新增弹框disable显示',\n" +
                "  `edit_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '编辑框是否显示',\n" +
                "  `edit_size` int(11) NOT NULL DEFAULT '1' COMMENT '编辑弹框布局大小',\n" +
                "  `edit_padding` int(11) NOT NULL DEFAULT '0' COMMENT '编辑弹框布局显示后占位填充',\n" +
                "  `edit_disabled` varchar(50) NOT NULL DEFAULT '0' COMMENT '编辑弹框布局disable显示',\n" +
                "  `required` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否必填',\n" +
                "  `min` decimal(20,6) DEFAULT NULL COMMENT '最小值(长度)',\n" +
                "  `max` decimal(20,6) DEFAULT NULL COMMENT '最大值(长度)',\n" +
                "  `default_value` varchar(200) DEFAULT NULL COMMENT '默认内容',\n" +
                "  `mobile_display_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '移动端显示类型',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `role_id_portal_id_property` (`role_id`,`portal_id`,`property`),\n" +
                "  KEY `role_id` (`role_id`),\n" +
                "  KEY `portal_id` (`portal_id`)\n" +
                ") COMMENT='系统表表头';");

        setUpgradeDDL(1, "ALTER TABLE `sys_portal_column` MODIFY COLUMN `reference` varchar(200) DEFAULT NULL COMMENT '字典或者跳转地址';");
        // 注：display_name 固定 varchar(50)，不因为“启动同步写不进长文案”而放宽。
        //     表头显示名由 PortalConfigService.normalizeDisplayName 收敛到 50 字（超宽只取注解文案主标题，
        //     如“业务类型(字典XXX:一长串枚举)”只入库“业务类型”），枚举清单属于接口文档不属于表头。

        // 六个基础表格（AcUser/AcDept/SaObjectStorage/SysConfig/SysDict/SysDictType）的列配置不再写 initData：
        // PortalConfigService 启动扫描 @AdminPortal 时按 VO 注解＋字段声明顺序产出 sys_portal_column
        // （显隐/禁用/字典引用/弹框尺寸/分组＝注解，列序＝声明序）。
        // 为什么摘：原 69 行是一次性 UI 配置导出——扫描对已存在列"只增不更新"，initData 写入后注解规则演进
        //   它永不跟进，和 sys_portal.url 漂移事故同族；正源只留 VO 注解一处（2026-10-17）。
        // display_order 随之＝VO 声明序，不再用手写序号；调列序改 VO 声明，勿再回到种子硬编码。
    }
}
