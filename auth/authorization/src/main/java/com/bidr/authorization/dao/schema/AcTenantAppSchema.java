package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 租户级应用开通表 Schema——字段平移自业务仓旧表 tenant_app DDL，口径变化见 {@link AcTenantApp} 类注释。
 *
 * <p>🔴 不设升级 DDL：新表走 CREATE IF NOT EXISTS（MybatisPlusConfig 建表链，M-1），
 * 存量库无此表名，不存在升级腿；旧表 tenant_app 不读不迁不 DROP（业务侧删除在后续棒）。</p>
 *
 * <p>🔴 {@code uk_tenant_app(tenant_id, app_code)}＝逻辑主键：一租户对一应用至多一条开通行
 * （含软删行在内物理唯一，bind 撞键走复活而非新插）；tenant_id/app_code 均为弱引用无 FK
 * （引用完整性在仓储层 {@code AcTenantAppService.validateBindable} 兜底）。</p>
 *
 * @author Sharp
 */
@Service
public class AcTenantAppSchema extends BaseMybatisSchema<AcTenantApp> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_tenant_app`\n" +
                "(\n" +
                "    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `tenant_id`   bigint(20)   NOT NULL COMMENT '租户id（ac_tenant.id）',\n" +
                "    `app_code`    varchar(64)  NOT NULL COMMENT '应用编码（ac_subsystem.app_code）',\n" +
                "    `status`      varchar(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 已开通/DISABLED 已停用',\n" +
                "    `plan_code`   varchar(64)  DEFAULT NULL COMMENT '套餐编码（售卖档位，空=未分级）',\n" +
                "    `start_at`    datetime(3)  DEFAULT NULL COMMENT '生效时间（空=立即生效）',\n" +
                "    `expire_at`   datetime(3)  DEFAULT NULL COMMENT '到期时间（空=长期有效）',\n" +
                "    `config_json` text COMMENT '应用级配置JSON（按 app_code 语义消费）',\n" +
                "    `create_by`   varchar(50)  DEFAULT NULL COMMENT '创建者',\n" +
                "    `create_at`   datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_by`   varchar(50)  DEFAULT NULL COMMENT '更新者',\n" +
                "    `update_at`   datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "    `valid`       char(1)      DEFAULT '1' COMMENT '有效性',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE KEY `uk_tenant_app` (`tenant_id`, `app_code`)\n" +
                ") COMMENT='租户级应用开通（售卖三闸门①：租户买了没→入口是否下发；逻辑主键 UK(tenant_id,app_code)）';");
    }
}
