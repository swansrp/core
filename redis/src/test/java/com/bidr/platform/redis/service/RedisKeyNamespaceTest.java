package com.bidr.platform.redis.service;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Title: RedisKeyNamespaceTest
 * Description: 键枚举的命名空间契约——扫描串补 app.projectId 前缀、返回剥掉前缀的逻辑键。
 * 缺陷原形：{@code keysByPattern} 把裸串直接透传给 {@code redisTemplate.keys()}，而读写一律补前缀
 * ⇒ 任何按前缀枚举会话/索引的读腿恒空；若改为返回物理键，调用方再喂回本服务会二次加前缀读空。
 *
 * @author Sharp
 * @since 2026/10/10
 */
class RedisKeyNamespaceTest {

    private static final String APP_ID = "inkhub";
    private static final String SESSION_PATTERN = "llm:agent:session:*";
    private static final String SESSION_KEY = "llm:agent:session:abc";

    private RedisServiceImpl service;
    private RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        service = new RedisServiceImpl();
        redisTemplate = mock(RedisTemplate.class);
        Field field = RedisServiceImpl.class.getDeclaredField("redisTemplate");
        field.setAccessible(true);
        field.set(service, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        BeanUtil.setContext(null);
    }

    private void app(String projectId) {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("app.projectId")).thenReturn(projectId);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        BeanUtil.setContext(context);
    }

    @Test
    @DisplayName("keysByPattern：扫描串带 app 前缀，返回剥掉前缀的逻辑键")
    void keysByPatternScansPrefixedKeysAndReturnsLogicalKeys() {
        app(APP_ID);
        when(redisTemplate.keys("inkhub:" + SESSION_PATTERN)).thenReturn(new LinkedHashSet<>(
                Arrays.asList("inkhub:" + SESSION_KEY, "inkhub:" + SESSION_KEY + ":events")));

        Set<String> keys = service.keysByPattern(SESSION_PATTERN);

        verify(redisTemplate).keys("inkhub:" + SESSION_PATTERN);
        assertEquals(new HashSet<>(Arrays.asList(SESSION_KEY, SESSION_KEY + ":events")), keys);
    }

    @Test
    @DisplayName("逻辑键可直接喂回读写口：再加一次前缀得到原物理键（不二次加前缀）")
    void returnedKeysAreReversibleToPhysicalKeys() {
        app(APP_ID);
        when(redisTemplate.keys("inkhub:" + SESSION_PATTERN))
                .thenReturn(new HashSet<>(Collections.singletonList("inkhub:" + SESSION_KEY)));

        String logicalKey = service.keysByPattern(SESSION_PATTERN).iterator().next();

        assertEquals("inkhub:" + SESSION_KEY, service.getKey(logicalKey));
    }

    @Test
    @DisplayName("剥的是 app 前缀，调用方自己的键前缀保留（会话枚举据此再截自己的前缀）")
    void appPrefixStripKeepsCallerPrefix() {
        app(APP_ID);
        when(redisTemplate.keys("inkhub:" + SESSION_PATTERN))
                .thenReturn(new HashSet<>(Collections.singletonList("inkhub:" + SESSION_KEY)));

        Set<String> keys = service.keysByPattern(SESSION_PATTERN);

        assertEquals("abc", keys.iterator().next().substring("llm:agent:session:".length()));
    }

    @Test
    @DisplayName("getKeys(pattern) 与 keysByPattern 同口径（同族不得各留一套命名空间）")
    void getKeysDelegatesToSameNamespace() {
        app(APP_ID);
        when(redisTemplate.keys("inkhub:" + SESSION_PATTERN))
                .thenReturn(new HashSet<>(Collections.singletonList("inkhub:" + SESSION_KEY)));

        assertEquals(service.keysByPattern(SESSION_PATTERN), service.getKeys(SESSION_PATTERN));
        verify(redisTemplate, org.mockito.Mockito.times(2)).keys("inkhub:" + SESSION_PATTERN);
    }

    @Test
    @DisplayName("keys(prefix)：SCAN 的 match 带 app 前缀，结果剥回逻辑键")
    void keysScansPrefixedMatchAndReturnsLogicalKeys() {
        app(APP_ID);
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("inkhub:auth:token:a", "inkhub:auth:token:b");
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        Set<String> keys = service.keys("auth:token:");

        ArgumentCaptor<ScanOptions> captor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(captor.capture());
        assertEquals("inkhub:auth:token:*", captor.getValue().getPattern());
        assertEquals(new HashSet<>(Arrays.asList("auth:token:a", "auth:token:b")), keys);
    }

    @Test
    @DisplayName("未配置 app.projectId：前缀为空串，进出原样（不因修复而多出冒号）")
    void withoutAppIdKeysPassThroughUnchanged() {
        app(null);
        when(redisTemplate.keys(SESSION_PATTERN))
                .thenReturn(new HashSet<>(Collections.singletonList(SESSION_KEY)));

        assertEquals(new HashSet<>(Collections.singletonList(SESSION_KEY)), service.keysByPattern(SESSION_PATTERN));
        verify(redisTemplate).keys(SESSION_PATTERN);
    }

    @Test
    @DisplayName("扫不到键＝空集，不抛也不回 null")
    void emptyScanYieldsEmptySet() {
        app(APP_ID);
        when(redisTemplate.keys("inkhub:" + SESSION_PATTERN)).thenReturn(Collections.emptySet());

        Set<String> keys = service.keysByPattern(SESSION_PATTERN);

        assertTrue(keys.isEmpty());
    }

    @Test
    @DisplayName("空模式拒绝：不接受裸串扫描，绝不退化成全库枚举")
    void blankPatternIsRejectedBeforeScanning() {
        app(APP_ID);

        assertThrows(ServiceException.class, () -> service.keysByPattern(""));
        assertThrows(ServiceException.class, () -> service.keys(null));
        verifyNoInteractions(redisTemplate);
    }
}
