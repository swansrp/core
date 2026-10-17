package com.bidr.admin.manage.sso.service;

import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.kernel.exception.ServiceException;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Title: AcSsoRedirectPortalServiceTest
 * Description: SSO 回调白名单登记面查重与编辑锁的离线腿（M15 棒 0b-4／0b-7／0b-8）。
 * <p>🔴 写成 TestNG（与该模块既有测试同族——JUnit/TestNG 混合 provider 下 {@code -Dtest} 会空跑）＋
 * {@code Mockito.mock()} 直接构造（mockito 经 kernel 以 compile 域传递，零 pom 改动）。</p>
 * <p>🔴 钩子调用口径（10-14 review R1）＝控制器按 {@code isAdmin()} 在
 * {@code adminBeforeX}／{@code beforeX} 之间<b>二选一</b>分派，而锁现在覆写在 {@code beforeX} 上，
 * 所以本类<b>两条都要打</b>：{@code beforeX}＝非 admin 调用者走的那支（它必须也拒），
 * {@code adminBeforeX}＝admin 那支，靠基类缺省转调接住（框架 {@code PortalCommonService} 的形状，取证＝M-27）。
 * 只测 admin 支的形态＝上一轮的敞口，勿再退回去。</p>
 * <p>登记口（add）两腿＝① 同 (partner_id, redirect_uri) 且 valid='1' 已存在 ⇒ 拒；
 * ② 查重口不命中 ⇒ 不抛（🔴 本腿只证明"add 钩子在不重复时不抛"，<b>不证明</b> {@code valid='1'}
 * 过滤本身——那是 {@code AcSsoRedirectServiceTest} 的真针，把 mock 设成 false 后本腿恒过）。</p>
 * <p>编辑口（update）腿＝锁归属／锁有效性（撤销漏杀的闸，admin 与非 admin 两支各一条）／
 * 改址撞重拒／只改备注放行；入口断言腿＝尾空格拒。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Test
public class AcSsoRedirectPortalServiceTest {

    private static final long PARTNER_ID = 9L;
    private static final long ROW_ID = 7L;
    private static final String REDIRECT_URI = "https://sub.example.com/sso/cb";
    private static final String VALID_YES = "1";
    private static final String VALID_NO = "0";

    private AcSsoRedirect entity() {
        AcSsoRedirect entity = new AcSsoRedirect();
        entity.setPartnerId(PARTNER_ID);
        entity.setRedirectUri(REDIRECT_URI);
        return entity;
    }

    /**
     * 编辑口的"当前库里那一行"：钩子拿到的是已合入请求值的实体，判锁必须回查原行，
     * 所以每条编辑腿都要先把原行 mock 出来。
     */
    private AcSsoRedirect originalRow(long partnerId, String uri, String valid) {
        AcSsoRedirect row = new AcSsoRedirect();
        row.setId(ROW_ID);
        row.setPartnerId(partnerId);
        row.setRedirectUri(uri);
        row.setValid(valid);
        return row;
    }

    private AcSsoRedirect entityToEdit(String uri) {
        AcSsoRedirect entity = new AcSsoRedirect();
        entity.setId(ROW_ID);
        entity.setPartnerId(PARTNER_ID);
        entity.setRedirectUri(uri);
        entity.setValid(VALID_YES);
        return entity;
    }

    /**
     * admin 支＝非 admin 支的缺省转调目标（本类不再覆写 {@code adminBeforeX}），
     * 故 admin 调用者走的其实是同一套锁。删掉 {@code beforeAdd} 里的 assertTrue 两支同红。
     */
    private AcSsoRedirectPortalService serviceWith(AcSsoRedirectService repository) {
        return new AcSsoRedirectPortalService(repository);
    }

