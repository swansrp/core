package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormWideTableSyncLog;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 宽表同步日志 Schema Service
 *
 * @author sharp
 */
@Service
public class FormWideTableSyncLogSchema extends BaseMybatisSchema<FormWideTableSyncLog> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_wide_table_sync_log` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',\n" +
                "  `config_id` bigint(20) NOT NULL COMMENT '关联宽表配置 ID',\n" +
                "  `history_id` varchar(50) NOT NULL COMMENT '填报历史 ID',\n" +
                "  `status` varchar(20) NOT NULL COMMENT '同步状态: success/fail',\n" +
                "  `error_msg` text COMMENT '错误信息',\n" +
                "  `synced_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '同步时间',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  UNIQUE KEY `uk_config_history` (`config_id`, `history_id`) USING BTREE\n" +
                ") COMMENT='宽表同步日志';");
    }
}
