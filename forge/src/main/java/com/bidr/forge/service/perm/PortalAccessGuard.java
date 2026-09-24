package com.bidr.forge.service.perm;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.service.perm.PortalColumnPolicyService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Title: PortalAccessGuard
 * Description: 低代码 Portal 的整体粒度访问守卫——判断当前用户能否使用某个 Portal
 * <p>
 * 与 {@link ColumnPermGuard} 同一套 {@code ac_resource_perm} 通用资源权限表，只是资源粒度不同：
 * 本守卫的资源是 Portal 本身（{@code resourceType=sys_portal}，{@code resourceId=} Portal 逻辑名），
 * 决定「这个数据页面谁能打开」；列守卫的资源是列配置行，决定「页面里哪几列谁能看」。
 * </p>
 * <p>
 * 为何按 Portal 逻辑名而非 dataset/matrix 主键：dataset 没有独立存在场景，真正对外提供的是
 * 基于它生成的 Portal（dashboard / portalTable）；且 {@code sys_portal} 唯一键为 {@code role_id_name}，
 * 同一 Portal 在不同角色下是不同的行、不同物理 id，只有 {@code name} 跨角色稳定，故以其为资源标识。
 * </p>
 *
 * @author Sharp
 * @since 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalAccessGuard {

    /**
     * 整体粒度授权的资源类型：低代码 Portal
     */
    public static final String RESOURCE_TYPE_PORTAL = "sys_portal";

    /**
     * 列级（原表列）授权的资源类型：资源标识仍为 Portal 逻辑名，
     * 每主体行的 extra_data 携带该主体被隐藏的列 token（{@code {"columns":["c:prop",…]}}）。
     * 与整体/行级同用 sys_portal 主键之外的独立 resourceType，避免全量覆盖保存相互污染。
     * 常量正源在 admin {@link PortalColumnPolicyService}（列权限语义归口），此处编译期内联引用。
     */
    public static final String RESOURCE_TYPE_PORTAL_COLUMN = PortalColumnPolicyService.RESOURCE_TYPE_PORTAL_COLUMN;

    /**
     * 列级（透视列）授权的资源类型：透视父列 itemValue 与度量 field 的隐藏集合，
     * token 形如 {@code p:itemValue} / {@code m:field}，{@code resourceId=} Portal 逻辑名。
     * 常量正源在 admin {@link PortalColumnPolicyService}（列权限语义归口），此处编译期内联引用。
     */
    public static final String RESOURCE_TYPE_PORTAL_PIVOT = PortalColumnPolicyService.RESOURCE_TYPE_PORTAL_PIVOT;

    private final ResourcePermFilterService resourcePermFilterService;

    /**
     * 校验当前用户可使用该 Portal，无权则抛出权限异常
     * <p>
     * 三类情形不拦截，口径与列守卫一致：
     * <ul>
     *   <li>线程上下文无登录用户（定时任务、内部调用、ChatBI 后台取数）——此类入口不由本守卫保护；</li>
     *   <li>Portal 非低代码生成的 DATASET/MATRIX 数据模式——{@code @AdminPortal} 代码注册的 Portal
     *       无 {@code data_mode}，天然不纳入整体粒度授权；</li>
     *   <li>Portal 名称为空——无资源标识可比对。</li>
     * </ul>
     * 「该 Portal 一条授权记录都没配」由 {@link ResourcePermFilterService} 判为不限制，即默认开放。
     * </p>
     *
     * @param portalName Portal 名称（逻辑名，同时作为资源标识）
     * @param portal     已按名称/角色解析出的 Portal 配置
     */
    public void assertPortalAccessible(String portalName, SysPortal portal) {
        if (FuncUtil.isEmpty(portal) || FuncUtil.isEmpty(portalName)) {
            return;
        }
        if (FuncUtil.isEmpty(AccountContext.getOperator())) {
            log.debug("[数据权限] 线程上下文无登录用户，跳过 Portal 访问校验：portal={}", portalName);
            return;
        }
        // 仅保护低代码平台生成的 Portal（DATASET/MATRIX），代码注册的 @AdminPortal 不参与
        if (!isLowCodePortal(portal.getDataMode())) {
            return;
        }
        if (!resourcePermFilterService.hasPermission(RESOURCE_TYPE_PORTAL, portalName)) {
            log.warn("[数据权限] 用户 {} 无权访问 portal={}（resourceType={}, resourceId={}）",
                    AccountContext.getOperator(), portalName, RESOURCE_TYPE_PORTAL, portalName);
            // 白名单语义的边界效应：portal 一旦配了任意授权行，未命中的主体即被拒——
            // 文案面向用户给出下一步指引（显示名+找管理员配哪个维度），内部 portalName 仅留日志
            String title = FuncUtil.isNotEmpty(portal.getDisplayName()) ? portal.getDisplayName() : portalName;
            Validator.assertException(ErrCodeSys.SYS_PERMIT_ERROR,
                    "您暂无数据页面「" + title + "」的访问权限，请联系管理员在权限配置中为您所属的角色/部门/用户组授权后重试");
        }
    }

    /**
     * 是否为低代码平台生成的 Portal 数据模式（DATASET/MATRIX）
     */
    private boolean isLowCodePortal(String dataMode) {
        return PortalDataMode.DATASET.name().equalsIgnoreCase(dataMode)
                || PortalDataMode.MATRIX.name().equalsIgnoreCase(dataMode);
    }
}
