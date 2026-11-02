package com.bidr.identity.mirror.dao.schema;

import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: IdmUserTenantMirrorSchema
 * Description: 用户租户关系镜像表 Schema（T2.3d）。无种子数据（镜像只经快照同步落库）。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）。surrogate 自增 {@code id} 主键＋
 * UK(user_id,tenant_id)（同步全量比对的判据键，承诺面裁定）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmUserTenantMirrorSchema extends BaseMybatisSchema<IdmUserTenantMirror> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `idm_user_tenant_mirror` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增id',\n" +
                "  `user_id` bigint NOT NULL COMMENT '用户id',\n" +
                "  `tenant_id` bigint NOT NULL COMMENT '租户id',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_user_tenant` (`user_id`,`tenant_id`)\n" +
                ") COMMENT='用户租户关系镜像表';");
    }
}
