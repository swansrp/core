package com.bidr.authorization.client;

import com.bidr.authorization.authz.AuthzVersionHolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: AuthzCacheTest
 * Description: T2.3b 判定缓存单测——version bump 即失效、双版本并存语义、fail 边界。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class AuthzCacheTest {

    @Test
    public void putGet同版本命中() {
        AuthzCache cache = new AuthzCache();
        cache.put(1L, 100L, "t:1", "writer.draft", true);
        assertEquals(Boolean.TRUE, cache.get(1L, 100L, "t:1", "writer.draft"));
        assertEquals(1, cache.size());
    }

    @Test
    public void 版本bump即清空() {
        AuthzCache cache = new AuthzCache();
        cache.put(1L, 100L, "t:1", "writer.draft", true);
        cache.put(1L, 101L, "t:1", "writer.draft", false);
        assertEquals(2, cache.size());
        // 版本 bump：全部失效
        assertNull(cache.get(2L, 100L, "t:1", "writer.draft"));
        assertEquals(0, cache.size());
    }

    @Test
    public void bump后可写入新判定() {
        AuthzCache cache = new AuthzCache();
        cache.put(1L, 100L, "t:1", "kb.search", true);
        assertNull(cache.get(2L, 100L, "t:1", "kb.search"));
        cache.put(2L, 100L, "t:1", "kb.search", false);
        assertFalse(cache.get(2L, 100L, "t:1", "kb.search"));
        assertTrue(cache.get(2L, 100L, "t:1", "kb.search") == Boolean.FALSE);
    }

    @Test
    public void invalidate强制清空() {
        AuthzCache cache = new AuthzCache();
        cache.put(1L, 100L, "p:9", "epc.supervision", true);
        cache.invalidate();
        assertEquals(0, cache.size());
        assertNull(cache.get(1L, 100L, "p:9", "epc.supervision"));
    }

    @Test
    public void AuthzVersionHolder_bump递增() {
        long before = AuthzVersionHolder.current();
        long after = AuthzVersionHolder.bump();
        assertTrue(after > before);
        assertEquals(after, AuthzVersionHolder.current());
    }
}
