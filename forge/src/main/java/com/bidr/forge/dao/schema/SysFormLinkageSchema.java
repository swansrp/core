package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysFormLinkage;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单项联动配置Schema
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysFormLinkageSchema extends BaseMybatisSchema<SysFormLinkage> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_form_linkage` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `form_config_id` bigint(20) NOT NULL COMMENT '表单配置ID',\n" +
                "  `linkage_name` varchar(100) NOT NULL COMMENT '联动名称',\n" +
                "  `trigger_event` varchar(20) NOT NULL DEFAULT 'change' COMMENT '触发事件(change/blur/focus)',\n" +
                "  `condition_script` text COMMENT '条件脚本(JS)',\n" +
                "  `action_script` text NOT NULL COMMENT '执行脚本(JS)',\n" +
                "  `target_fields` varchar(500) DEFAULT NULL COMMENT '目标字段(逗号分隔)',\n" +
                "  `priority` int(11) DEFAULT '0' COMMENT '优先级',\n" +
                "  `is_enabled` char(1) DEFAULT '1' COMMENT '是否启用',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_form_config_id` (`form_config_id`)\n" +
                ") COMMENT='表单项联动配置';");
    }
}
