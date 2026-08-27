package com.bidr.insight.chatbi.controller;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.insight.chatbi.service.ChatBiConversationService;
import com.bidr.insight.chatbi.vo.ChatBiConversation;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: ChatBiConversationController
 * Description: 智能问数历史对话端点——列表/详情/删除（Redis 按访问人隔离保留，天数见系统参数），
 * 自 ChatBiController 按职责拆分，URL 前缀共用不变（前端零改动）。
 * 评价动作不落本控制器：经 llm 通用评价端点 + ChatBiRatingListener 钩子双写对话正文。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Api(tags = "智能问数 - 历史对话")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiConversationController {

    private final ChatBiConversationService chatBiConversationService;

    @ApiOperation(value = "历史对话列表（Redis 按访问人保留，天数见系统参数；新→旧，列表不带消息明细）")
    @RequestMapping(value = "/conversation/list", method = RequestMethod.GET)
    public List<ChatBiConversation> listConversations() {
        return chatBiConversationService.listConversations(AccountContext.getDisplayName());
    }

    @ApiOperation(value = "对话详情（含全部消息与 chart-spec，前端恢复渲染用）")
    @RequestMapping(value = "/conversation/detail", method = RequestMethod.GET)
    public ChatBiConversation getConversationDetail(String conversationId) {
        Validator.assertNotNull(conversationId, ErrCodeSys.PA_PARAM_NULL, "对话标识");
        return chatBiConversationService.getConversation(conversationId);
    }

    @ApiOperation(value = "删除历史对话（仅能删自己的）")
    @RequestMapping(value = "/conversation/delete", method = RequestMethod.POST)
    public void deleteConversation(String conversationId) {
        Validator.assertNotNull(conversationId, ErrCodeSys.PA_PARAM_NULL, "对话标识");
        chatBiConversationService.deleteConversation(conversationId, AccountContext.getDisplayName());
    }
}