    /**
     * 腿①：重复登记拒——existsValid 命中（组合等值 ∧ valid='1'）即抛 ServiceException。
     * <p>把查询退化成"只按 redirect_uri 全局查"时最后那条 verify 也会翻红。</p>
     */
    public void duplicateRegistrationRejected() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.existsValid(PARTNER_ID, REDIRECT_URI)).thenReturn(true);
        try {
            serviceWith(repository).beforeAdd(entity());
            assertTrue(false, "重复登记必须抛 ServiceException");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("拒绝重复登记"), "文案应点名拒因: " + e.getMessage());
        }
        // 查重必须按 (partner_id, redirect_uri) 组合口径入查，不得只按 URI
        Mockito.verify(repository).existsValid(PARTNER_ID, REDIRECT_URI);
    }

    /**
     * 腿②：查重口不命中时 add 钩子不得抛。
     * <p>🔴 如实降级（10-14 review F3）＝本腿<b>只</b>证明钩子在不重复时放行，
     * <b>不</b>证明"valid='0' 的旧行被过滤"：{@code existsValid} 是 mock，返回 false 时恒过。
     * valid 过滤的真针在 {@code AcSsoRedirectServiceTest}（离线直断 wrapper 三条件）。</p>
     */
    public void reRegistrationAfterRevokeAllowed() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.existsValid(PARTNER_ID, REDIRECT_URI)).thenReturn(false);
        serviceWith(repository).beforeAdd(entity());
        Mockito.verify(repository).existsValid(PARTNER_ID, REDIRECT_URI);
    }

    /**
     * 腿③（编辑锁①）：改归属拒——把行的 partner_id 从 9 改成 10 必须被挡。
     */
    public void updateChangingPartnerRejected() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.selectById(ROW_ID)).thenReturn(originalRow(PARTNER_ID, REDIRECT_URI, VALID_YES));
        AcSsoRedirect entity = entityToEdit(REDIRECT_URI);
        entity.setPartnerId(10L);
        try {
            serviceWith(repository).beforeUpdate(entity);
            assertTrue(false, "改归属必须抛 ServiceException");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("归属应用"), "文案应点名拒因: " + e.getMessage());
        }
    }

    /**
     * 腿④（编辑锁②＝撤销漏杀的闸，🔴 非 admin 分支那支）：把已撤销行（valid='0'）
     * 经编辑口翻回 '1' 必须被挡——本腿打的是 {@code beforeUpdate}，即控制器在
     * {@code isAdmin()}＝false 时实际调用的那支。
     * <p>🔴 本腿的存在理由＝10-14 review R1：锁原先只覆写在 {@code adminBeforeUpdate} 上，
     * 非 admin 调用者两支都不跑 ⇒ 敞口原样复现。若有人把锁搬回 {@code adminBeforeX}，本腿翻红。</p>
     */
    public void nonAdminBranchAlsoRejectsValidRewrite() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.selectById(ROW_ID)).thenReturn(originalRow(PARTNER_ID, REDIRECT_URI, VALID_NO));
        try {
            serviceWith(repository).beforeUpdate(entityToEdit(REDIRECT_URI));
            assertTrue(false, "非 admin 分支也必须挡住撤销漏杀改写");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("有效性"), "文案应点名拒因: " + e.getMessage());
        }
    }

    /**
     * 腿⑤（同一闸的 admin 分支）：admin 调用者走 {@code adminBeforeUpdate}，
     * 靠基类缺省转调落到 {@code beforeUpdate} ⇒ 同样必须拒（取证＝M-27）。
     * <p>本腿防的是"把 {@code beforeUpdate} 的锁挪进 {@code adminBeforeUpdate}"这种反向重构：
     * 那样 admin 支仍绿、非 admin 支失守，而只有这两条腿成对才能把两支都钉住。</p>
     */
    public void adminBranchRejectsValidRewriteViaDefaultDelegation() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.selectById(ROW_ID)).thenReturn(originalRow(PARTNER_ID, REDIRECT_URI, VALID_NO));
        try {
            serviceWith(repository).adminBeforeUpdate(entityToEdit(REDIRECT_URI));
            assertTrue(false, "admin 分支（缺省转调）也必须拒");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("有效性"), "文案应点名拒因: " + e.getMessage());
        }
    }

    /**
     * 腿⑥（编辑锁③）：改地址撞上同 partner 下另一条有效登记 ⇒ 拒，且查重必须排除自身 id。
     * <p>把 excludeId 传成 null／漏传，最后那条 verify 翻红。</p>
     */
    public void updateRedirectUriCollisionRejected() {
        String newUri = "https://sub.example.com/sso/other";
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.selectById(ROW_ID)).thenReturn(originalRow(PARTNER_ID, REDIRECT_URI, VALID_YES));
        Mockito.when(repository.existsValidExcludingId(PARTNER_ID, newUri, ROW_ID)).thenReturn(true);
        try {
            serviceWith(repository).beforeUpdate(entityToEdit(newUri));
            assertTrue(false, "改址撞已有登记必须抛 ServiceException");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("拒绝重复登记"), "文案应点名拒因: " + e.getMessage());
        }
        Mockito.verify(repository).existsValidExcludingId(PARTNER_ID, newUri, ROW_ID);
    }

    /**
     * 腿⑦（正向对照，防三条锁把正常编辑挡死）：只改备注、三个键一字未动 ⇒ 放行且不入查查重。
     * <p>若有人把锁写成"无条件拒一切 update"或漏了"地址没变就不查重"的分支，本腿翻红。</p>
     */
    public void updateRemarkOnlyAllowedWithoutDupQuery() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(repository.selectById(ROW_ID)).thenReturn(originalRow(PARTNER_ID, REDIRECT_URI, VALID_YES));
        AcSsoRedirect entity = entityToEdit(REDIRECT_URI);
        entity.setRemark("仅补备注");
        serviceWith(repository).beforeUpdate(entity);
        Mockito.verify(repository, Mockito.never())
                .existsValidExcludingId(Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong());
    }

    /**
     * 腿⑧（入口断言＝承诺面 37 第③支）：redirect_uri 首尾带空白 ⇒ 拒登记。
     * <p>理由不是洁癖＝列 {@code utf8mb4_bin} 属 PAD SPACE，尾空格不参与等值比较，而出票响应回填
     * 请求原串 ⇒ "登记 {@code ...cb }"与"请求 {@code ...cb}"会判成相等。删掉 {@code assertRegisterFields}
     * 的 trim 那条 assertTrue，本腿翻红。</p>
     */
    public void registrationWithTrailingSpaceInUriRejected() {
        AcSsoRedirectService repository = Mockito.mock(AcSsoRedirectService.class);
        AcSsoRedirect entity = entity();
        entity.setRedirectUri(REDIRECT_URI + " ");
        try {
            serviceWith(repository).beforeAdd(entity);
            assertTrue(false, "首尾空白的 redirect_uri 必须拒登记");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("首尾不能有空格"), "文案应点名拒因: " + e.getMessage());
        }
        // 断言先于查重：坏输入不该去打库
        Mockito.verify(repository, Mockito.never()).existsValid(Mockito.anyLong(), Mockito.anyString());
    }
}
