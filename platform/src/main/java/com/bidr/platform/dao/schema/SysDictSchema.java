package com.bidr.platform.dao.schema;

import com.bidr.platform.dao.entity.SysDict;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 字典数据表 Schema
 */
@Service
public class SysDictSchema extends BaseMybatisSchema<SysDict> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dict` (\n" +
                "  `dict_id` varchar(100) NOT NULL COMMENT '字典编码',\n" +
                "  `dict_pid` varchar(100) DEFAULT NULL COMMENT '字典父节点',\n" +
                "  `dict_sort` int(11) DEFAULT '0' COMMENT '字典排序',\n" +
                "  `dict_name` varchar(100) NOT NULL DEFAULT '' COMMENT '字典类型',\n" +
                "  `dict_title` varchar(100) NOT NULL DEFAULT '' COMMENT '字典显示名称',\n" +
                "  `dict_item` varchar(100) DEFAULT '' COMMENT '字典项名称',\n" +
                "  `dict_value` varchar(100) NOT NULL COMMENT '字典键值',\n" +
                "  `dict_label` varchar(100) NOT NULL DEFAULT '' COMMENT '字典标签',\n" +
                "  `is_default` char(1) DEFAULT '0' COMMENT '是否默认（1是 0否）',\n" +
                "  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',\n" +
                "  `show` char(1) NOT NULL DEFAULT '0' COMMENT '是否显示',\n" +
                "  `read_only` char(1) NOT NULL DEFAULT '0' COMMENT '只读',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  PRIMARY KEY (`dict_id`) USING BTREE,\n" +
                "  UNIQUE KEY `dict_value_dict_name` (`dict_value`,`dict_name`),\n" +
                "  KEY `dict_pid` (`dict_pid`)\n" +
                ") COMMENT='字典数据表';");
    }
}
