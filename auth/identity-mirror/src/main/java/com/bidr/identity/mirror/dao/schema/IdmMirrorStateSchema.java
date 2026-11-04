package com.bidr.identity.mirror.dao.schema;

import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: IdmMirrorStateSchema
 * Description: 镜像同步状态表 Schema（T2.3d）。无种子数据（水位由首次成功同步写入）。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）。{@code state_key} 唯一（upsert 判据键）；
 * 状态行只更新不删，时间列仅 update_at。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmMirrorStateSchema extends BaseMybatisSchema<IdmMirrorState> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `idm_mirror_state` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增id',\n" +
                "  `state_key` varchar(50) NOT NULL COMMENT '状态键',\n" +
                "  `state_value` varchar(200) DEFAULT NULL COMMENT '状态值（watermark=epoch millis）',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_state_key` (`state_key`)\n" +
                ") COMMENT='镜像同步状态表';");
    }
}
