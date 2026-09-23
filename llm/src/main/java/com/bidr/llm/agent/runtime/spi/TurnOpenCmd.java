package com.bidr.llm.agent.runtime.spi;

import com.bidr.llm.agent.runtime.dto.FileRef;
import lombok.Data;

import java.util.List;

/**
 * Title: TurnOpenCmd
 * Description: 发起一轮（建轮即流）的命令。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Data
public class TurnOpenCmd {

    /**
     * 会话 id（agent-system 为平台 session_id；OpenHands 为其 conversation_id）
     */
    private String sessionId;

    /**
     * 绑定的 Agent 编码（{@code message.started} 帧要带；由本地会话行给出）
     */
    private String agentCode;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 幂等键（同键同体复用同一轮次；不传由上层生成）
     */
    private String idempotencyKey;

    /**
     * 附件引用（仅上传成功项；可为空）
     */
    private List<FileRef> files;
}