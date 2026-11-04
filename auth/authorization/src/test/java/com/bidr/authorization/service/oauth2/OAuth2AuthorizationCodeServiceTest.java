package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.platform.redis.service.RedisService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: OAuth2AuthorizationCodeServiceTest
 * Description: 授权码生命周期（T1.2 棒 6）：签发→消费往返、二次消费失效（单次有效）、
 * 未签发/过期消费落空、TTL 按配置值入 Redis、code 形态（SecureRandom base64url 43 字符）。
 * Redis 用 Mockito 打桩成内存 Map（TokenServiceImpl 离线腿同款做法，不碰真 Redis）。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class OAuth2AuthorizationCodeServiceTest {

    private RedisService redisService;
    private OAuth2AuthorizationCodeService codeService;
    /** set/get/delete 的内存模拟（键值即 RedisService 的逻辑键） */
    private Map<String, Object> store;

    @Before
    public void setUp() {
        IdentityIdpProperties properties = new IdentityIdpProperties();
        properties.setCodeTtlSeconds(300L);
        redisService = Mockito.mock(RedisService.class);
        store = new ConcurrentHashMap<>();
        Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0, String.class), invocation.getArgument(2));
            return null;
        }).when(redisService).set(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any());
        Mockito.when(redisService.get(ArgumentMatchers.anyString(),
                ArgumentMatchers.<Class<?>>any(), ArgumentMatchers.<Class<?>>any()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, String.class)));
        Mockito.when(redisService.delete(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            // 真 Redis DEL 语义：删到行（键在场）返回 true，键缺席返回 false——
            // consume 的原子消费权闸依赖该语义（review 第 1 轮 I-5）
            return store.remove(invocation.getArgument(0, String.class)) != null;
        });
        codeService = new OAuth2AuthorizationCodeService(redisService, properties);
    }

    private OAuth2AuthorizationCode context() {
        OAuth2AuthorizationCode ctx = new OAuth2AuthorizationCode();
        ctx.setClientId("demo-app");
        ctx.setRedirectUri("http://127.0.0.1:5174");
        ctx.setUserId(42L);
        ctx.setCustomerNumber("000001");
        ctx.setUserName("zhangsan");
        ctx.setName("张三");
        ctx.setTenantIds(new ArrayList<>(Arrays.asList(5L, 7L)));
        return ctx;
    }

    @Test
    public void issue_thenConsume_roundTripKeepsBinding() {
        String code = codeService.issue(context());
        Assert.assertNotNull(code);
        Assert.assertEquals("SecureRandom 32 字节 base64url＝43 字符", 43, code.length());
        OAuth2AuthorizationCode consumed = codeService.consume(code);
        Assert.assertNotNull(consumed);
        Assert.assertEquals("demo-app", consumed.getClientId());
        Assert.assertEquals("http://127.0.0.1:5174", consumed.getRedirectUri());
        Assert.assertEquals(Long.valueOf(42L), consumed.getUserId());
        Assert.assertEquals("000001", consumed.getCustomerNumber());
        Assert.assertEquals("zhangsan", consumed.getUserName());
        Assert.assertEquals(Arrays.asList(5L, 7L), consumed.getTenantIds());
    }

    @Test
    public void consumeTwice_secondUseFails() {
        String code = codeService.issue(context());
        Assert.assertNotNull(codeService.consume(code));
        Assert.assertNull("授权码单次有效：二次消费必须落空", codeService.consume(code));
    }

    /**
     * review 第 1 轮 I-5 收口：并发双消费恰一成功（RFC 6749 §4.1.2 单次有效 MUST）。
     * 单测以 DEL=0 路径钉死——GET 拿到值但 DEL 返回 false（消费权被并发方抢走）必须落空；
     * 真并发恰一成功由 Redis DEL 的单键原子性保证（键含 256bit 随机 code，不重发）。
     */
    @Test
    public void consume_delLostRace_evenWithFreshReadReturnsNull() {
        String code = codeService.issue(context());
        String key = OAuth2AuthorizationCodeService.CODE_KEY_PREFIX + code;

        // 并发抢先方已把键删掉（DEL=0）；本方 GET 恰在删除前读到了值——胜负只看 DEL
        Mockito.when(redisService.delete(key)).thenReturn(Boolean.FALSE);

        Assert.assertNull("GET 有值但 DEL=0 ⇒ 消费权被并发方独占 ⇒ 落空", codeService.consume(code));
    }

    @Test
    public void consume_delGateExactlyOneWinnerUnderSimulatedRace() {
        // 两线程同 code 消费的离线形态：先到方 DEL=true 拿走上下文，后到方 DEL=false 落空
        String code = codeService.issue(context());
        Assert.assertNotNull("先到方（DEL=true）独占消费权", codeService.consume(code));
        Mockito.when(redisService.delete(OAuth2AuthorizationCodeService.CODE_KEY_PREFIX + code))
                .thenReturn(Boolean.FALSE);
        Assert.assertNull("后到方（DEL=0）落空——恰一成功", codeService.consume(code));
    }

    @Test
    public void consume_unknownOrExpiredCode_fails() {
        Assert.assertNull("未签发 code 消费落空", codeService.consume("no-such-code"));
        // 过期形态：Redis TTL 到期后 get 落空（等价模拟——键被清）
        String code = codeService.issue(context());
        store.clear();
        Assert.assertNull("过期 code 消费落空", codeService.consume(code));
    }

    @Test
    public void consume_blankCode_noRedisInteraction() {
        Assert.assertNull(codeService.consume(" "));
        Mockito.verifyNoInteractions(redisService);
    }

    @Test
    public void issue_storesWithConfiguredTtl() {
        codeService.issue(context());
        ArgumentCaptor<Integer> ttl = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(redisService).set(Mockito.startsWith(OAuth2AuthorizationCodeService.CODE_KEY_PREFIX),
                ttl.capture(), ArgumentMatchers.any());
        Assert.assertEquals(Integer.valueOf(300), ttl.getValue());
        Assert.assertEquals("Redis 里恰好一条授权码", 1, store.size());
    }
}
