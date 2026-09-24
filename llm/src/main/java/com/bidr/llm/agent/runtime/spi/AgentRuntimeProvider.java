package com.bidr.llm.agent.runtime.spi;

import com.bidr.llm.agent.runtime.dto.AgentInfo;
import com.bidr.llm.agent.runtime.dto.CancelResult;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.dto.TurnPage;
import com.bidr.llm.agent.runtime.dto.UploadUrlResult;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;

import java.util.List;

/**
 * Title: AgentRuntimeProvider
 * Description: 上游 Agent 运行时的**可切换 SPI**——同一套 relay 面，底下可接 agent-system（开放协议）
 * 或 OpenHands（agent-server REST + 会话级 WS）。
 * <p>
 * **内部契约只有两样**，两个实现都必须落在它们上面，这是"可切换"成立的前提：
 * <ol>
 * <li>REST 出入参 = 本模块的 DTO（驼峰，见 {@code dto} 包），**不泄漏任何上游字段名</b>；</li>
 * <li>流 = <b>§5.7 形状的帧 JSON</b>（{@link RuntimeTurnLink#nextFrame()}），字段 snake_case 与平台一致，
 * 前端归约器只认这一种形状。</li>
 * </ol>
 * 因此 vendor 实现里"原样透传"只是它内部的优化（上游本就是该形状），而 OpenHands 实现必须做
 * 事件→帧映射与轮次切分——**差异被关在实现里，不往上传**。
 * <p>
 * 实现方必须遵守的不变式（对齐契约文档 A5）：
 * <ul>
 * <li>{@link #openTurn} / {@link #attachTurn} **同步建链**：开流前错误直接抛
 * {@code ServiceException}（框架据此回 JSON 信封，relay 不会拿到 emitter）；</li>
 * <li>断流 ≠ 取消：{@link RuntimeTurnLink#close()} 只停止消费，**不得**取消上游轮次；</li>
 * <li>终局必达：每轮以一个终局帧（done/error/cancelled）收尾；异常断开按断流处理，
 * 由客户端 {@link #attachTurn} 续收；</li>
 * <li>取消可容忍码（40900/40403）与 40402 语义由各实现映射到同一套平台码（见 {@code AgentRuntimeErrors}），
 * 前端只按码分支。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/23
 */
public interface AgentRuntimeProvider {

    /**
     * 实现标识（agent-system / openhands），写入日志与诊断
     */
    String name();

    /**
     * 可用 Agent 清单
     */
    List<AgentInfo> listAgents();

    /**
     * 建会话（业务侧再登记归属）
     */
    SessionInfo createSession(SessionCreateCmd cmd);

    /**
     * 会话校验（恢复路径）：会话不存在/已删须映射为平台码 40402
     */
    SessionInfo validateSession(String sessionId);

    /**
     * 软删会话
     */
    DeleteResult deleteSession(String sessionId);

    /**
     * 历史轮次（默认最新 limit 条，before 为前翻游标）
     */
    TurnPage history(String sessionId, String before, Integer limit);

    /**
     * 单轮状态/结果
     */
    TurnItem turn(String sessionId, String turnId);

    /**
     * 取消轮次（排队中/执行中均可）
     */
    CancelResult cancel(String sessionId, String turnId);

    /**
     * 建轮即流：同步建链并准备消费该轮完整流。
     *
     * @throws com.bidr.kernel.exception.ServiceException 开流前错误（鉴权/会话失效/参数/限流）
     */
    RuntimeTurnLink openTurn(TurnOpenCmd cmd);

    /**
     * 挂流恢复：补齐该轮前缀（快照或重放）并继续接收增量
     */
    RuntimeTurnLink attachTurn(String sessionId, String turnId);

    /**
     * 附件是否走"预签名直传"（agent-system 是；OpenHands 是文件上传 API，能力位供上层与前端分支）
     */
    default boolean supportsPresignedUpload() {
        return true;
    }

    /**
     * 领附件直传凭据（仅支持预签名直传的实现需要实现）。
     * 文案只给名词：框架模板是 {@code SYS_CONFIG_NOT_EXIST = "当前配置不支持该%s"}，
     * 整句塞进来会重复成"当前配置不支持该当前…"（真机冒烟实测到）。
     */
    default UploadUrlResult createUploadUrl(String fileName, long fileSize, String mimeType) {
        throw new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST, "附件直传");
    }

    /**
     * 附件是否走"relay 代收转推"（上游只有文件上传 API、无预签名位时的第二条路：
     * 浏览器把文件交给 relay，relay 用服务端凭据写进沙箱工作目录）。
     * 与 {@link #supportsPresignedUpload()} 互斥使用：两者皆 false 表示该上游不支持附件。
     */
    default boolean supportsRelayUpload() {
        return false;
    }

    /**
     * 代收转推：把内容写入该会话沙箱内路径，返回沙箱内绝对路径（供正文引用）。
     *
     * @param sessionId 平台会话 id
     * @param fileName  展示名（实现方须自行清洗，禁止让它拼出目录穿越）
     * @param content   文件字节
     */
    default String uploadFile(String sessionId, String fileName, byte[] content) {
        throw new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST, "附件代收转推");
    }
}