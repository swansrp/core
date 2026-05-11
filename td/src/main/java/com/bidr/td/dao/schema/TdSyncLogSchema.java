package com.bidr.td.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.td.dao.entity.TdSyncLog;
import org.springframework.stereotype.Service;

@Service
public class TdSyncLogSchema extends BaseMybatisSchema<TdSyncLog> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `td_sync_log` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `biz_id` varchar(50) NOT NULL COMMENT '业务ID',\n" +
                "  `stable_name` varchar(100) DEFAULT NULL COMMENT '超级表名',\n" +
                "  `sub_table_name` varchar(100) DEFAULT NULL COMMENT '子表名',\n" +
                "  `sync_type` varchar(20) NOT NULL COMMENT '同步类型(INSERT/UPDATE/DELETE)',\n" +
                "  `sync_status` int(4) DEFAULT '0' COMMENT '同步状态(0=待同步,1=成功,2=失败)',\n" +
                "  `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误信息',\n" +
                "  `retry_count` int(4) DEFAULT '0' COMMENT '重试次数',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_biz_id` (`biz_id`) USING BTREE,\n" +
                "  KEY `idx_sync_status` (`sync_status`) USING BTREE\n" +
                ") COMMENT='TDengine 数据同步日志表';");
    }
}
