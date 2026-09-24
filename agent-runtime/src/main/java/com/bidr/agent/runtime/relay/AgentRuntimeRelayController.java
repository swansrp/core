package com.bidr.agent.runtime.relay;

import com.bidr.agent.runtime.dto.AgentChatSendReq;
import com.bidr.llm.agent.runtime.dto.AgentInfo;
import com.bidr.agent.runtime.dto.AgentSessionCreateReq;
import com.bidr.llm.agent.runtime.dto.CancelResult;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.dto.TurnPage;
import com.bidr.llm.agent.runtime.dto.UploadUrlResult;
import com.bidr.agent.runtime.dao.entity.ChatSession;
import com.bidr.agent.runtime.service.AgentChatSessionService;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Title: AgentRuntimeRelayController
 * Description: 浏览器 ↔ epc-ai 的 relay 面（契约 B 节）：路径与 agent-system 同形（`/agents`、`/sessions`、
 * `…/messages:stream`、`…/{mid}/stream`），**上游密钥不出后端**。
 * <p>
 * 本控制器**不感知上游是 agent-system 还是 OpenHands**：REST 走 {@link AgentRuntimeProvider}，
 * 流走"provider 同步建链 → {@link AgentStreamRelay#attach} 泵帧"，两侧统一吐 §5.7 形状帧。
 * <p>
 * 三处刻意约定（见契约 B2）：
 * <ul>
 * <li>REST 出参按框架惯例驼峰；帧内字段保持平台原样（snake_case）；</li>
 * <li>{@code GET /sessions} 返回**本人**清单（上游按 Key 可见整租户，不能当用户清单用）；</li>
 * <li>幂等键走请求体 {@code clientKey}，由 provider 转上游头（前端无需自定义头）。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/22
 */
@Api(tags = "总包AI助手 - Agent平台中转")
@RestController
@RequestMapping("/web/agent/rt")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "my.agent.runtime", name = "enabled", havingValue = "true")
public class AgentRuntimeRelayController {

    private final AgentRuntimeProvider provider;

    /** 代收转推单文件上限（与平台侧"非图片 ≤100MB"区分：本路径经 relay 内存中转，收紧到 20MB） */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;
    private final AgentChatSessionService sessionService;
    private final AgentStreamRelay streamRelay;

    @ApiOperation("可用 Agent（平台发现）")
    @GetMapping("/agents")
    public List<AgentInfo> agents() {
        return provider.listAgents();
    }

    @ApiOperation("建会话（上游建 + 本地登记；归属 = 当前登录人）")
    @PostMapping("/sessions")
    public SessionInfo createSession(@Validated @RequestBody AgentSessionCreateReq req) {
        return sessionService.create(req.getAgentCode(), req.getTitle(), req.getGroupKey(),
                req.getBusinessType(), req.getBusinessId());
    }

    @ApiOperation("我的会话清单（本地归属，可按业务绑定过滤）")
    @GetMapping("/sessions")
    public List<ChatSession> mySessions(@RequestParam(required = false) String businessType,
                                        @RequestParam(required = false) String businessId) {
        return sessionService.listMine(businessType, businessId);
    }

    @ApiOperation("会话详情（恢复路径：本地归属校验 + 上游校验）")
    @GetMapping("/sessions/{sessionId}")
    public SessionInfo sessionDetail(@PathVariable String sessionId) {
        return sessionService.detail(sessionId);
    }

    @ApiOperation("软删会话（上游软删 + 本地置无效）")
    @DeleteMapping("/sessions/{sessionId}")
    public DeleteResult deleteSession(@PathVariable String sessionId) {
        return sessionService.delete(sessionId);
    }

    @ApiOperation("历史轮次（归属校验后由 provider 归一）")
    @GetMapping("/sessions/{sessionId}/messages")
    public TurnPage messages(@PathVariable String sessionId,
                             @RequestParam(required = false) String before,
                             @RequestParam(required = false) Integer limit) {
        sessionService.requireOwned(sessionId);
        return provider.history(sessionId, before, limit);
    }

