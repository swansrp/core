package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcUserDeptSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcUserDeptSchema extends BaseMybatisSchema<AcUserDept> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user_dept` (\n" +
                "  `user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '用户id',\n" +
                "  `dept_id` varchar(50) NOT NULL COMMENT '组织id',\n" +
                "  `data_scope` int(11) DEFAULT NULL COMMENT '数据权限范围',\n" +
                "  PRIMARY KEY (`user_id`,`dept_id`) USING BTREE,\n" +
                "  KEY `dept_id` (`dept_id`),\n" +
                "  KEY `user_id` (`user_id`) USING BTREE\n" +
                ") COMMENT='用户组织结构表';");
    }
}
