package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 通用资源权限表Schema
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Service
public class AcResourcePermSchema extends BaseMybatisSchema<AcResourcePerm> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_resource_perm` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `resource_type` varchar(100) NOT NULL COMMENT '资源类型（表名）',\n" +
                "  `resource_id` varchar(50) NOT NULL COMMENT '资源ID（表主键）',\n" +
                "  `subject_type` int NOT NULL COMMENT '授权主体类型（0=角色 1=用户 2=用户组 3=部门）',\n" +
                "  `subject_id` varchar(50) NOT NULL COMMENT '主体标识（role_id / customer_number / group_id / dept_id）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_resource_subject` (`resource_type`, `resource_id`, `subject_type`, `subject_id`),\n" +
                "  KEY `idx_resource` (`resource_type`, `resource_id`),\n" +
                "  KEY `idx_subject` (`subject_type`, `subject_id`)\n" +
                ") COMMENT='通用资源权限表';");
    }
}
