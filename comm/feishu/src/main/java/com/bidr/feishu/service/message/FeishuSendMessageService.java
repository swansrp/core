package com.bidr.feishu.service.message;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: FeishuSendMessageService
 * Description: 飞书消息发送服务（对标 WxSendMessageService / WxSendTextCardService），
 * 支持按 open_id / 账号列表发送文本与卡片消息
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuSendMessageService {

    private static final String MSG_TYPE_TEXT = "text";
    private static final String MSG_TYPE_INTERACTIVE = "interactive";

    @Resource
    private Client feishuClient;

    @Resource
    private AcAccountService acAccountService;

    /**
     * 向多个用户发送文本消息
     *
     * @param openIds 接收人 open_id 列表
     * @param text    文本内容
     */
    public void sendTextMessage(List<String> openIds, String text) {
        Map<String, Object> content = new LinkedHashMap<>(2);
        content.put("text", text);
        sendMessage(openIds, MSG_TYPE_TEXT, JsonUtil.toJson(content));
    }

    /**
     * 向多个用户发送卡片消息
     *
     * @param openIds 接收人 open_id 列表
     * @param title   卡片标题
     * @param content 卡片正文（支持 lark_md 格式）
     */
    public void sendCardMessage(List<String> openIds, String title, String content) {
        sendMessage(openIds, MSG_TYPE_INTERACTIVE, buildCardContent(title, content));
    }

    /**
     * 按账号 id 列表发送文本消息（账号 id 即飞书 open_id，未在基础库中的账号跳过）
     *
     * @param accountIds 账号 id 列表
     * @param text       文本内容
     */
    public void sendTextByAccounts(List<String> accountIds, String text) {
        List<String> openIds = new ArrayList<>();
        if (FuncUtil.isNotEmpty(accountIds)) {
            for (String accountId : accountIds) {
                AcAccount account = acAccountService.selectById(accountId);
                if (FuncUtil.isNotEmpty(account)) {
                    openIds.add(account.getId());
                } else {
                    log.warn("飞书发送消息跳过，账号不存在: {}", accountId);
                }
            }
        }
        sendTextMessage(openIds, text);
    }

    /**
     * 逐个接收人发送消息
     */
    private void sendMessage(List<String> openIds, String msgType, String content) {
        if (FuncUtil.isEmpty(openIds)) {
            log.warn("飞书发送消息跳过，接收人为空");
            return;
        }
        for (String openId : openIds) {
            try {
                CreateMessageReq req = CreateMessageReq.newBuilder()
                        .receiveIdType("open_id")
                        .createMessageReqBody(CreateMessageReqBody.newBuilder()
                                .receiveId(openId)
                                .msgType(msgType)
                                .content(content)
                                .build())
                        .build();
                CreateMessageResp resp = feishuClient.im().v1().message().create(req);
                if (!resp.success()) {
                    log.error("飞书消息发送失败, openId: {}, code: {}, msg: {}", openId, resp.getCode(), resp.getMsg());
                }
            } catch (Exception e) {
                log.error("飞书消息发送异常, openId: {}", openId, e);
            }
        }
    }

    /**
     * 构建飞书消息卡片 JSON（红色模板 + 标题 + 正文）
     */
    private String buildCardContent(String title, String content) {
        Map<String, Object> titleNode = new LinkedHashMap<>(4);
        titleNode.put("tag", "plain_text");
        titleNode.put("content", title);

        Map<String, Object> header = new LinkedHashMap<>(4);
        header.put("title", titleNode);
        header.put("template", "red");

        Map<String, Object> textNode = new LinkedHashMap<>(4);
        textNode.put("tag", "lark_md");
        textNode.put("content", content);

        Map<String, Object> element = new LinkedHashMap<>(4);
        element.put("tag", "div");
        element.put("text", textNode);

        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(element);

        Map<String, Object> card = new LinkedHashMap<>(4);
        card.put("header", header);
        card.put("elements", elements);
        return JsonUtil.toJson(card);
    }
}
