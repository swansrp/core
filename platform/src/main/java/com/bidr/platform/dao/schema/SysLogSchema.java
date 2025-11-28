package com.bidr.platform.dao.schema;

import com.bidr.platform.dao.entity.SysLog;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 系统日志表 Schema
 */
@Service
public class SysLogSchema extends BaseMybatisSchema<SysLog> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_log` (\n" +
                "  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志id',\n" +
                "  `project_id` varchar(100) DEFAULT NULL COMMENT '项目标识id',\n" +
                "  `module_id` varchar(100) DEFAULT NULL COMMENT '模块id',\n" +
                "  `env_type` varchar(100) DEFAULT NULL COMMENT '环境类型',\n" +
                "  `create_time` datetime(3) DEFAULT NULL COMMENT '日志创建时间',\n" +
                "  `log_seq` bigint(20) DEFAULT NULL COMMENT '日志序列号',\n" +
                "  `log_level` varchar(20) DEFAULT NULL COMMENT '日志级别',\n" +
                "  `request_id` varchar(100) DEFAULT NULL COMMENT '请求id',\n" +
                "  `trace_id` varchar(100) DEFAULT NULL COMMENT 'trace id',\n" +
                "  `request_ip` varchar(100) DEFAULT NULL COMMENT '请求ip',\n" +
                "  `user_ip` varchar(100) DEFAULT NULL COMMENT '用户ip',\n" +
                "  `server_ip` varchar(100) DEFAULT NULL COMMENT '服务器ip',\n" +
                "  `thread_name` varchar(100) DEFAULT NULL COMMENT '线程名',\n" +
                "  `class_name` varchar(200) DEFAULT NULL COMMENT '类名',\n" +
                "  `method_name` varchar(200) DEFAULT NULL COMMENT '方法名',\n" +
                "  `content` longtext COMMENT '内容',\n" +
                "  PRIMARY KEY (`log_id`),\n" +
                "  KEY `module_id` (`module_id`),\n" +
                "  KEY `project_id` (`project_id`),\n" +
                "  KEY `env_type` (`env_type`),\n" +
                "  KEY `request_id` (`request_id`),\n" +
                "  KEY `trace_id` (`trace_id`),\n" +
                "  KEY `request_ip` (`request_ip`)\n" +
                ") COMMENT='系统日志表';");
    }
}
