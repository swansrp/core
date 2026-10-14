package com.bidr.admin.manage.sso.service;

import com.bidr.admin.manage.sso.vo.AcSsoRedirectVO;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Title: AcSsoRedirectPortalService
 * Description: SSO 回调白名单登记面（框架通用 Portal，零手写 CRUD）。
 * <p>查重判据＝{@code (partner_id, redirect_uri)} 组合 ∧ valid='1' 已存在即拒；
 * 🔴 拒绝走 {@code ServiceException}（Validator.assertTrue 即抛）。
 * 撤销＝软删由框架接住（{@code AdminBaseDeleteControllerInf}：实体有 valid 字段即
 * {@code getRepo().disable(entity)} 置 valid='0'），🔴 本类不覆写删除口。</p>
 * <p>🔴 覆写点＝{@link #beforeAdd}／{@link #beforeUpdate} 而不是 {@code adminBeforeX}：
 * 控制器按 {@code isAdmin()} 二选一分派（{@code AdminBaseInsertControllerInf} /
 * {@code AdminBaseUpdateControllerInf}），而基类 {@code PortalCommonService.adminBeforeX} 的缺省实现就是转调
 * {@code beforeX}（机制取证＝M-27）⇒ 覆写 {@code beforeX} 两支通吃；若只覆写 {@code adminBeforeX}，
 * 非 admin 调用者（无上下文场景下 {@code isAdmin()} 为 false，如 Excel 导入路）两支都不跑，本类的锁形同不存在。</p>
 * <p>🔴 编辑口（{@code /update}、{@code /update/list}、Excel {@code /import/update}）由
 * {@code BaseAdminController} 继承即存在、不写就可达，且钩子拿到的是<b>已合入请求值的实体</b>
 * （{@code AdminBaseUpdateControllerInf.updateEntity} 先 {@code selectById} 取原行再 merge，非 strict 支把
 * merge 后的实体送进钩子；JsonNode／strict 支的 merge 源是原始 JSON body），
 * 三条 HTTP 路＋Excel 路都会把请求里的 {@code partnerId}/{@code redirectUri}/{@code valid} 合进实体后
 * 直接 {@code updateById}、<b>全程不过 add 钩子</b> ⇒ 本类的 {@link #beforeUpdate} 是那一步的闸：
 * 锁归属与有效性、改址时重跑查重。🔴 只从 VO 删列挡不住——strict 形态的 merge 源是原始 JSON body。
 * 副作用如实登记＝Excel {@code /import/update} 走 {@code BasePortalService.parseCommonEntity} 时<b>不回查库里原行</b>，
 * 表里没有的列一律 null ⇒ 入口断言恒拒＝本表导入面实质不可用（方向安全，拒因文案非"导入不支持"）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Service
@RequiredArgsConstructor
public class AcSsoRedirectPortalService extends BasePortalService<AcSsoRedirect, AcSsoRedirectVO> {

    /**
     * redirect_uri 列宽（与 createDDL 的 varchar(500) 等值）。
     * <p>🔴 必须在入口挡：VO 无校验注解、controller 无 {@code @Valid}、实体上的 {@code @Size} 不在
     * HTTP 链执行；若目标库 sql_mode 非严格，超长串会被<b>静默截断</b>入库 ⇒ 登记值 ≠ 出票比对原串
     * ⇒ 该 partner 永久拒票，而管理台列表看着"登记正确"。</p>
     */
    private static final int REDIRECT_URI_MAX_LENGTH = 500;

    private final AcSsoRedirectService acSsoRedirectService;

    @Override
    public void beforeAdd(AcSsoRedirect entity) {
        super.beforeAdd(entity);
        assertRegisterFields(entity);
        Validator.assertTrue(!acSsoRedirectService.existsValid(entity.getPartnerId(), entity.getRedirectUri()),
                ErrCodeSys.SYS_ERR_MSG, "该 partner 下已登记同一回调地址（redirect_uri），拒绝重复登记");
    }

    /**
     * 编辑口三条锁（判据只此一处，三条 HTTP 更新路＋Excel 路都过这里）：
     * ① 归属不可改——跨家改写会让 B 家凭空多一条 create_by/remark 全是 A 家语境的白名单行；
     * ② 有效性不可改——撤销只许走 {@code /delete}（软删）、复活只许走新增，
     * 🔴 否则"撤销漏杀"成立：老撤销行被编辑翻回 '1'，同组出现两行有效，点删一行另一行仍被
     * {@code existsValid} 命中＝管理员以为撤了其实没撤；
     * ③ 改地址时重跑查重（排除自身 id，否则"保存但没动 redirect_uri"会被判成重复）。
     * <p>🔴 original 必须自己回查：钩子入参是<b>已合入请求值</b>的实体，框架不给"库里原行"。</p>
     */
    @Override
    public void beforeUpdate(AcSsoRedirect entity) {
        super.beforeUpdate(entity);
        assertRegisterFields(entity);
        Validator.assertNotNull(entity.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        AcSsoRedirect original = acSsoRedirectService.selectById(entity.getId());
        Validator.assertNotNull(original, ErrCodeSys.SYS_ERR_MSG, "待修改的SSO回调登记不存在，拒绝修改");
        Validator.assertTrue(Objects.equals(original.getPartnerId(), entity.getPartnerId()),
                ErrCodeSys.SYS_ERR_MSG, "归属应用（partner_id）不允许修改：要换归属请撤销本条后在对应应用下重新登记");
        Validator.assertTrue(Objects.equals(original.getValid(), entity.getValid()),
                ErrCodeSys.SYS_ERR_MSG, "有效性（valid）不允许通过编辑修改：撤销请用删除，重新登记请新增一条");
        if (!Objects.equals(original.getRedirectUri(), entity.getRedirectUri())) {
            Validator.assertTrue(!acSsoRedirectService.existsValidExcludingId(
                            entity.getPartnerId(), entity.getRedirectUri(), entity.getId()),
                    ErrCodeSys.SYS_ERR_MSG, "该 partner 下已登记同一回调地址（redirect_uri），拒绝重复登记");
        }
    }

    /**
     * 入口非空、列宽与首尾空白断言（add／update 共用）：坏输入不留给 DB 兜底。
     * <p>🔴 第三条不是洁癖：{@code Validator.assertNotBlank} 只拦"全空白串"，不拦 {@code "https://h/cb "} 这种尾空格；
     * 而 {@code redirect_uri} 列的 {@code utf8mb4_bin} 在 MySQL 8 属 <b>PAD SPACE</b> 排序规则 ⇒
     * 等值比较忽略尾部空格（MySQL 8 口径，本批 A3 真机探针 {@code SELECT 'a ' = 'a' COLLATE utf8mb4_bin} 复核），于是"登记带尾空格的地址"与"请求裸地址"会判成相等，
     * 而出票响应回填的是<b>请求原串</b>。判据落在登记侧＝首尾空白一律拒。</p>
     */
    private void assertRegisterFields(AcSsoRedirect entity) {
        Validator.assertNotNull(entity.getPartnerId(), ErrCodeSys.PA_PARAM_NULL, "partner_id");
        Validator.assertNotBlank(entity.getRedirectUri(), ErrCodeSys.PA_PARAM_FORMAT, "redirect_uri");
        Validator.assertTrue(entity.getRedirectUri().equals(entity.getRedirectUri().trim()),
                ErrCodeSys.PA_PARAM_FORMAT, "redirect_uri 首尾不能有空格（粘贴带入会被等值比较忽略，导致登记值与出票比对值不一致）");
        Validator.assertTrue(entity.getRedirectUri().length() <= REDIRECT_URI_MAX_LENGTH,
                ErrCodeSys.PA_PARAM_FORMAT, "redirect_uri 超长（上限 " + REDIRECT_URI_MAX_LENGTH + " 字符），拒绝登记");
    }
}
