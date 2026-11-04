package com.bidr.authorization.controller.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthPartner;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.identity.IdentitySnapshotRes;
import com.bidr.kernel.constant.CommonConst;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: IdentitySnapshotController
 * Description: 身份快照开放端点（T2.3d，provider 侧）——
 * {@code GET /open-api/v1/identity/snapshot}，{@code @Auth(AuthPartner)} 机器凭证鉴权
 * （x-api-key: Base64(appKey:appSecret)，ac_partner 注册面发放，每次调用记 partner 历史）。
 * <p>消费方＝子系统侧 identity-mirror 模块（默认关，identity.mirror.sync.enabled=true 才拉）。
 * 数据面＝全量身份拉平：用户/租户取 valid='1' 全量、归属关系取全量关系行——只用既有
 * repository 的 list 能力，不新写 SQL。</p>
 * <p>🔴 G9 边界：本端点只出「身份面」（用户/租户/归属），<b>零角色/菜单/功能码字段</b>——
 * 授权判定走 /authz 端点（T2.3b）实时回判，快照永不携带授权语义。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Api(tags = "T2.3d open-api - 身份快照")
@RestController
@RequestMapping(path = "/open-api/v1/identity")
@RequiredArgsConstructor
@Auth(AuthPartner.class)
public class IdentitySnapshotController {

    private final AcUserService acUserService;
    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;

    /**
     * 全量身份快照（用户/租户/归属关系＋watermark）。
     * <p>watermark＝生成时刻 epoch millis（每答必新，消费方只作水位锚点不作增量裁剪）。</p>
     */
    @ApiOperation(value = "身份快照", notes = "机器凭证鉴权（x-api-key: Base64(appKey:appSecret)）；"
            + "返回身份面全量，不含任何角色/菜单/功能码字段")
    @GetMapping(value = "/snapshot")
    public IdentitySnapshotRes snapshot() {
        IdentitySnapshotRes res = new IdentitySnapshotRes();
        res.setWatermark(System.currentTimeMillis());

        LambdaQueryWrapper<AcUser> userWrapper = acUserService.getQueryWrapper()
                .eq(AcUser::getValid, CommonConst.YES);
        for (AcUser user : acUserService.select(userWrapper)) {
            IdentitySnapshotRes.UserItem item = new IdentitySnapshotRes.UserItem();
            item.setId(user.getUserId());
            item.setCustomerNumber(user.getCustomerNumber());
            item.setUserName(user.getUserName());
            item.setName(user.getName());
            res.getUsers().add(item);
        }

        for (AcTenant tenant : acTenantService.listValid()) {
            IdentitySnapshotRes.TenantItem item = new IdentitySnapshotRes.TenantItem();
            item.setId(tenant.getId());
            item.setTenantCode(tenant.getCode());
            item.setTenantName(tenant.getName());
            res.getTenants().add(item);
        }

        List<AcUserTenant> relations = acUserTenantService.select();
        for (AcUserTenant relation : relations) {
            IdentitySnapshotRes.UserTenantItem item = new IdentitySnapshotRes.UserTenantItem();
            item.setUserId(relation.getUserId());
            item.setTenantId(relation.getTenantId());
            res.getUserTenants().add(item);
        }
        return res;
    }
}
