package com.bidr.platform.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.platform.dao.entity.SysBizDict;
import org.springframework.stereotype.Service;

/**
 * 字典表 Schema Service
 *
 * @author sharp
 */
@Service
public class SysBizDictSchema extends BaseMybatisSchema<SysBizDict> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_biz_dict` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `dict_code` varchar(50) NOT NULL COMMENT '字典编码',\n" +
                "  `dict_name` varchar(50) NOT NULL COMMENT '字典名称',\n" +
                "  `biz_id` varchar(50) DEFAULT NULL COMMENT 'NULL表示系统共用字典',\n" +
                "  `label` varchar(100) NOT NULL COMMENT '字典项显示名称',\n" +
                "  `value` varchar(100) NOT NULL COMMENT '字典项值',\n" +
                "  `description` varchar(255) DEFAULT NULL COMMENT '描述',\n" +
                "  `sort` int(11) DEFAULT '0' COMMENT '排序号',\n" +
                "  `is_default` char(1) DEFAULT '0' COMMENT '是否为默认项',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '是否有效',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_value` (`value`) USING BTREE,\n" +
                "  KEY `idx_dict_enterprise` (`dict_code`,`biz_id`) USING BTREE\n" +
                ") COMMENT='业务字典表';");
    }
}
