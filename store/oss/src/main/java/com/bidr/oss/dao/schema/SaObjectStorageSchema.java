package com.bidr.oss.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.oss.dao.entity.SaObjectStorage;
import org.springframework.stereotype.Service;

/**
 * 对象存储记录 Schema
 *
 * @author sharp
 */
@Service
public class SaObjectStorageSchema extends BaseMybatisSchema<SaObjectStorage> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_object_storage` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `key` varchar(500) NOT NULL,\n" +
                "  `name` varchar(100) NOT NULL COMMENT '文件名',\n" +
                "  `uri` varchar(500) NOT NULL COMMENT '地址',\n" +
                "  `size` bigint(20) NOT NULL DEFAULT '0' COMMENT '文件大小',\n" +
                "  `type` varchar(10) NOT NULL COMMENT '文件存储类型',\n" +
                "  `create_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` varchar(1) NOT NULL DEFAULT '0' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uri` (`uri`)\n" +
                ") COMMENT='对象存储记录';");
    }
}
