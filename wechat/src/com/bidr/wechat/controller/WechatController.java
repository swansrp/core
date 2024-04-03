package com.bidr.wechat.controller;

import com.bidr.wechat.facade.WechatPublicTextFacade;
import com.bidr.wechat.po.platform.msg.ReceiveUserMsg;
import com.bidr.wechat.service.WechatPublicService;
import com.bidr.wechat.service.account.SyncAccountMenuService;
import com.bidr.kernel.config.response.Response;
import com.bidr.kernel.config.response.ResponseHandler;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.common.CommonResVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * Title: WechatController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/1/22 20:43
 */
@Slf4j
@RestController
@RequestMapping(value = "/wechat")
@Api(value = "微信-公众号交互", tags = "微信-公众号交互")
public class WechatController {

    private static final String FACADE_PREFIX = "wechatPublic";
    private static final String FACADE_SUFFIX = "Facade";
    private static final String COMMON_HANDLE_METHOD = "handle";

    @Resource
    private WechatPublicService wechatPublicService;
    @Resource
    private WechatPublicTextFacade wechatPublicTextFacade;
    @Resource
    private SyncAccountMenuService syncAccountMenuService;

    @ApiOperation(value = "微信公众号签名确认", notes = "微信公众号签名确认")
    @RequestMapping(value = "", method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "signature", value = "微信加密签名", required = true),
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "timestamp", value = "时间戳", required = true),
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "nonce", value = "随机数", required = true),
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "echostr", value = "随机字符串", required = true)})
    public String signature(@RequestParam(value = "nonce") String nonce,
                            @RequestParam(value = "timestamp") String timeStamp,
                            @RequestParam(value = "signature") String signature,
                            @RequestParam(value = "echostr") String echoStr) {
        wechatPublicService.signature(signature, timeStamp, nonce);
        return echoStr;
    }

    @ApiOperation(value = "接受微信公众号推送", notes = "接受微信公众号推送")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public String handle(@RequestParam(value = "signature") String signature,
                         @RequestParam(value = "msg_signature") String msgSignature,
                         @RequestParam(value = "openid") String openId,
                         @RequestParam(value = "nonce") String nonce,
                         @RequestParam(value = "timestamp") String timeStamp,
                         @RequestBody String postData) {
        wechatPublicService.signature(signature, timeStamp, nonce);
        ReceiveUserMsg receiveUserMsg = wechatPublicService.deCrypt(msgSignature, timeStamp, nonce, postData,
                ReceiveUserMsg.class);
        log.info(JsonUtil.toJson(receiveUserMsg));
        String msgType =
                Character.toUpperCase(receiveUserMsg.getMsgType().charAt(0)) + receiveUserMsg.getMsgType().substring(1);
        Object facade = BeanUtil.getBean(FACADE_PREFIX + msgType + FACADE_SUFFIX);
        Object res;
        if (facade == null) {
            res = wechatPublicTextFacade.handleUnknownMsgTypeHandle(receiveUserMsg);
        } else {
            String methodName = COMMON_HANDLE_METHOD;
            if (StringUtils.isNotBlank(receiveUserMsg.getEvent())) {
                methodName = StringUtil.firstLowerCamelCase(receiveUserMsg.getEvent().toLowerCase());
            }
            log.info("methodName {}", methodName);
            Method method = ReflectionUtil.getMethod(facade.getClass(), methodName, ReceiveUserMsg.class);
            if (method == null) {
                res = wechatPublicTextFacade.handleUnknownMsgTypeHandle(receiveUserMsg);
            } else {
                log.info("invoke {}-{}", facade.getClass().getSimpleName(), method.getName());
                res = ReflectionUtil.invoke(facade, method, receiveUserMsg);
            }

        }
        String replyMsg = JsonUtil.readJson(res, String.class);
        return wechatPublicService.crypt(replyMsg, timeStamp, nonce);
    }

    @ApiOperation(value = "更新公众号菜单", notes = "更新公众号菜单")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "Long", name = "roleId", value = "角色id", required = true)})
    @RequestMapping(value = "/menu", method = RequestMethod.POST)
    public ResponseEntity<Response<CommonResVO>> createMenu(@RequestParam Long roleId) {
        syncAccountMenuService.syncPermitAndWechatPublicMenu(roleId);
        return ResponseHandler.commonResponse();
    }

}
