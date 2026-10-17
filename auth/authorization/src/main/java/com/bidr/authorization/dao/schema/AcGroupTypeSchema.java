package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcGroupTypeSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcGroupTypeSchema extends BaseMybatisSchema<AcGroupType> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_group_type` (\n" +
                "  `id` varchar(50) NOT NULL DEFAULT '' COMMENT '用户组类别id',\n" +
                "  `name` varchar(50) NOT NULL COMMENT '用户组类别名称',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") COMMENT='组类型';");
    }
}
