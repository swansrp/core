package com.bidr.insight.chatbi.controller;

import com.bidr.insight.chatbi.service.ChatBiService;
import com.bidr.insight.chatbi.vo.ChatBiAskReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Title: ChatBiController
 * Description: 智能问数执行入口——SSE 流式问数（走默认 AuthLogin，SSO 登录访问）。
 * 语义目录/敏感配置/看板路由/历史对话已按职责拆至同包各控制器，URL 前缀共用不变。
 * <p>
 * 事件协议见 {@link com.bidr.llm.sse.SseEventSender}：
 * delta（token 增量）/ spec（chart-spec 编排指令）/ done（剔除代码块后的正文）/ error。
 * 评价助手回复与运营统计已上提 llm 通用端点（/web/api/agent/rating/save 与 /rating/stat），
 * 对话正文双写经 ChatBiRatingListener 钩子承接，本包不再提供评价端点。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Api(tags = "智能问数")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiController {

    private final ChatBiService chatBiService;

    /**
     * 流式问数：模板基类（{@link ChatBiService} 继承 AbstractFlowAskAgent）建连并执行，
     * SseEmitter 不走 JSON 消息转换器，全局响应包装不影响本端点
     */
    @ApiOperation(value = "智能问数（SSE 流式）")
    @RequestMapping(value = "/ask", method = RequestMethod.POST, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Validated @RequestBody ChatBiAskReq req) {
        return chatBiService.ask(req);
    }
}
