package com.bidr.authorization.controller.login;

import com.bidr.authorization.constants.SsoConfigKeys;
import com.bidr.authorization.service.login.SsoTicketService;
import com.bidr.authorization.vo.login.SsoTicketRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * Title: SsoTicketController
 * Description: SSO 出票口（core 底座自带，M15 棒 0b-4 改拍回灌：入参扩为 app_id＋redirect_uri 两个必填）。
 * <p>
 * 独立 bean 名 + {@code POST /web/sso/ticket}，与既有 {@code SsoLoginController} 的 /login、
 * {@code TokenIntrospectController} 的 /introspect 路径不重叠。
 * 🔴 本口<b>刻意不挂免登录豁免注解</b>（对照 {@code SsoLoginController#login} 显式标注的免登录豁免形态）——
 * 出票必须以墨枢自身登录态为前提，票的 username 只从当前会话抽，不接受前端传用户名。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Slf4j
@Api(tags = "系统基础 - SSO票据签发")
@RestController("SsoTicketController")
@RequestMapping(value = "/web/sso")
public class SsoTicketController {

    @Resource
    private SsoTicketService ssoTicketService;

    @ApiOperation(value = "签发SSO票据(id_token)",
            notes = "前提=当前请求带有效登录态（本口未标免登录豁免注解，未登录直接业务拒绝）。"
                    + "入参两个必填：" + SsoConfigKeys.TICKET_PARAM_APP_ID
                    + "（子系统应用标识＝ac_partner.app_key）＋" + SsoConfigKeys.TICKET_PARAM_REDIRECT_URI
                    + "（回调地址全串）。三态语义：1) 已登录且 (" + SsoConfigKeys.TICKET_PARAM_APP_ID + ","
                    + SsoConfigKeys.TICKET_PARAM_REDIRECT_URI + ") 组合已登记白名单（ac_sso_redirect，全串等值"
                    + "∧valid='1'）且出票配置齐全→签发RS256票（claims只username+exp，存活秒数属部署配置）"
                    + "并回填回调地址；2) 业务拒绝（文案即根因、不带状态码数字），两条拒因分开点名："
                    + SsoConfigKeys.TICKET_PARAM_APP_ID + "空白或" + SsoConfigKeys.TICKET_PARAM_REDIRECT_URI
                    + "空白＝参数格式拒；未登录/会话失效＝拒且不透出partner与白名单查询；" + SsoConfigKeys.TICKET_PARAM_APP_ID
                    + " 未登记（不在ac_partner）＝拒；" + SsoConfigKeys.TICKET_PARAM_REDIRECT_URI
                    + " 未登记在该应用名下＝拒（组合判据，地址登记在别家名下同样拒）；私钥或有效期未配置＝拒"
                    + "（core不设兜底默认值）；3) 票供子系统以配置的公钥验签消费，过期或错钥在消费端一律拒。")
    @RequestMapping(value = "/ticket", method = RequestMethod.POST)
    public SsoTicketRes ticket(
            @ApiParam(value = "子系统应用标识（必填，对应 ac_partner.app_key；查不到即拒，不透出白名单查询结果）",
                    required = true, name = SsoConfigKeys.TICKET_PARAM_APP_ID) String app_id,
            @ApiParam(value = "回调地址（必填，全串等值且必须登记在上述应用所属 partner 名下；未登记即拒）",
                    required = true, name = SsoConfigKeys.TICKET_PARAM_REDIRECT_URI) String redirect_uri,
            HttpServletRequest request) {
        return ssoTicketService.issue(app_id, redirect_uri, request);
    }
}
