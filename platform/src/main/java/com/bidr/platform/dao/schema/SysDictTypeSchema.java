package com.bidr.platform.dao.schema;

import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 字典类型表 Schema
 */
@Service
public class SysDictTypeSchema extends BaseMybatisSchema<SysDictType> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dict_type` (\n" +
                "  `dict_name` varchar(50) NOT NULL COMMENT '字典类型',\n" +
                "  `dict_title` varchar(50) NOT NULL COMMENT '字典显示名称',\n" +
                "  `read_only` varchar(1) NOT NULL DEFAULT '0' COMMENT '只读',\n" +
                "  `expired` int(11) NOT NULL DEFAULT '1440' COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`dict_name`)\n" +
                ") COMMENT='字典类型表';");
    }
}
