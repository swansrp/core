package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 子系统注册表 Schema——字段平移自业务仓旧表 DDL（棒7 将删旧表），仅口径变化见 {@link AcSubsystem} 类注释。
 *
 * <p>🔴 无 upgradeDDL：新表走 CREATE IF NOT EXISTS（MybatisPlusConfig 建表链，M-1），
 * 存量库无此表名，不存在升级腿；旧表不读不迁不 DROP（§8.1 裁定，业务侧删除在棒7）。</p>
 *
 * <p>🔴 {@code uk_partner_id}＝唯一键：一 partner 至多挂一子系统（注册口语义）；
 * MySQL 唯一键对多 NULL 合法 ⇒ 未绑定（partner_id=NULL）的子系统可有多行。
 * {@code app_code} 已是主键天然唯一，不再另建 idx_app_code。</p>
 *
 * @author sharp
 */
@Service
public class AcSubsystemSchema extends BaseMybatisSchema<AcSubsystem> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_subsystem` (\n" +
                "  `app_code`              varchar(64)  NOT NULL COMMENT '应用编码（业务主键，门户入口/功能码前缀都按它）',\n" +
                "  `name`                  varchar(100) NOT NULL COMMENT '子系统显示名',\n" +
                "  `icon`                  varchar(200) DEFAULT NULL COMMENT '图标',\n" +
                "  `order_no`              int(11)      DEFAULT NULL COMMENT '门户排序',\n" +
                "  `entry_type`            varchar(32)  DEFAULT NULL COMMENT '入口形态（iframe/新窗口/同页）',\n" +
                "  `entry_url`             varchar(500) DEFAULT NULL COMMENT '入口地址',\n" +
                "  `partner_id`            bigint       DEFAULT NULL COMMENT '归属partner（ac_partner 口径 id；唯一键＝一 partner 至多挂一子系统，NULL=未绑定）',\n" +
                "  `visible_to_all_tenant` char(1)      DEFAULT '0' COMMENT '是否对所有租户可见：1=公开目录（只读展示，不扩写权）/0=按开通与授权判定',\n" +
                "  `admin_url`             varchar(500) DEFAULT NULL COMMENT '子系统后台地址（管理入口）',\n" +
                "  `status`                varchar(20)  DEFAULT NULL COMMENT '状态（1=启用）',\n" +
                "  `description`           varchar(500) DEFAULT NULL COMMENT '说明',\n" +
                "  `create_by`             varchar(50)  DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at`             datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by`             varchar(50)  DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at`             datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`app_code`),\n" +
                "  UNIQUE KEY `uk_partner_id` (`partner_id`)\n" +
                ") COMMENT='子系统注册表（门户入口清单；租户是否可见另由 tenant_app 开通面判定）';");
    }
}
