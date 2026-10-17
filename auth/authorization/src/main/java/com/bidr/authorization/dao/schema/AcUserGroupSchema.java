package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcUserGroupSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcUserGroupSchema extends BaseMybatisSchema<AcUserGroup> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user_group` (\n" +
                "  `user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '用户id',\n" +
                "  `group_id` bigint(20) NOT NULL COMMENT '组id',\n" +
                "  `data_scope` int(11) DEFAULT '0' COMMENT '数据权限范围',\n" +
                "  PRIMARY KEY (`user_id`,`group_id`) USING BTREE,\n" +
                "  KEY `group_id` (`group_id`),\n" +
                "  KEY `user_id` (`user_id`) USING BTREE\n" +
                ") COMMENT='用户组群关系';");
    }
}
