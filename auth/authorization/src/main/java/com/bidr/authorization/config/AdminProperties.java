package com.bidr.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Title: AdminProperties
 * Description: identity.admin.{username,initial-password}——平台超管账号种子（AdminUserSeeder 消费）。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>username 有值 ＝ 按该账号找 ac_user，缺则建（user_type='00'，绝不绑 ac_user_tenant）；</li>
 *   <li>username 空白 ＝ 操作 Schema 种子行（user_id=1，AcUserSchema.setInitData 清库后必在）；</li>
 *   <li>initial-password 有值 ＝ pristine（工厂态 hash/本 seeder 新建）时以它落库；</li>
 *   <li>initial-password 空白 ＝ pristine 时随机 16 位落库并 log.warn 打印一次明文（仅此一次）。</li>
 * </ul>
 * <p>🔴 密码策略只作用于 pristine 态：密码已被人工改过（hash ≠ 工厂态常量）一律不覆盖。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.admin")
public class AdminProperties {

    /** 平台超管账号（ac_user.user_name 口径）；空白＝操作 Schema 种子行 user_id=1 */
    private String username = "";

    /** 初始密码明文（仅 pristine 态生效；空白＝随机生成＋log.warn 打印一次） */
    private String initialPassword = "";
}
