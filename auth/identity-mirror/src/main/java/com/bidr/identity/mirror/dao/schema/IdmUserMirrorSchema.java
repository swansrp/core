package com.bidr.identity.mirror.dao.schema;

import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: IdmUserMirrorSchema
 * Description: 用户身份镜像表 Schema（T2.3d）。形态照 AcUserSchema（@Service＋static setCreateDDL，
 * 建表由 MybatisPlusConfig 统一执行），无种子数据——镜像数据只经快照同步落库，不 setInitData。
 * <p>🔴 表级不写 CHARSET/COLLATE（项目纪律）。{@code id} 不自增（＝上游 user_id）；
 * {@code customer_number} 唯一（@BindUser SPI 解析到本表后的 matchField 落点）；
 * {@code user_name} 唯一（T2.4a：登录名对齐键，NULL 不参与唯一冲突）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmUserMirrorSchema extends BaseMybatisSchema<IdmUserMirror> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `idm_user_mirror` (\n" +
                "  `id` bigint NOT NULL COMMENT '用户id（上游user_id，非自增）',\n" +
                "  `customer_number` varchar(50) NOT NULL COMMENT '用户编码',\n" +
                "  `user_name` varchar(64) DEFAULT NULL COMMENT '登录名',\n" +
                "  `name` varchar(50) DEFAULT NULL COMMENT '用户姓名',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_customer_number` (`customer_number`),\n" +
                "  UNIQUE KEY `uk_user_name` (`user_name`)\n" +
                ") COMMENT='用户身份镜像表';");
    }
}
