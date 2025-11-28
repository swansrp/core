package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcUserRoleSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcUserRoleSchema extends BaseMybatisSchema<AcUserRole> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user_role` (\n" +
                "  `user_id` bigint(20) NOT NULL COMMENT '用户ID',\n" +
                "  `role_id` bigint(20) NOT NULL COMMENT '角色ID',\n" +
                "  PRIMARY KEY (`user_id`,`role_id`)\n" +
                ") COMMENT='用户和角色关联表';");

        setInitData("INSERT INTO `ac_user_role` (`user_id`, `role_id`) VALUES\n\t(1, 1);");
    }
}
