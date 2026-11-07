package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 三方应用对接 Schema
 *
 * @author sharp
 */
@Service
public class AcPartnerSchema extends BaseMybatisSchema<AcPartner> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_partner` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `app_key` varchar(50) NOT NULL COMMENT '应用的唯一标识key',\n" +
                "  `app_secret` varchar(50) NOT NULL COMMENT '应用的密钥',\n" +
                "  `platform` varchar(50) DEFAULT NULL COMMENT '所属平台',\n" +
                "  `remark` varchar(50) DEFAULT NULL COMMENT '备注',\n" +
                "  `status` int NOT NULL DEFAULT '0' COMMENT '有效性',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_app_key` (`app_key`)\n" +
                ") COMMENT='三方应用对接';");

        // v1（主系统开放口·方案B）：app_key 从普通 KEY 收紧为唯一键——开放口按 partner 解析
        // 菜单归属应用依赖「app_key → 恰一行」的语义，普通 KEY 兜不住重复登记。
        // ⚠️ 已接受风险：存量库若已有重复 app_key，本 ALTER 会失败（版本号不推进），
        // 需修数（合并/停用重复行）后重启重试；不 bump 版本正是为了让修数后能自动补跑。
        // 另注：shouldSkip 认 ADD UNIQUE KEY（kernel initializer 幂等分支，I-1）——
        // fresh install（create DDL 已带唯一键）与重跑由该分支跳过并照常推进版本闸。
        setUpgradeDDL(1, "ALTER TABLE `ac_partner` ADD UNIQUE KEY `uk_app_key` (`app_key`);");
    }
}
