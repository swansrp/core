package com.bidr.authorization.client;

import com.bidr.authorization.authz.AuthzVersionHolder;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClientException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: RemoteAuthzCheckerTest
 * Description: T2.3b 判定链五腿＋bump 实证腿（plan §8.1 钉死，mock AuthzCheckApi＋真
 * AuthzCache，零真 HTTP）：
 * <ol>
 *   <li>缓存命中零 HTTP（版本节流窗口内不重复拉 /authz/version、check 不重发）；</li>
 *   <li>版本变化（server bump）→ AuthzCache 整缓存失效 → 重拉判定；</li>
 *   <li>HTTP 失败 × CLOSED＝异常上抛（判定拒绝）；</li>
 *   <li>HTTP 失败 × OPEN_TO_CACHE＝回落最近成功判定（版本已 bump、主缓存已清的强形态）；</li>
 *   <li>versionRefreshMs 节流周期内不重复拉 /authz/version；</li>
 *   <li>bump 实证：AuthzVersionHolder.bump() 后 current() 变化＋旧版本缓存键失效。</li>
 * </ol>
 * 附加腿：无登录态（AccountContext 空）fail-closed 直接拒、零远程交互。
 *
 * @author coder (棒4, T2.3b 2026-11-03)
 * @since 2026-11-03
 */
public class RemoteAuthzCheckerTest {

    private static final long USER_ID = 919L;

    private final AuthzCheckApi api = mock(AuthzCheckApi.class);
    private final AuthzClientProperties properties = new AuthzClientProperties();
    private final AuthzCache cache = new AuthzCache();
    private RemoteAuthzChecker checker;

    @Before
    public void wire() {
        properties.setFailMode(AuthzClientProperties.FailMode.CLOSED);
        checker = new RemoteAuthzChecker(api, cache, properties);
        AccountInfo account = new AccountInfo();
        account.setUserId(USER_ID);
        AccountContext.set(account);
    }

    @After
    public void clean() {
        AccountContext.remove();
    }

    private void stubVersion(long version) {
        when(api.authzVersion()).thenReturn(version);
    }

    private void stubCheck(boolean allowed) {
        when(api.check(anyLong(), anyString(), anyString())).thenReturn(allowed);
    }

    /** 腿①：缓存命中零 HTTP——窗口内第二次判定不发 check、不重拉 version */
    @Test
    public void 腿1_缓存命中零HTTP() {
        properties.setVersionRefreshMs(Long.MAX_VALUE);
        stubVersion(1L);
        stubCheck(true);

        assertTrue(checker.isAdmin());
        // 第二次：节流窗口内 version 不重拉＋缓存命中 → api 面零新增调用
        assertTrue(checker.isAdmin());

        verify(api, times(1)).authzVersion();
        verify(api, times(1)).check(anyLong(), anyString(), anyString());
    }

    /** 腿②：版本变化（bump）→缓存失效重拉——server 侧判定翻转要能收回 */
    @Test
    public void 腿2_版本变化缓存失效重拉() {
        properties.setVersionRefreshMs(0L);
        stubVersion(1L);
        stubCheck(true);
        assertTrue(checker.isAdmin());

        // server bump＋判定翻转（撤管）
        stubVersion(2L);
        stubCheck(false);
        assertFalse(checker.isAdmin());

        verify(api, times(2)).authzVersion();
        verify(api, times(2)).check(anyLong(), anyString(), anyString());
    }

    /** 腿③：HTTP 失败 × CLOSED＝上抛拒绝（fail-closed） */
    @Test
    public void 腿3_HTTP失败CLOSED拒() {
        properties.setVersionRefreshMs(0L);
        stubVersion(1L);
        when(api.check(anyLong(), anyString(), anyString()))
                .thenThrow(new RestClientException("hub down"));

        assertThrows(RestClientException.class, checker::isAdmin);
    }

    /** 腿④：HTTP 失败 × OPEN_TO_CACHE＝回落最近成功判定（强形态：版本已 bump、主缓存已清） */
    @Test
    public void 腿4_HTTP失败OPEN_TO_CACHE回落缓存() {
        properties.setFailMode(AuthzClientProperties.FailMode.OPEN_TO_CACHE);
        properties.setVersionRefreshMs(0L);
        stubVersion(1L);
        stubCheck(true);
        assertTrue(checker.isAdmin());

        // 版本 bump（主缓存随 get 清空）＋随后 check 网络挂 → 回落 lastGood=true
        stubVersion(2L);
        when(api.check(anyLong(), anyString(), anyString()))
                .thenThrow(new RestClientException("hub down"));
        assertTrue("OPEN_TO_CACHE 应回落最近成功判定", checker.isAdmin());
    }

    /** 腿④补：OPEN_TO_CACHE 但从未成功过（无回落值）＝同样拒（上抛） */
    @Test
    public void 腿4补_OPEN_TO_CACHE无回落值也拒() {
        properties.setFailMode(AuthzClientProperties.FailMode.OPEN_TO_CACHE);
        properties.setVersionRefreshMs(0L);
        stubVersion(1L);
        when(api.check(anyLong(), anyString(), anyString()))
                .thenThrow(new RestClientException("hub down"));

        assertThrows(RestClientException.class, checker::isAdmin);
    }

    /** 腿⑤：version 节流周期内不重复拉 /authz/version（多次判定只拉一次） */
    @Test
    public void 腿5_版本节流不重复拉() {
        properties.setVersionRefreshMs(Long.MAX_VALUE);
        stubVersion(5L);
        stubCheck(true);

        assertTrue(checker.isAdmin());
        assertTrue(checker.isAdmin());
        assertTrue(checker.isAdmin());

        verify(api, times(1)).authzVersion();
    }

    /** 腿⑥（bump 实证）：bump 后 current() 变化＋旧版本缓存键失效（AuthzCache 版本键控） */
    @Test
    public void 腿6_bump实证_旧版本缓存键失效() {
        long before = AuthzVersionHolder.current();
        cache.put(before, USER_ID, "t:7", "kb.search", true);
        assertEquals(1, cache.size());

        long after = AuthzVersionHolder.bump();
        assertTrue("bump 后版本必须前进", after > before);
        assertEquals(after, AuthzVersionHolder.current());

        // 旧版本键随新版本 get 整缓存失效（bump→秒级收回机制腿）
        org.junit.Assert.assertNull("版本不符应清空旧判定", cache.get(after, USER_ID, "t:7", "kb.search"));
        assertEquals(0, cache.size());
    }

    /** 附加腿：无登录态（AccountContext 空）fail-closed 直接拒，零远程交互 */
    @Test
    public void 附加_无登录态fail_closed() {
        AccountContext.remove();
        assertFalse(checker.isAdmin());
        Mockito.verifyNoInteractions(api);
    }

    /** 附加腿：admin 判定的最小调用面约定＝check(userId, "", "admin")（与 server 侧约定钉死） */
    @Test
    public void 附加_admin最小调用面约定() {
        properties.setVersionRefreshMs(Long.MAX_VALUE);
        stubVersion(1L);
        stubCheck(true);

        assertTrue(checker.isAdmin());
        verify(api).check(eq(USER_ID), eq(""), eq("admin"));
    }
}
