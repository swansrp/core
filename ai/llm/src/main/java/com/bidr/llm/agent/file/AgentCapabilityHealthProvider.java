package com.bidr.llm.agent.file;

import java.util.Map;

/**
 * Title: AgentCapabilityHealthProvider
 * Description: agent 能力位<b>运行级健康快照</b>端口（探活可观测层的对外契约，llm 协议层零外围依赖，
 * 只 import {@code java.util}）——{@code /agents} 契约经本端口把探活件的探测结果上报前端
 * （徽标亮/灰两态）：
 * <ul>
 *     <li><b>运行级补充</b>：不改 {@code AgentSummary#capabilities} 的声明级推导（端口/工具 Bean
 *         在场即 on），探活失败只让徽标变灰，能力位与相关工具面不受影响；</li>
 *     <li>key＝能力位名（{@code ocr}/{@code artifacts.doc}/{@code exec.sandbox}，与
 *         {@link AgentFileCapabilityResolver} 能力位常量同口径），value＝{@link #UP}/
 *         {@link #DOWN}/{@link #UNCONFIGURED} 小写常量；</li>
 *     <li><b>缺席＝兼容</b>：无实现 Bean 时 {@code /agents} 不输出 health 字段（前端全亮＝旧宿主
 *         行为）；{@code snapshot()} 返回不可变副本，读侧可安全共享。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/14
 */
public interface AgentCapabilityHealthProvider {

    /** 健康值：探测可达（按腿口径 200/任意状态码） */
    String UP = "up";

    /** 健康值：已配置但服务不可达（连接异常/非预期状态码/密钥无效） */
    String DOWN = "down";

    /** 健康值：未配置（对应腿 base-url/密钥缺失或未启用），不发包不计失败 */
    String UNCONFIGURED = "unconfigured";

    /**
     * 能力位健康快照：key=能力位名，value=up/down/unconfigured；
     * 返回不可变副本（探测后更新，启动+定时刷新）
     */
    Map<String, String> snapshot();
}
