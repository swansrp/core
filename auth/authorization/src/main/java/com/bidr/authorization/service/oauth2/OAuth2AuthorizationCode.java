package com.bidr.authorization.service.oauth2;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: OAuth2AuthorizationCode
 * Description: 授权码绑定的上下文（T1.2 棒 6）——签发时从当前登录态固化，存 Redis 的值体
 * （JSON 序列化往返）。🔴 code 本身不入值体：code 是 Redis 键的尾段（键＝
 * {@code identity:idp:authcode:<code>}），值体只带绑定面，消费时按键一次读取＋立即删除。
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Data
public class OAuth2AuthorizationCode {

    /** 绑定的接入方应用（ac_partner.app_key ＝ OAuth2 client_id） */
    private String clientId;

    /** 签发时校验通过的回调地址（消费时必须同串回传，RFC 6749 §4.1.3） */
    private String redirectUri;

    /** 签发时登录用户的 ac_user.user_id */
    private Long userId;

    /** 签发时登录用户工号（token sub claim 的来源） */
    private String customerNumber;

    /** 登录名（token username claim 的来源） */
    private String userName;

    /** 展示名（token name claim 的来源） */
    private String name;

    /** 绑定租户 id 集合（token tenant_ids claim 的来源；无归属＝空列表） */
    private List<Long> tenantIds = new ArrayList<>(0);

    /** authorize 入参 scope（本棒只透传登记，不做粒度闸；demo 无 scope 要求） */
    private String scope;
}
