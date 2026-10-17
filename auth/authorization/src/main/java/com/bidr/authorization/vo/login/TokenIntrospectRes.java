package com.bidr.authorization.vo.login;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: TokenIntrospectRes
 * Description: Token 回查（introspection）响应（M15 棒 0 纯加法）。
 * <p>三态判定人人可查（valid/tokenType）；身份明细（customerNumber/userId/name/expireSeconds）
 * 仅当调用方自身携带有效登录态时填充，否则留空。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
@Data
@ApiModel("Token回查结果")
public class TokenIntrospectRes {

    @ApiModelProperty(value = "被查询token是否有效（存在于服务端会话且归属校验通过；游客态/已注销视为无效）")
    private boolean valid;

    @ApiModelProperty(value = "被查询token类型（TokenType枚举名），可解析即给出")
    private String tokenType;

    @ApiModelProperty(value = "被查询token归属的用户编号；仅调用方自身带有效登录态时返回，否则为空")
    private String customerNumber;

    @ApiModelProperty(value = "归属用户在ac_user中的主键id；仅调用方自身带有效登录态且能查到时返回，否则为空")
    private Long userId;

    @ApiModelProperty(value = "归属用户姓名；仅调用方自身带有效登录态且能查到时返回，否则为空")
    private String name;

    @ApiModelProperty(value = "剩余有效秒数（由会话时间戳与超时秒数推算）；仅调用方自身带有效登录态时返回，无法计算留空")
    private Long expireSeconds;

    @ApiModelProperty(value = "归属用户的租户集（ac_user_tenant 现查后烤进会话的 TENANT_IDS）；"
            + "仅调用方自身带有效登录态时返回——子系统据此把账号连同租户落成本地记录；"
            + "null＝会话缺该 claim（老 token，旁路形态），[]＝新 token 且用户无任何租户归属")
    private java.util.List<Long> tenantIds;
}
