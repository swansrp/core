package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * SSO回调地址白名单 Schema
 * <p>🔴 索引＝普通 KEY（非唯一键）：valid 是软删位，唯一键会让"撤销后再登记同一地址"撞键——已定案。</p>
 * <p>🔴 表级不写 CHARSET/COLLATE 子句（项目纪律），但 `redirect_uri` 必须带**列级**
 * {@code COLLATE utf8mb4_bin}：这一列的判定是"全串等值"的承重件——跟随库默认（通常 *_ci）时
 * MySQL 的 {@code =} 会把大小写变体判成相等，而出票口回填的是**请求原串**（{@code SsoTicketService}），
 * 于是"只登记过 /sso/cb"能放票给 /SSO/CB 这类从未登记的路径。列级 bin 的 core 正先例＝
 * {@code AcPartnerHistorySchema} 的 app_key/remote_ip（同为安全判定用列）。
 * 🔴 本表若已在某库存在，{@code CREATE TABLE IF NOT EXISTS} 是 no-op ⇒ 列级 collation 不会追溯生效，
 * 生效与否只认 {@code information_schema.COLUMNS}（启动建表腿核）。</p>
 *
 * @author sharp
 */
@Service
public class AcSsoRedirectSchema extends BaseMybatisSchema<AcSsoRedirect> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_sso_redirect` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `partner_id` bigint NOT NULL COMMENT '归属partner（ac_partner 口径 id）',\n" +
                "  `redirect_uri` varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT '回调地址（命中判定＝入参全串等值，列级 bin 排序规则是判据承重件）',\n" +
                "  `remark` varchar(200) DEFAULT NULL COMMENT '登记备注（用途/环境说明）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性（软删位：撤销登记＝置0，再登记＝插新行）',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_partner_uri` (`partner_id`, `redirect_uri`)\n" +
                ") COMMENT='SSO回调地址白名单';");
    }
}
