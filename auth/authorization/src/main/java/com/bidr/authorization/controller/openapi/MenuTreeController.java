package com.bidr.authorization.controller.openapi;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthPartner;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.MenuService;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: MenuTreeController
 * Description: 子系统菜单树开放端点（方案B 棒3 §4.1）——
 * {@code GET /open-api/v1/menu/tree?user={customerNumber}}，{@code @Auth(AuthPartner)} 机器凭证鉴权
 * （x-api-key: Base64(appKey:appSecret)）。
 * <p>归属应用由 partner 身份解析、<b>不接受请求参数指定</b>（防调用方越权点名他 app）：
 * {@code PARTNER_ID}（AuthPartner 烤入）→ {@code AcSubsystemService.getByPartnerId} → {@code app_code}；
 * 查不到回落烤入的 {@code APP_KEY} 当 app_code（存量 partner 未注册 ac_subsystem 的过渡腿）；
 * 解析结果仍空 ⇒ 业务拒绝（fail-closed，<b>不得回落平台自身</b>）。命中多条 partner 绑定
 * （uk_partner_id 应已防住，防御腿，MP getOne 抛 MybatisPlusException）同样 fail-closed 拒。
 * user→{@link MenuService#getMenuTree(String, String)} 同核（用户不存在沿用既有业务拒绝），
 * 菜单行按解析出的 app_code 聚合前收窄。</p>
 *
 * @author Sharp
 * @since 2026/11/06
 */
@Api(tags = "open-api - 子系统菜单树")
@RestController
@RequestMapping(path = "/open-api/v1/menu")
@RequiredArgsConstructor
@Auth(AuthPartner.class)
public class MenuTreeController {

    private final AcSubsystemService acSubsystemService;
    private final MenuService menuService;

    /**
     * 子系统菜单树。直返 POJO，全局 Response 信封包装（M-4）；clientType 走 ClientTypeHolder
     * 缺省 WEB（开放口调用方不带 CLIENT_TYPE 头也安全，M-7）。
     */
    @ApiOperation(value = "子系统菜单树", notes = "机器凭证鉴权（x-api-key: Base64(appKey:appSecret)）；"
            + "归属应用由 partner 身份解析（ac_subsystem.partner_id → app_code，过渡期回落 appKey），不接受请求参数指定")
    @GetMapping(value = "/tree")
    public List<MenuTreeRes> menuTree(@RequestParam("user") String customerNumber) {
        String appCode = resolveAppCode();
        return menuService.getMenuTree(customerNumber, appCode);
    }

    /**
     * partner → 子系统 → app_code 解析链（包内可见＝给离线测试直断言，不为测试拓宽公开 API）。
     * <p>解析顺序：①{@code TokenItem.PARTNER_ID} 上下文值（Number/String 皆容）→
     * {@code getByPartnerId} 命中取 {@code appCode}；②①未得（无 pid／无子系统行）→ 过渡腿：
     * {@code TokenItem.APP_KEY} 上下文值当 app_code；③仍空 → 业务拒绝（fail-closed）。</p>
     */
    String resolveAppCode() {
        String resolved = null;
        Long partnerId = readPartnerId();
        if (partnerId != null) {
            AcSubsystem subsystem;
            try {
                subsystem = acSubsystemService.getByPartnerId(partnerId);
            } catch (MybatisPlusException e) {
                // uk_partner_id 应已防住；命中多条＝数据态破坏，fail-closed 拒（不回落平台自身）
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                        "子系统归属解析异常（partner 绑定多条子系统），请联系平台管理员核对 ac_subsystem");
            }
            if (subsystem != null) {
                resolved = subsystem.getAppCode();
            }
        }
        if (FuncUtil.isEmpty(resolved)) {
            Object appKey = AccountContext.getExtraData(TokenItem.APP_KEY.name());
            if (appKey != null) {
                resolved = String.valueOf(appKey);
            }
        }
        if (FuncUtil.isEmpty(resolved)) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                    "无法解析调用方归属应用（partner 未绑定子系统且无 appKey），拒绝返回菜单");
        }
        return resolved;
    }

    /** PARTNER_ID 上下文值宽松转 Long（AuthPartner 烤入的是 Long；String 数字亦容，其余视为无） */
    private static Long readPartnerId() {
        Object value = AccountContext.getExtraData(TokenItem.PARTNER_ID.name());
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && FuncUtil.isNotEmpty((String) value)) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
