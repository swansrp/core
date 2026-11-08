package com.bidr.feishu.bridge;

import com.bidr.authorization.identity.BridgeUserPrincipal;
import com.bidr.authorization.identity.IdentityLoginCmd;
import com.bidr.authorization.identity.OrgSyncResult;
import com.bidr.kernel.exception.ServiceException;
import com.lark.oapi.Client;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenResp;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenRespBody;
import com.lark.oapi.service.contact.v3.model.AvatarInfo;
import com.lark.oapi.service.contact.v3.model.Department;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.lark.oapi.service.contact.v3.model.GetUserRespBody;
import com.lark.oapi.service.contact.v3.model.User;
import com.lark.oapi.service.contact.v3.model.UserStatus;
import com.bidr.feishu.config.FeishuProperties;
import com.bidr.feishu.service.contact.FeishuDepartmentService;
import com.bidr.feishu.service.contact.FeishuUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Title: FeishuIdentityBridgeTest
 * Description: 飞书身份桥离线单测（T1.1 棒5）——login 验票换身份（code→open_id→user_id→principal
 * 映射、验票失败异常、user_id 缺席回退 open_id）、syncOrg 拉数映射（根部门行/部门行/用户行/
 * 状态二值/手机号归一），纯 Mockito 不碰内核表（M-5"桥不写内核表"的测试面印证：本测试类无任何
 * 仓库层依赖）。
 * <p>🔴 JUnit 5（jupiter）形态：本模块 surefire＝junit-platform provider 且 classpath 无 vintage
 * 引擎，JUnit 4 风格的测试类会被静默跳过（pack-bidr 棒4 实证教训，见该模块 pom 注释）。
 * Client 用 Mockito deep stubs（inline mock maker 可透 final 链）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class FeishuIdentityBridgeTest {

    private Client feishuClient;
    private FeishuDepartmentService feishuDepartmentService;
    private FeishuUserService feishuUserService;
    private FeishuIdentityBridge bridge;

    @BeforeEach
    public void setUp() {
        feishuClient = Mockito.mock(Client.class, Mockito.RETURNS_DEEP_STUBS);
        feishuDepartmentService = Mockito.mock(FeishuDepartmentService.class);
        feishuUserService = Mockito.mock(FeishuUserService.class);
        bridge = new FeishuIdentityBridge(new FeishuProperties(), feishuClient,
                feishuDepartmentService, feishuUserService);
    }

    private void stubAuthOk(String openId, String name, String mobile, String email, String avatarUrl) throws Exception {
        CreateAccessTokenResp resp = new CreateAccessTokenResp();
        resp.setCode(0);
        CreateAccessTokenRespBody body = new CreateAccessTokenRespBody();
        body.setOpenId(openId);
        body.setName(name);
        body.setMobile(mobile);
        body.setEmail(email);
        body.setAvatarUrl(avatarUrl);
        resp.setData(body);
        Mockito.when(feishuClient.authen().v1().accessToken()
                .create(Mockito.any(CreateAccessTokenReq.class))).thenReturn(resp);
    }

    private void stubContactUserLookup(String openId, String userId) throws Exception {
        GetUserResp resp = new GetUserResp();
        resp.setCode(0);
        GetUserRespBody body = new GetUserRespBody();
        if (userId != null) {
            User user = new User();
            user.setUserId(userId);
            body.setUser(user);
        }
        resp.setData(body);
        Mockito.when(feishuClient.contact().v3().user().get(Mockito.any(GetUserReq.class))).thenReturn(resp);
    }

    /** authorizeUrl：state 编码透传＋空态回退 pan 标识（原 getAuthorizeUrl 契约保持） */
    @Test
    public void authorizeUrlEncodesStateAndFallsBackToPan() throws Exception {
        FeishuProperties props = new FeishuProperties();
        props.setAppId("cli_x");
        props.setRedirectUri("http://host/app/feishu/callback");
        FeishuIdentityBridge bridgeWithProps = new FeishuIdentityBridge(props, feishuClient,
                feishuDepartmentService, feishuUserService);
        String withState = bridgeWithProps.authorizeUrl("/pan?folderId=3");
        assertTrue(withState.startsWith("https://open.feishu.cn/open-apis/authen/v1/authorize?"));
        assertTrue(withState.contains("app_id=cli_x"));
        assertTrue(withState.contains("redirect_uri=http%3A%2F%2Fhost%2Fapp%2Ffeishu%2Fcallback"));
        assertTrue(withState.contains("state=%2Fpan%3FfolderId%3D3"));
        assertEquals("pan", extractParam(bridgeWithProps.authorizeUrl(null), "state"));
    }

    /** login 正例：code→open_id→user_id 对齐键；principal 要素与 rawClaims 标准键断言 */
    @Test
    public void loginMapsPrincipalWithUserIdAsIdentityKey() throws Exception {
        stubAuthOk("ou_abc123", "张三", "+8613821823226", "zs@example.com", "http://avatar/origin.png");
        stubContactUserLookup("ou_abc123", "zhangsan");

        IdentityLoginCmd cmd = new IdentityLoginCmd();
        cmd.code = "auth-code-1";
        BridgeUserPrincipal principal = bridge.login(cmd);

        assertNotNull(principal);
        assertEquals("zhangsan", principal.externalId);
        assertEquals("zhangsan", principal.username);
        assertEquals("张三", principal.displayName);
        assertEquals("http://avatar/origin.png", principal.avatar);
        assertEquals("ou_abc123", principal.rawClaims.get("openId"));
        assertEquals("zs@example.com", principal.rawClaims.get("email"));
        // rawClaims 原样：mobile 不做 +86 归一（归一是入库口径，随落库半边退役）
        assertEquals("+8613821823226", principal.rawClaims.get("mobile"));
    }

    /** login 回退腿：飞书后台未分配 user_id（或无字段权限）→ 对齐键回退 open_id */
    @Test
    public void loginFallsBackToOpenIdWhenUserIdAbsent() throws Exception {
        stubAuthOk("ou_fallback", "李四", null, null, null);
        stubContactUserLookup("ou_fallback", null);

        IdentityLoginCmd cmd = new IdentityLoginCmd();
        cmd.code = "auth-code-2";
        BridgeUserPrincipal principal = bridge.login(cmd);

        assertEquals("ou_fallback", principal.username);
        assertEquals("ou_fallback", principal.externalId);
        assertEquals("李四", principal.displayName);
        assertNull(principal.rawClaims.get("email"));
    }

    /** 验票失败腿①：票据空白（三态全空）→ 桥侧运行时异常（core SPI 契约：不定义受检异常） */
    @Test
    public void loginRejectsBlankCredential() throws Exception {
        try {
            bridge.login(new IdentityLoginCmd());
            fail("空白票据应拒绝");
        } catch (ServiceException expected) {
            assertTrue(expected.getMessage().contains("飞书授权码不能为空"));
        }
    }

    /** 验票失败腿②：飞书侧拒绝（SDK 应答非 0）→ ServiceException"飞书授权失败" */
    @Test
    public void loginThrowsWhenFeishuAuthFails() throws Exception {
        CreateAccessTokenResp resp = new CreateAccessTokenResp();
        resp.setCode(99991663);
        resp.setMsg("invalid code");
        Mockito.when(feishuClient.authen().v1().accessToken()
                .create(Mockito.any(CreateAccessTokenReq.class))).thenReturn(resp);

        IdentityLoginCmd cmd = new IdentityLoginCmd();
        cmd.code = "bad-code";
        try {
            bridge.login(cmd);
            fail("飞书拒绝应抛验票异常");
        } catch (ServiceException expected) {
            assertTrue(expected.getMessage().contains("飞书授权失败"));
        }
    }

    /** syncOrg 部门腿：根部门行（0/飞书组织/showOrder 0）＋飞书部门行（父为根挂 "0"） */
    @Test
    public void syncOrgBuildsRootAndDeptRows() throws Exception {
        Department dept = new Department();
        dept.setOpenDepartmentId("od_100");
        dept.setParentDepartmentId("0");
        dept.setName("研发部");
        dept.setLeaderUserId("u_leader");
        Mockito.when(feishuDepartmentService.listAll("0")).thenReturn(Collections.singletonList(dept));
        Mockito.when(feishuUserService.listByDepartments(Mockito.anyList()))
                .thenReturn(Collections.emptyList());

        OrgSyncResult result = bridge.syncOrg();

        assertNotNull(result.syncedAt);
        assertEquals(2, result.depts.size());
        OrgSyncResult.DeptRow root = result.depts.get(0);
        assertEquals("0", root.externalId);
        assertEquals("飞书组织", root.name);
        assertEquals(Integer.valueOf(0), root.showOrder);
        assertNull(root.parentExternalId);

        OrgSyncResult.DeptRow row = result.depts.get(1);
        assertEquals("od_100", row.externalId);
        assertEquals("研发部", row.name);
        assertEquals("0", row.parentExternalId);
        assertEquals("u_leader", row.leader);
    }

    /** syncOrg 用户腿：状态二值（在职→active/离职→frozen）、+86 归一、头像、displayName 兜底 */
    @Test
    public void syncOrgMapsUserRowsWithStatusAndNormalization() throws Exception {
        Mockito.when(feishuDepartmentService.listAll("0")).thenReturn(Collections.emptyList());

        User active = new User();
        active.setUserId("zhangsan");
        active.setOpenId("ou_1");
        active.setName("张三");
        active.setMobile("+8613821823226");
        active.setEmail("zs@example.com");
        active.setDepartmentIds(new String[]{"od_100", "od_200"});
        UserStatus activeStatus = new UserStatus();
        activeStatus.setIsActivated(Boolean.TRUE);
        activeStatus.setIsResigned(Boolean.FALSE);
        activeStatus.setIsExited(Boolean.FALSE);
        active.setStatus(activeStatus);
        AvatarInfo avatar = new AvatarInfo();
        avatar.setAvatarOrigin("http://avatar/zs.png");
        active.setAvatar(avatar);

        User resigned = new User();
        resigned.setUserId(null);
        resigned.setOpenId("ou_2");
        resigned.setName(null);
        UserStatus resignedStatus = new UserStatus();
        resignedStatus.setIsActivated(Boolean.TRUE);
        resignedStatus.setIsResigned(Boolean.TRUE);
        resignedStatus.setIsExited(Boolean.FALSE);
        resigned.setStatus(resignedStatus);

        Mockito.when(feishuUserService.listByDepartments(Mockito.anyList()))
                .thenReturn(java.util.Arrays.asList(active, resigned));

        List<OrgSyncResult.UserRow> users = bridge.syncOrg().users;
        assertEquals(2, users.size());

        OrgSyncResult.UserRow activeRow = users.get(0);
        assertEquals("zhangsan", activeRow.externalId);
        assertEquals("zhangsan", activeRow.username);
        assertEquals("张三", activeRow.displayName);
        assertEquals("od_100", activeRow.deptExternalId);
        assertEquals("active", activeRow.status);
        assertEquals("zs@example.com", activeRow.email);
        assertEquals("13821823226", activeRow.mobile);
        assertEquals("http://avatar/zs.png", activeRow.avatar);

        OrgSyncResult.UserRow resignedRow = users.get(1);
        // 身份键回退 open_id＋displayName NOT NULL 护栏回退 open_id（原链同口径）
        assertEquals("ou_2", resignedRow.externalId);
        assertEquals("ou_2", resignedRow.displayName);
        // 离职收敛 frozen（保住原链"离职不建号"漏斗行为，见桥类注释状态映射口径）
        assertEquals("frozen", resignedRow.status);
    }

    private static String extractParam(String url, String name) {
        for (String pair : url.split("\\?|&")) {
            if (pair.startsWith(name + "=")) {
                return pair.substring(name.length() + 1);
            }
        }
        return null;
    }
}
