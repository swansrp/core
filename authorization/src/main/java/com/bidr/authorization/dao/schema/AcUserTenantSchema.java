package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: AcUserTenantSchema
 * Description: 用户-租户成员关系表 Schema（物理删、无 valid 列，形态照 AcUserDeptSchema）。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）。(user_id, tenant_id) 既是主键也显式声明为
 * 唯一键 {@code uk_user_tenant}——后者是承诺面点名的查重判据名（information_schema 可见、
 * 与实体复合主键同列，冗余但语义自证），关系表体量小，写放大可接受。</p>
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Service
public class AcUserTenantSchema extends BaseMybatisSchema<AcUserTenant> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user_tenant` (\n" +
                "  `user_id` bigint(20) NOT NULL COMMENT '用户id',\n" +
                "  `tenant_id` bigint(20) NOT NULL COMMENT '租户id',\n" +
                "  `is_admin` tinyint(1) DEFAULT NULL COMMENT '租户内管理员（关系属性，非全局角色）',\n" +
                "  PRIMARY KEY (`user_id`,`tenant_id`) USING BTREE,\n" +
                "  UNIQUE KEY `uk_user_tenant` (`user_id`,`tenant_id`),\n" +
                "  KEY `tenant_id` (`tenant_id`) USING BTREE\n" +
                ") COMMENT='用户租户关系表';");
    }
}
