package com.bidr.td.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.td.dao.entity.TdTagMapping;
import org.springframework.stereotype.Service;

@Service
public class TdTagMappingSchema extends BaseMybatisSchema<TdTagMapping> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `td_tag_mapping` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `sub_table_name` varchar(100) NOT NULL COMMENT 'TDengine 子表名',\n" +
                "  `biz_id` varchar(50) NOT NULL COMMENT '业务ID',\n" +
                "  `stable_name` varchar(100) DEFAULT NULL COMMENT '超级表名',\n" +
                "  `tag_json` varchar(2000) DEFAULT NULL COMMENT 'Tag JSON 内容',\n" +
                "  `description` varchar(255) DEFAULT NULL COMMENT '描述',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '是否有效',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_biz_id` (`biz_id`) USING BTREE,\n" +
                "  KEY `idx_sub_table_name` (`sub_table_name`) USING BTREE\n" +
                ") COMMENT='TDengine 子表与业务设备映射关系表';");
    }
}
