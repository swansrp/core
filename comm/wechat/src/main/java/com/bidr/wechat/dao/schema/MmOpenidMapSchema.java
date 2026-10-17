package com.bidr.wechat.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.wechat.dao.entity.MmOpenidMap;
import org.springframework.stereotype.Service;

/**
 * 微信公众号账号对应关系 Schema
 *
 * @author sharp
 */
@Service
public class MmOpenidMapSchema extends BaseMybatisSchema<MmOpenidMap> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `mm_openid_map` (\n" +
                "  `open_id` varchar(50) NOT NULL COMMENT 'openId',\n" +
                "  `union_id` varchar(50) NOT NULL COMMENT '开放平台id',\n" +
                "  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',\n" +
                "  `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',\n" +
                "  `avatar` varchar(200) DEFAULT NULL COMMENT '头像地址',\n" +
                "  PRIMARY KEY (`open_id`,`union_id`)\n" +
                ") COMMENT='微信公众号账号对应关系';");
    }
}
