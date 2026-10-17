package com.bidr.wechat.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.wechat.dao.entity.MmRoleTagMap;
import org.springframework.stereotype.Service;

/**
 * 用户标签角色关系 Schema
 *
 * @author sharp
 */
@Service
public class MmRoleTagMapSchema extends BaseMybatisSchema<MmRoleTagMap> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `mm_role_tag_map` (\n" +
                "  `role_id` bigint unsigned NOT NULL COMMENT '角色id',\n" +
                "  `tag_id` int(11) NOT NULL COMMENT '标签id',\n" +
                "  `tag_name` varchar(50) DEFAULT NULL COMMENT '标签名',\n" +
                "  `menu_id` varchar(20) DEFAULT NULL COMMENT '个性菜单id',\n" +
                "  PRIMARY KEY (`role_id`,`tag_id`)\n" +
                ") COMMENT='用户标签角色关系';");
    }
}
