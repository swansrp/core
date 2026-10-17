package com.bidr.llm.usage;

import com.bidr.kernel.utils.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Title: ModelUsageReporter
 * Description: 模型包装层交用量的唯一出口（M14 棒5）：现取 sink、兜异常、算 key 指纹。
 *
 * <p>三条客户端（sync / SSE / vision）都从这里出，指纹算法与"取不到 bean 就静默"的口径只有一份。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
public final class ModelUsageReporter {

    /**
     * 指纹取 sha256 hex 的前 16 位：够区分"用的是哪一把 key"（拆账要的就是这个分组键），
     * 又不必把整串哈希铺在流水行里。截短不削弱不可逆性——反查明文本来就不可能。
     */
    static final int FINGERPRINT_HEX_CHARS = 16;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ModelUsageReporter() {
    }

    /**
     * 建一条用量事实：载体、用途档、模型名、key 指纹与当时的会话身份一次填齐，
     * token/工具数/finish/耗时由调用方按各自链路读到的值补——读不到的栏留 null
     *
     * @param identity 会话身份快照（{@code ModelCallContexts.current()} 或发起线程早拍的快照）
     */
    public static ModelUsageFacts newFacts(String via, String purposeType, String modelName,
                                           String keyFingerprint, ModelCallContext identity) {
        ModelUsageFacts facts = new ModelUsageFacts();
        facts.setVia(via);
        facts.setPurposeType(purposeType);
        facts.setModelName(modelName);
        facts.setKeyFingerprint(keyFingerprint);
        if (identity != null) {
            facts.setSessionId(identity.getSessionId());
            facts.setAgentKey(identity.getAgentKey());
            facts.setAskedBy(identity.getAskedBy());
        }
        facts.setCallAtMs(System.currentTimeMillis());
        return facts;
    }

    /**
     * 交出台账：框架不装实现时 {@code getBean} 返回 null ⇒ 静默跳过，模型调用行为与改前逐字节一致。
     * 取 bean 与回调整体兜异常——计量是复盘与计费的底账，写不进只记 warn，绝不把模型调用带崩
     * （同 {@code AgentSessionContext#reportToolSurface} 的纪律）
     */
    public static void report(ModelUsageFacts facts) {
        if (facts == null) {
            return;
        }
        try {
            ModelUsageSink sink = BeanUtil.getBean(ModelUsageSink.class);
            if (sink == null) {
                return;
            }
            sink.report(facts);
        } catch (Exception e) {
            log.warn("模型用量上报失败（不影响模型调用）: via={} purpose={} session={} error={}",
                    facts.getVia(), facts.getPurposeType(), facts.getSessionId(), e.getMessage());
        }
    }

    /**
     * key 指纹（🔴 绝不返回明文）：空/空白 key＝null，哈希取不到＝null——两种 null 都按"这一路没 key"
     * 落库，不拿占位串冒充一把不存在的 key
     */
    public static String keyFingerprint(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
            }
            return hex.substring(0, Math.min(FINGERPRINT_HEX_CHARS, hex.length()));
        } catch (Exception e) {
            log.warn("模型 key 指纹计算失败（该栏留空）: error={}", e.getMessage());
            return null;
        }
    }
}
