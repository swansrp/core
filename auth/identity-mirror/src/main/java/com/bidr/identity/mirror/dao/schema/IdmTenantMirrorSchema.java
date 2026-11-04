package com.bidr.identity.mirror.dao.schema;

import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: IdmTenantMirrorSchema
 * Description: 租户身份镜像表 Schema（T2.3d）。无种子数据（镜像只经快照同步落库）。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）。{@code id} 不自增（＝上游租户 id）；
 * {@code tenant_code} 唯一（镜像侧业务键）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmTenantMirrorSchema extends BaseMybatisSchema<IdmTenantMirror> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `idm_tenant_mirror` (\n" +
                "  `id` bigint NOT NULL COMMENT '租户id（上游id，非自增）',\n" +
                "  `tenant_code` varchar(50) NOT NULL COMMENT '租户业务编码',\n" +
                "  `tenant_name` varchar(100) DEFAULT NULL COMMENT '租户名称',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_tenant_code` (`tenant_code`)\n" +
                ") COMMENT='租户身份镜像表';");
    }
}
