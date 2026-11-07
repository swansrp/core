package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.mapper.AcSubsystemMapper;
import com.bidr.authorization.vo.subsystem.QuerySubsystemReq;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.bidr.authorization.constants.err.AccountErrCode.AC_PARTNER_NOT_EXISTED;
import static com.bidr.authorization.constants.err.AccountErrCode.AC_SUBSYSTEM_HAS_MENU;

/**
 * Title: AcSubsystemService
 * Description: 子系统注册表仓储（方案B 棒2）。
 * <p>🔴 类名硬约束：框架 {@code BaseAdminController.getRepo()} 按
 * {@code Introspector.decapitalize(实体简名)+"Service"} 取 bean，本类必须叫此名（同 {@link AcSsoRedirectService}）。</p>
 * <p>partner_id＝裸 Long 弱引用（无 FK/实体关联，照 AcSsoRedirect 模式，M-6）：
 * 保存时由 {@link #validatePartnerBound} 兜底，删除侧两向守卫＝本类 {@link #deleteWithGuard}
 * （菜单引用拒删）＋ AdminPartnerController.beforeDelete（子系统引用拒删渠道）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class AcSubsystemService extends BaseSqlRepo<AcSubsystemMapper, AcSubsystem> {

    private final AcPartnerService acPartnerService;
    private final AcMenuService acMenuService;

    /**
     * 保存口（插腿）：partner_id 非空时对应 ac_partner 必须存在——弱引用无 FK，引用完整性在仓储层兜。
     * 更新腿的同一校验走 {@link #validatePartnerBound}（controller beforeUpdate 钩子调用）。
     */
    @Override
    public boolean insert(AcSubsystem entity) {
        validatePartnerBound(entity == null ? null : entity.getPartnerId());
        return super.insert(entity);
    }

    /**
     * partner 绑定校验：partner_id 非空 ⇒ ac_partner 必须有该 id 行，否则业务异常（AC_PARTNER_NOT_EXISTED）。
     * <p>null＝未绑定（uk_partner_id 多 NULL 合法），直接放行。</p>
     */
    public void validatePartnerBound(Long partnerId) {
        if (partnerId == null) {
            return;
        }
        Validator.assertTrue(acPartnerService.existedById(partnerId), AC_PARTNER_NOT_EXISTED);
    }

    /**
     * 按 partner 取子系统（uk_partner_id 语义下至多一行）。
     *
     * @param partnerId ac_partner 口径 id（null ⇒ 返回 null，不抛）
     * @return 命中行；未绑定/无行 ⇒ null
     */
    public AcSubsystem getByPartnerId(Long partnerId) {
        if (partnerId == null) {
            return null;
        }
        return super.selectOne(partnerIdWrapper(partnerId));
    }

    /**
     * {@code partner_id} 等值条件的共享构造（{@link #getByPartnerId} 判据只此一处）。
     * <p>包内可见＝给同包离线测试直接断言 wrapper 形态（同 {@link AcSsoRedirectService#validWrapper}
     * 纪律），不为测试拓宽公开 API。</p>
     */
    LambdaQueryWrapper<AcSubsystem> partnerIdWrapper(Long partnerId) {
        LambdaQueryWrapper<AcSubsystem> wrapper = super.getQueryWrapper();
        wrapper.eq(AcSubsystem::getPartnerId, partnerId);
        return wrapper;
    }

    /**
     * 管理面分页查询：app_code 等值、name 模糊、status 等值（条件空则不下发）。
     */
    public Page<AcSubsystem> querySubsystem(QuerySubsystemReq req) {
        LambdaQueryWrapper<AcSubsystem> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(req.getAppCode()), AcSubsystem::getAppCode, req.getAppCode());
        wrapper.like(FuncUtil.isNotEmpty(req.getName()), AcSubsystem::getName, req.getName());
        wrapper.eq(FuncUtil.isNotEmpty(req.getStatus()), AcSubsystem::getStatus, req.getStatus());
        return super.select(wrapper, req);
    }

    /**
     * 带守卫删除：ac_menu 有 app_code 命中行则拒（菜单被引用语义，AC_SUBSYSTEM_HAS_MENU），
     * 守卫过才物理删行。
     *
     * @param appCode 应用编码（空 ⇒ 拒，不触库）
     * @return 删除是否成功
     */
    public boolean deleteWithGuard(String appCode) {
        if (FuncUtil.isEmpty(appCode)) {
            return false;
        }
        Validator.assertFalse(acMenuService.existed(menuAppCodeWrapper(appCode)), AC_SUBSYSTEM_HAS_MENU);
        return deleteById(appCode);
    }

    /**
     * 删除守卫的 {@code ac_menu.app_code} 等值条件构造（守卫判据只此一处）。
     * <p>🔴 删掉 {@code eq(getAppCode, ...)} 会让守卫退化成「ac_menu 非空即拒删」——
     * {@code AcSubsystemServiceTest} 的真针盯着这一条（同 {@link AcSsoRedirectService#validWrapper} 纪律）。</p>
     */
    LambdaQueryWrapper<AcMenu> menuAppCodeWrapper(String appCode) {
        LambdaQueryWrapper<AcMenu> wrapper = acMenuService.getQueryWrapper();
        wrapper.eq(AcMenu::getAppCode, appCode);
        return wrapper;
    }
}
