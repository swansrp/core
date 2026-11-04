package com.bidr.authorization.client.session;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.client.AuthzCheckApi;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.identity.mirror.dao.repository.IdmTenantMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserTenantMirrorService;
import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SubsystemSessionService} 自签会话链（镜像闸 → 租户闸 → 零租户回 provider 判管理员 → 双票签发）
 * 离线单测。
 *
 * <p>钉五件事：① 身份缺失／镜像无此人／镜像已停用／<b>非平台管理员</b>零有效租户归属 ⇒ 一律
 * {@link ServiceException} 且不发会话（平台管理员腿：零租户时回 {@link AuthzCheckApi} 判，
 * 判是才放行——<b>与镜像 id 数值无关</b>）；② 正常腿 ⇒ {@code accessToken}/{@code refreshToken}
 * 非空且可由 {@link AuthTokenUtil#decode(String)} 解回对应 {@link TokenType}；③ 烤入 token 的内容含
 * {@code OPERATOR}（工号）／{@code USER_ID}（镜像 id）／{@code NICK_NAME}／{@code USER_NAME}／
 * {@code TENANT_IDS}＋{@code TENANT_ID}；④ {@link TokenHolder} 同请求内被置位；⑤ {@link
 * SubsystemSessionService#refresh(String)} 与登录<b>同口径</b>（无效票／类型冒充／镜像停用／
 * 非管理员零租户一律拒；旧 refresh 一次性销毁）。</p>
 *
 * <p>管理员判据腿单独钉 {@link SubsystemSessionService#isPlatformAdmin(Long)} 的四条 fail-closed 语义
 * （null／provider 未装配／provider 抛异常／正常委托），防止闸被"本地猜 id"实现绕过。</p>
 *
 * <p>🔴 本模块沿用 JUnit 4（见 {@code ai/*} 模块 pom 的备注：传递依赖带 JUnit4 时 surefire 会选错
 * provider，JUnit5 测试会静默 0 条）。</p>
 *
 * @author Sharp
 * @since 2026-11-04
 */
@RunWith(MockitoJUnitRunner.class)
public class SubsystemSessionServiceTest {

    private static final String USER_NAME = "u1";
    private static final String CUSTOMER_NUMBER = "CN-u1";
    private static final long MIRROR_USER_ID = 88L;

    /** 与 core RemoteAuthzChecker 的接线契约（server 侧 admin 短路先于 scope 解析） */
    private static final String ADMIN_SCOPE = "";
    private static final String ADMIN_PERMISSION = "admin";

    @Mock
    private IdmUserMirrorService userMirrorService;
    @Mock
    private IdmUserTenantMirrorService userTenantMirrorService;
    @Mock
    private IdmTenantMirrorService tenantMirrorService;
    @Mock
    private TokenService tokenService;
    @Mock
    private ObjectProvider<AuthzCheckApi> authzCheckApiProvider;
    @Mock
    private AuthzCheckApi authzCheckApi;

    private SubsystemSessionService sessionService;

    @Before
    public void setUp() {
        sessionService = new SubsystemSessionService(userMirrorService, userTenantMirrorService,
                tenantMirrorService, tokenService, authzCheckApiProvider);
    }

    @After
    public void tearDown() {
        // issueSession 置位的线程态 token：清掉防线程池串号
        TokenHolder.remove();
    }

    // ==================== fixture ====================

    private static IdmUserMirror mirrorUser(String userName, String customerNumber, String valid) {
        return mirrorUser(MIRROR_USER_ID, userName, customerNumber, valid);
    }

    private static IdmUserMirror mirrorUser(long id, String userName, String customerNumber, String valid) {
        IdmUserMirror mirror = new IdmUserMirror();
        mirror.setId(id);
        mirror.setUserName(userName);
        mirror.setCustomerNumber(customerNumber);
        mirror.setName("张三");
        mirror.setValid(valid);
        return mirror;
    }

    private static IdmTenantMirror validTenant(Long id) {
        IdmTenantMirror tenant = new IdmTenantMirror();
        tenant.setId(id);
        tenant.setTenantCode("tenant-" + id);
        tenant.setTenantName("租户" + id);
        tenant.setValid("1");
        return tenant;
    }

    private static IdmTenantMirror invalidTenant(Long id) {
        IdmTenantMirror tenant = validTenant(id);
        tenant.setValid("0");
        return tenant;
    }

    private static IdmUserTenantMirror relation(Long tenantId, String valid) {
        IdmUserTenantMirror relation = new IdmUserTenantMirror();
        relation.setUserId(MIRROR_USER_ID);
        relation.setTenantId(tenantId);
        relation.setValid(valid);
        return relation;
    }

    /** 两条有效归属（11／12）＋一条指向停用租户（13）＋一条关系行已失效（14） */
    private void stubTenantMirrors() {
        when(tenantMirrorService.listAll()).thenReturn(Arrays.asList(
                validTenant(11L), validTenant(12L), invalidTenant(13L), validTenant(14L)));
        when(userTenantMirrorService.listByUserId(MIRROR_USER_ID)).thenReturn(Arrays.asList(
                relation(11L, "1"), relation(13L, "1"), relation(12L, "1"), relation(14L, "0")));
    }

    private void stubTokenIssuing() {
        when(tokenService.buildWebAccessToken(CUSTOMER_NUMBER))
                .thenReturn(new TokenInfo("uuid-access", TokenType.WEB_ACCESS_TOKEN, CUSTOMER_NUMBER, 600));
        when(tokenService.buildWebRefreshToken(CUSTOMER_NUMBER))
                .thenReturn(new TokenInfo("uuid-refresh", TokenType.WEB_REFRESH_TOKEN, CUSTOMER_NUMBER, 1200));
    }

    /** 记录 putItem 写入项，并让 getTokenValue(...) 回放同一份内容（＝redis 侧的 token map） */
    private Map<String, Object> recordTokenItems() {
        Map<String, Object> items = new HashMap<>();
        doAnswer(invocation -> {
            items.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(tokenService).putItem(any(TokenInfo.class), anyString(), any());
        // 管理员零租户腿只断言 items 本身、不消费回放，故 lenient
        lenient().when(tokenService.getTokenValue(any(TokenInfo.class))).thenReturn(items);
        return items;
    }

    /** 零租户腿的必经通道：provider 在位、默认判非管理员（判真由各腿显式覆写） */
    private void stubAuthzClient() {
        when(authzCheckApiProvider.getIfAvailable()).thenReturn(authzCheckApi);
    }

    // ==================== login：拒绝腿 ====================

    @Test
    public void 登录名缺失_拒绝登录且零交互() {
        ServiceException nullEx = Assert.assertThrows(ServiceException.class, () -> sessionService.login(null));
        ServiceException blankEx = Assert.assertThrows(ServiceException.class, () -> sessionService.login("  "));

        Assert.assertTrue(nullEx.getMessage(), nullEx.getMessage().contains("登录身份缺失"));
        Assert.assertTrue(blankEx.getMessage(), blankEx.getMessage().contains("登录身份缺失"));
        verifyNoInteractions(userMirrorService, userTenantMirrorService, tenantMirrorService,
                tokenService, authzCheckApiProvider);
    }

    @Test
    public void 镜像查无此人_拒绝登录且零写() {
        when(userMirrorService.findByUserName(USER_NAME)).thenReturn(null);

        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> sessionService.login(USER_NAME));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("尚未同步到本系统"));
        // 镜像闸在前：租户/签发一律不碰
        verifyNoInteractions(userTenantMirrorService, tenantMirrorService, tokenService, authzCheckApiProvider);
    }

    @Test
    public void 镜像已停用_拒绝登录() {
        when(userMirrorService.findByUserName(USER_NAME)).thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "0"));

        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> sessionService.login(USER_NAME));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("已停用"));
        verifyNoInteractions(userTenantMirrorService, tenantMirrorService, tokenService, authzCheckApiProvider);
    }

    @Test
    public void 非管理员零有效租户归属_拒绝登录() {
        when(userMirrorService.findByUserName(USER_NAME)).thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "1"));
        // 关系行指向停用租户（13）＋关系行自身失效（14）⇒ 有效租户集为空
        when(tenantMirrorService.listAll()).thenReturn(Arrays.asList(validTenant(11L), invalidTenant(13L)));
        when(userTenantMirrorService.listByUserId(MIRROR_USER_ID))
                .thenReturn(Arrays.asList(relation(13L, "1"), relation(14L, "0")));
        stubAuthzClient();

        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> sessionService.login(USER_NAME));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("无有效租户归属"));
        // 空租户集绝不放行（空集在 core 是读旁路）：一票都不签发
        verifyNoInteractions(tokenService);
        // 闸的判据必须回 provider（默认 false 即非管理员）
        verify(authzCheckApi).check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION);
    }

    @Test
    public void 关系行存在但租户目录为空_同样按零归属拒绝() {
        when(userMirrorService.findByUserName(USER_NAME)).thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "1"));
        when(tenantMirrorService.listAll()).thenReturn(new ArrayList<>());
        stubAuthzClient();

        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> sessionService.login(USER_NAME));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("无有效租户归属"));
        verifyNoInteractions(userTenantMirrorService, tokenService);
        verify(authzCheckApi).check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION);
    }

    // ==================== login：正常腿 ====================

    @Test
    public void 正常腿_签发双票并烤入身份项与租户项() {
        when(userMirrorService.findByUserName(USER_NAME)).thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "1"));
        stubTenantMirrors();
        stubTokenIssuing();
        Map<String, Object> items = recordTokenItems();

        LoginRes res = sessionService.login(USER_NAME);

        // 出参：userId 内部用（@JsonIgnore），name/customerNumber 照给，双票非空
        Assert.assertEquals(Long.valueOf(MIRROR_USER_ID), res.getUserId());
        Assert.assertEquals(CUSTOMER_NUMBER, res.getCustomerNumber());
        Assert.assertEquals("张三", res.getName());
        Assert.assertNotNull("accessToken 必须非空", res.getAccessToken());
        Assert.assertNotNull("refreshToken 必须非空", res.getRefreshToken());

        // 票串可解回、类型/工号正确（＝TokenService.buildWeb* 产物经 AuthTokenUtil.getToken 拼装）
        TokenInfo access = AuthTokenUtil.decode(res.getAccessToken());
        Assert.assertEquals(TokenType.WEB_ACCESS_TOKEN, access.getType());
        Assert.assertEquals(CUSTOMER_NUMBER, access.getCustomerNumber());
        TokenInfo refreshToken = AuthTokenUtil.decode(res.getRefreshToken());
        Assert.assertEquals(TokenType.WEB_REFRESH_TOKEN, refreshToken.getType());
        Assert.assertEquals(CUSTOMER_NUMBER, refreshToken.getCustomerNumber());

        // 烤入 access token 的内容（＝tokenService.getTokenValue(access) 回放的 map）
        Map<String, Object> tokenValue = tokenService.getTokenValue(access);
        Assert.assertEquals("OPERATOR＝工号", CUSTOMER_NUMBER, tokenValue.get(TokenItem.OPERATOR.name()));
        Assert.assertEquals("USER_ID＝镜像 id", Long.valueOf(MIRROR_USER_ID), tokenValue.get(TokenItem.USER_ID.name()));
        Assert.assertEquals("张三", tokenValue.get(TokenItem.NICK_NAME.name()));
        Assert.assertEquals(USER_NAME, tokenValue.get(TokenItem.USER_NAME.name()));
        Assert.assertEquals("TENANT_IDS＝两侧 valid='1' 的交集、按关系行顺序去重（排除停用租户 13 与失效关系 14）",
                Arrays.asList(11L, 12L), tokenValue.get(TokenItem.TENANT_IDS.name()));
        Assert.assertEquals("TENANT_ID 取首个", Long.valueOf(11L), tokenValue.get(TokenItem.TENANT_ID.name()));
        // 镜像无角色字段（G9）：不写 ROLE_MAP／PERMIT_LIST（M-43：client 对缺键容忍）
        Assert.assertFalse("不得写 ROLE_MAP", items.containsKey(TokenItem.ROLE_MAP.name()));
        Assert.assertFalse("不得写 PERMIT_LIST", items.containsKey(TokenItem.PERMIT_LIST.name()));

        // 同请求内线程态 token（供 tokenService.getTokenValue() 无参形态使用）
        TokenInfo holderToken = TokenHolder.get();
        Assert.assertNotNull("TokenHolder 必须置位", holderToken);
        Assert.assertEquals("uuid-access", holderToken.getToken());
        // 有租户的用户不发起管理员回判（零额外 HTTP）
        verifyNoInteractions(authzCheckApiProvider, authzCheckApi);
    }

    @Test
    public void 平台管理员零租户_回判放行签发会话且不烤租户项() {
        when(userMirrorService.findByUserName(USER_NAME))
                .thenReturn(mirrorUser(MIRROR_USER_ID, USER_NAME, CUSTOMER_NUMBER, "1"));
        when(tenantMirrorService.listAll()).thenReturn(new ArrayList<>());
        stubAuthzClient();
        when(authzCheckApi.check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION)).thenReturn(true);
        stubTokenIssuing();
        Map<String, Object> items = recordTokenItems();

        LoginRes res = sessionService.login(USER_NAME);

        Assert.assertEquals(Long.valueOf(MIRROR_USER_ID), res.getUserId());
        Assert.assertNotNull("accessToken 必须非空", res.getAccessToken());
        Assert.assertNotNull("refreshToken 必须非空", res.getRefreshToken());
        TokenInfo access = AuthTokenUtil.decode(res.getAccessToken());
        Assert.assertEquals(TokenType.WEB_ACCESS_TOKEN, access.getType());
        Assert.assertEquals("USER_ID＝镜像 id(=上游 uid)", Long.valueOf(MIRROR_USER_ID),
                items.get(TokenItem.USER_ID.name()));
        Assert.assertFalse("零租户不得写 TENANT_IDS", items.containsKey(TokenItem.TENANT_IDS.name()));
        Assert.assertFalse("零租户不得写 TENANT_ID", items.containsKey(TokenItem.TENANT_ID.name()));
        Assert.assertEquals("uuid-access", TokenHolder.get().getToken());
        // 判据来自 provider 回判，不是 id 数值
        verify(authzCheckApi).check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION);
    }

    // ==================== isPlatformAdmin：fail-closed 四腿 ====================

    @Test
    public void 判定管理员_userId为空_不碰provider且判否() {
        Assert.assertFalse(sessionService.isPlatformAdmin(null));
        verifyNoInteractions(authzCheckApiProvider, authzCheckApi);
    }

    @Test
    public void 判定管理员_provider未装配_判否() {
        when(authzCheckApiProvider.getIfAvailable()).thenReturn(null);

        Assert.assertFalse(sessionService.isPlatformAdmin(MIRROR_USER_ID));
        verifyNoInteractions(authzCheckApi);
    }

    @Test
    public void 判定管理员_客户端抛异常_判否不外漏() {
        stubAuthzClient();
        when(authzCheckApi.check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION))
                .thenThrow(new IllegalStateException("authz 不可达"));

        Assert.assertFalse(sessionService.isPlatformAdmin(MIRROR_USER_ID));
    }

    @Test
    public void 判定管理员_正常委托_scope空串加权限码admin() {
        stubAuthzClient();
        when(authzCheckApi.check(MIRROR_USER_ID, "", "admin")).thenReturn(true);

        Assert.assertTrue(sessionService.isPlatformAdmin(MIRROR_USER_ID));
        // 线上契约字面量：与 core RemoteAuthzChecker 一致（admin 短路先于 scope 解析）
        verify(authzCheckApi).check(eq(MIRROR_USER_ID), eq(""), eq("admin"));
    }

    // ==================== refresh ====================

    private static String refreshTokenString() {
        return AuthTokenUtil.getToken(
                AuthTokenUtil.buildToken("uuid-old-refresh", TokenType.WEB_REFRESH_TOKEN, CUSTOMER_NUMBER, 1200));
    }

    @Test
    public void 刷新_票不存在_拒绝且不查镜像() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(false);

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> sessionService.refresh(refreshTokenString()));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("登录已过期"));
        verifyNoInteractions(userMirrorService, userTenantMirrorService, tenantMirrorService,
                authzCheckApiProvider);
        verify(tokenService, never()).removeToken(any(TokenInfo.class));
    }

    @Test
    public void 刷新_畸形票串_拒绝而不是500() {
        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> sessionService.refresh("not-a-token"));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("登录已过期"));
        verifyNoInteractions(userMirrorService, userTenantMirrorService, tenantMirrorService,
                tokenService, authzCheckApiProvider);
    }

    @Test
    public void 刷新_非refreshToken类型一律拒() {
        // 🔴 必须把票判为「存在」：否则删掉类型闸也会被 isTokenExist=false 兜住，本腿就成了空转。
        // 用 lenient：类型闸命中时这一桩不会被调用，严格模式会判"多余桩"
        lenient().when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);

        for (TokenType forgedType : new TokenType[]{TokenType.WEB_ACCESS_TOKEN, TokenType.GUEST_TOKEN}) {
            String forged = AuthTokenUtil.getToken(
                    AuthTokenUtil.buildToken("uuid-forged-" + forgedType.name(), forgedType, CUSTOMER_NUMBER, 600));

            ServiceException ex = Assert.assertThrows(ServiceException.class,
                    () -> sessionService.refresh(forged));

            Assert.assertTrue("类型 " + forgedType + " 必须被拒", ex.getMessage().contains("登录已过期"));
            verify(tokenService, never()).buildWebAccessToken(anyString());
            verify(tokenService, never()).removeToken(any(TokenInfo.class));
            verifyNoInteractions(userMirrorService, userTenantMirrorService, tenantMirrorService,
                    authzCheckApiProvider);
        }
    }

    @Test
    public void 刷新_镜像无此人_账号状态异常且旧票不销毁() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);
        when(userMirrorService.findByCustomerNumber(CUSTOMER_NUMBER)).thenReturn(null);

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> sessionService.refresh(refreshTokenString()));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("账号状态异常"));
        verify(tokenService, never()).removeToken(any(TokenInfo.class));
        verifyNoInteractions(userTenantMirrorService, tenantMirrorService, authzCheckApiProvider);
    }

    @Test
    public void 刷新_镜像已停用_拒绝() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);
        when(userMirrorService.findByCustomerNumber(CUSTOMER_NUMBER))
                .thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "0"));

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> sessionService.refresh(refreshTokenString()));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("账号状态异常"));
        verify(tokenService, never()).removeToken(any(TokenInfo.class));
        verifyNoInteractions(userTenantMirrorService, tenantMirrorService, authzCheckApiProvider);
    }

    @Test
    public void 刷新_非管理员零有效租户_拒绝且旧票已销毁() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);
        when(userMirrorService.findByCustomerNumber(CUSTOMER_NUMBER))
                .thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "1"));
        when(userTenantMirrorService.listByUserId(MIRROR_USER_ID)).thenReturn(Collections.emptyList());
        when(tenantMirrorService.listAll()).thenReturn(Collections.singletonList(validTenant(11L)));
        stubAuthzClient();

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> sessionService.refresh(refreshTokenString()));

        Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("无有效租户归属"));
        verify(tokenService, never()).buildWebAccessToken(anyString());
        // 设计口径：旧 refresh 在镜像闸后、租户闸前即销毁（失败也得重登，不能留下可重放的票）
        verify(tokenService).removeToken(any(TokenInfo.class));
        verify(authzCheckApi).check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION);
    }

    @Test
    public void 刷新_正常腿_旧票一次性销毁并签发新双票() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);
        when(userMirrorService.findByCustomerNumber(CUSTOMER_NUMBER))
                .thenReturn(mirrorUser(USER_NAME, CUSTOMER_NUMBER, "1"));
        stubTenantMirrors();
        stubTokenIssuing();
        Map<String, Object> items = recordTokenItems();

        LoginRes res = sessionService.refresh(refreshTokenString());

        // 旧票销毁（一次性）
        verify(tokenService).removeToken(any(TokenInfo.class));
        Assert.assertNotNull(res.getAccessToken());
        Assert.assertNotNull(res.getRefreshToken());
        TokenInfo access = AuthTokenUtil.decode(res.getAccessToken());
        Assert.assertEquals(Long.valueOf(MIRROR_USER_ID), items.get(TokenItem.USER_ID.name()));
        Assert.assertEquals(Arrays.asList(11L, 12L), tokenService.getTokenValue(access)
                .get(TokenItem.TENANT_IDS.name()));
        verifyNoInteractions(authzCheckApiProvider, authzCheckApi);
    }

    @Test
    public void 刷新_平台管理员零租户_回判放行换新双票且不烤租户项() {
        when(tokenService.isTokenExist(any(TokenInfo.class))).thenReturn(true);
        when(userMirrorService.findByCustomerNumber(CUSTOMER_NUMBER))
                .thenReturn(mirrorUser(MIRROR_USER_ID, USER_NAME, CUSTOMER_NUMBER, "1"));
        when(tenantMirrorService.listAll()).thenReturn(new ArrayList<>());
        stubAuthzClient();
        when(authzCheckApi.check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION)).thenReturn(true);
        stubTokenIssuing();
        Map<String, Object> items = recordTokenItems();

        LoginRes res = sessionService.refresh(refreshTokenString());

        verify(tokenService).removeToken(any(TokenInfo.class));
        Assert.assertNotNull(res.getAccessToken());
        Assert.assertNotNull(res.getRefreshToken());
        Assert.assertEquals(Long.valueOf(MIRROR_USER_ID), items.get(TokenItem.USER_ID.name()));
        Assert.assertFalse("零租户不得写 TENANT_IDS", items.containsKey(TokenItem.TENANT_IDS.name()));
        Assert.assertFalse("零租户不得写 TENANT_ID", items.containsKey(TokenItem.TENANT_ID.name()));
        verify(authzCheckApi).check(MIRROR_USER_ID, ADMIN_SCOPE, ADMIN_PERMISSION);
    }
}
