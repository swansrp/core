package com.bidr.forge.datasource.dao.schema;

import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SysDataSourceSchema
 * Description: 数据源配置表建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class SysDataSourceSchema extends BaseMybatisSchema<SysDataSource> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_data_source` (\n" +
                "  `ds_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '数据源主键',\n" +
                "  `ds_name` varchar(100) NOT NULL DEFAULT '' COMMENT '数据源名称',\n" +
                "  `ds_type` varchar(20) NOT NULL DEFAULT 'mysql' COMMENT '数据库类型（目前仅支持 mysql 语法系）',\n" +
                "  `jdbc_url` varchar(500) NOT NULL DEFAULT '' COMMENT 'JDBC 连接地址',\n" +
                "  `username` varchar(100) DEFAULT '' COMMENT '用户名',\n" +
                "  `password` varchar(200) DEFAULT '' COMMENT '密码',\n" +
                "  `is_default` char(1) DEFAULT '0' COMMENT '是否默认数据源（1=是 0=否）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  PRIMARY KEY (`ds_id`),\n" +
                "  UNIQUE KEY `ds_name` (`ds_name`)\n" +
                ") COMMENT='数据源配置表';");
    }
}