    @ApiOperation("单轮状态/结果")
    @GetMapping("/sessions/{sessionId}/messages/{messageId}")
    public TurnItem message(@PathVariable String sessionId, @PathVariable String messageId) {
        sessionService.requireOwned(sessionId);
        return provider.turn(sessionId, messageId);
    }

    @ApiOperation("取消轮次（排队中/执行中均可）")
    @PostMapping("/sessions/{sessionId}/messages/{messageId}/cancel")
    public CancelResult cancel(@PathVariable String sessionId, @PathVariable String messageId) {
        sessionService.requireOwned(sessionId);
        return provider.cancel(sessionId, messageId);
    }

    @ApiOperation("发消息 + 建流（SSE 单轮流）")
    @PostMapping(value = "/sessions/{sessionId}/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendStream(@PathVariable String sessionId, @Validated @RequestBody AgentChatSendReq req) {
        ChatSession session = sessionService.requireOwned(sessionId);
        sessionService.touch(sessionId);
        TurnOpenCmd cmd = new TurnOpenCmd();
        cmd.setSessionId(sessionId);
        cmd.setAgentCode(session.getAgentCode());
        cmd.setContent(req.getContent());
        cmd.setFiles(req.getFiles());
        cmd.setIdempotencyKey(StringUtils.hasText(req.getClientKey()) ? req.getClientKey() : UUID.randomUUID().toString());
        // 同步建链：开流前错误在此抛出 → 框架回 JSON 信封（前端按 content-type 走错误分支）
        return streamRelay.attach(provider.openTurn(cmd));
    }

    @ApiOperation("挂流恢复（快照 + 续传）")
    @GetMapping(value = "/sessions/{sessionId}/messages/{messageId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(@PathVariable String sessionId, @PathVariable String messageId) {
        sessionService.requireOwned(sessionId);
        return streamRelay.attach(provider.attachTurn(sessionId, messageId));
    }

    @ApiOperation("领附件直传凭据（能力位：不支持的实现会明确报错）")
    @PostMapping("/files/upload-url")
    public UploadUrlResult uploadUrl(@RequestBody Map<String, Object> req) {
        String fileName = String.valueOf(req.getOrDefault("fileName", req.getOrDefault("file_name", "file")));
        long fileSize = Long.parseLong(String.valueOf(req.getOrDefault("fileSize", req.getOrDefault("file_size", "0"))));
        String mimeType = String.valueOf(req.getOrDefault("mimeType", req.getOrDefault("mime_type", "")));
        return provider.createUploadUrl(fileName, fileSize, mimeType);
    }

    /**
     * 附件代收转推（能力位分支二）：上游无预签名位时（如 OpenHands 只有 multipart 文件 API），
     * 浏览器把文件交给 relay，relay 用服务端凭据写进该会话沙箱工作目录，返回沙箱内绝对路径供正文引用。
     * 🔴 归属强校验同其他端点；文件名清洗与大小上限在 provider/本方法内做，绝不让浏览器决定落盘路径。
     */
    @ApiOperation("附件代收转推（写入会话沙箱目录）")
    @PostMapping("/sessions/{sessionId}/files")
    public Map<String, Object> uploadFile(@PathVariable String sessionId,
                                          @RequestParam("file") MultipartFile file) {
        sessionService.requireOwned(sessionId);
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrCodeSys.SYS_VALIDATE_NOT_PASS, "文件内容为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ServiceException(ErrCodeSys.SYS_VALIDATE_NOT_PASS,
                    "文件超过上限 " + (MAX_UPLOAD_BYTES / (1024 * 1024)) + "MB");
        }
        if (!provider.supportsRelayUpload()) {
            throw new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST, "附件代收转推");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException(ErrCodeSys.SYS_VALIDATE_NOT_PASS, "文件读取失败");
        }
        String path = provider.uploadFile(sessionId, file.getOriginalFilename(), bytes);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("path", path);
        res.put("name", file.getOriginalFilename());
        res.put("size", file.getSize());
        return res;
    }
}