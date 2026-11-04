package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.platform.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Title: OAuth2AuthorizationCodeService
 * Description: 授权码生命周期（T1.2 棒 6 纯加法）——生成／存取／消费，存量基建＝
 * {@link RedisService}（TokenServiceImpl 同款键值形态：逻辑键进、TTL 由 Redis 兜底过期）。
 *
 * <p>三条承重语义：
 * <ol>
 *   <li><b>单次有效</b>：{@link #consume} 以 Redis DEL 的原子性裁决消费权（GET 只取值、
 *       DEL 删到行者独占）——同一 code 二次/并发二次消费一律落空（RFC 6749 §4.1.2 的
 *       单次有效 MUST，review 第 1 轮 I-5 已收口）；</li>
 *   <li><b>过期失效</b>：存键即带 TTL（{@code identity.idp.code-ttl-seconds}，默认 300 秒），
 *       Redis 到期自动清——不消费也会过期；</li>
 *   <li><b>不可预测</b>：code＝{@link SecureRandom} 32 字节 base64url（约 256 bit 熵），
 *       禁 UUID 等可猜测形态。</li>
 * </ol>
 * 原子化收口说明（review 第 1 轮 I-5）：旧形态「GET→DEL 不看 DEL 结果」存在毫秒级并发双读
 * 窗口（旧类注登记"留 P2"）；现形态 DEL 单键操作在 Redis 侧原子，删到行（返回 true）的
 * 恰好一方独占消费权，并发双消费恰一成功，窗口关闭。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthorizationCodeService {

    /** Redis 逻辑键前缀（命名空间零客户名；实键由 RedisService 统一补 app 前缀） */
    public static final String CODE_KEY_PREFIX = "identity:idp:authcode:";

    /** code 字节数：32 字节 SecureRandom ＝ 256 bit 熵，base64url 后 43 字符 */
    private static final int CODE_BYTES = 32;

    private final RedisService redisService;
    private final IdentityIdpProperties idpProperties;

    /**
     * 签发授权码：生成 SecureRandom code ＋ 上下文固化存 Redis（TTL＝code-ttl-seconds）。
     *
     * @param context 绑定上下文（client/redirect/用户/租户），随 code 一起入 Redis
     * @return 明文 code（只此一次机会——服务端不存明文映射，键即 code）
     */
    public String issue(OAuth2AuthorizationCode context) {
        String code = generateCode();
        redisService.set(CODE_KEY_PREFIX + code, (int) idpProperties.getCodeTtlSeconds(), context);
        log.info("[IdP授权码]签发: client={}, user={}, ttl={}s", context.getClientId(),
                context.getCustomerNumber(), idpProperties.getCodeTtlSeconds());
        return code;
    }

    /**
     * 消费授权码：原子单次有效（review 第 1 轮 I-5 收口形态）——GET 取值后以 DEL 的返回值
     * 裁决：删到行（true）者独占消费权，DEL=0（已被并发消费/已过期）一律落空。
     * 并发双消费恰一成功。
     *
     * @param code 明文 code
     * @return 绑定上下文；code 不存在/已过期/已消费一律 null（调用方按 invalid_grant 拒）
     */
    public OAuth2AuthorizationCode consume(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String key = CODE_KEY_PREFIX + code.trim();
        // 前置 GET 只为取值，不参与胜负判定；DEL 是 Redis 原子操作＝消费权闸——
        // 键含 256bit 随机 code，GET 拿到值而 DEL=0 只可能发生在并发抢先消费场景
        OAuth2AuthorizationCode context = redisService.get(key, OAuth2AuthorizationCode.class);
        if (!Boolean.TRUE.equals(redisService.delete(key))) {
            // DEL=0 ⇒ 已被并发消费（或已过期）⇒ 走既有无效路径，调用方按 invalid_grant 拒
            return null;
        }
        return context;
    }

    /** SecureRandom 32 字节 ⇒ base64url 无填充（43 字符） */
    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
