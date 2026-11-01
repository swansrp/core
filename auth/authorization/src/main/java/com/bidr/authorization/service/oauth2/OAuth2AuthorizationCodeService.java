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
 *   <li><b>单次有效</b>：{@link #consume} 读值后<b>立即删除</b>再返回——同一 code 第二次消费
 *       落空（并发双读的窗口见类注末尾的已知面登记）；</li>
 *   <li><b>过期失效</b>：存键即带 TTL（{@code identity.idp.code-ttl-seconds}，默认 300 秒），
 *       Redis 到期自动清——不消费也会过期；</li>
 *   <li><b>不可预测</b>：code＝{@link SecureRandom} 32 字节 base64url（约 256 bit 熵），
 *       禁 UUID 等可猜测形态。</li>
 * </ol>
 * 🔴 已知面（demo 形态如实登记）：读＋删两步非原子，毫秒级并发双读理论上可双中——授权码 5 分钟
 * 一次性＋绑 client/redirect/用户，爆破/重放收益趋零；原子化（Lua/SETNX 语义）留 P2 记录。</p>
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
     * 消费授权码：读值后<b>立即删除</b>（单次有效）——第二次消费同一 code 落空。
     *
     * @param code 明文 code
     * @return 绑定上下文；code 不存在/已过期/已消费一律 null（调用方按 invalid_grant 拒）
     */
    public OAuth2AuthorizationCode consume(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String key = CODE_KEY_PREFIX + code.trim();
        OAuth2AuthorizationCode context = redisService.get(key, OAuth2AuthorizationCode.class);
        redisService.delete(key);
        return context;
    }

    /** SecureRandom 32 字节 ⇒ base64url 无填充（43 字符） */
    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
