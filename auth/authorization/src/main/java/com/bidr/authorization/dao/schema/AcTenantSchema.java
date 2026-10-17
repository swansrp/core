package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: AcTenantSchema
 * Description: 租户目录表 Schema。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）；code 索引＝普通 KEY 非唯一：
 * valid 是软删位，唯一键会让"停用后再启用同 code"撞键——同 AcSsoRedirect 定案。</p>
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Service
public class AcTenantSchema extends BaseMybatisSchema<AcTenant> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_tenant` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '租户名称',\n" +
                "  `code` varchar(50) DEFAULT NULL COMMENT '业务编码（租户对外可读标识）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性（软删位：停用＝置0，再启用＝插新行）',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_code` (`code`)\n" +
                ") COMMENT='租户目录表';");
    }
}
