package com.bidr.admin.service.perm;

import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: PortalColumnPolicyService
 * Description: Portal 列级权限策略装配正源——求当前用户在该 Portal 上被隐藏的列 token 集合
 * <p>
 * 本类为列权限解析的<b>唯一正源</b>，落位 admin 的原因：剥离消费方 {@code AdminPortalController#/config}
 * （运行态视图列配置）在 admin，而 forge 不能反向依赖 admin 之上的模块——正源下沉后 forge 直接引用本类。
 * 与 {@code forge.PortalAccessGuard} 同数据源：列隐藏信息挂在 {@code ac_resource_perm} 命中当前用户的
 * 授权行的 {@code extra_data} 上，结构 {@code {"columns":["c:prop",…]}}。资源按 {@code resourceType} 区分两类列：
 * <ul>
 *   <li>{@link #RESOURCE_TYPE_PORTAL_COLUMN}（原表列）：token {@code c:property}，
 *       同时驱动 table 显示列与 pivot 行维度（group by 即原表列）；</li>
 *   <li>{@link #RESOURCE_TYPE_PORTAL_PIVOT}（透视列）：token {@code p:itemValue} / {@code m:field}。</li>
 * </ul>
 * 采用<b>黑名单</b>语义：token 表示「该主体被隐藏的列」；命中多条授权行取<b>并集</b>（多主体只更严，绝不因命中
 * 宽松主体而放宽）。无任何命中行（未配列权限、或非登录上下文）返回空集，即不隐藏任何列——与行级「无授权记录=默认开放」一致。
 * </p>
 * <p>
 * 本服务只负责给出隐藏 token；实际剥离在三个落点：admin 运行态配置 {@code /config}（两类 Portal 表头）、
 * forge {@code PortalDriver#buildAliasMap}（table 数据）与 {@code DynamicBaseController} 的透视查询（pivot 数据）。
 * </p>
 *
 * @author Sharp
 * @since 2026-09-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalColumnPolicyService {

    /**
     * 列级（原表列）授权的资源类型：资源标识为 Portal 逻辑名，
     * 每主体行的 extra_data 携带该主体被隐藏的列 token（{@code {"columns":["c:prop",…]}}）。
     * 与整体/行级同用 sys_portal 主键之外的独立 resourceType，避免全量覆盖保存相互污染。
     */
    public static final String RESOURCE_TYPE_PORTAL_COLUMN = "sys_portal_column";

    /**
     * 列级（透视列）授权的资源类型：透视父列 itemValue 与度量 field 的隐藏集合，
     * token 形如 {@code p:itemValue} / {@code m:field}，{@code resourceId=} Portal 逻辑名。
     */
    public static final String RESOURCE_TYPE_PORTAL_PIVOT = "sys_portal_pivot";

    /**
     * 原表列 token 前缀
     */
    public static final String COLUMN_TOKEN_PREFIX = "c:";

    /**
     * extra_data 顶层结构：仅取 columns 一节，其余扩展位忽略
     */
    @Data
    @NoArgsConstructor
    private static class ColumnPolicyExtra {
        private List<String> columns;
    }

    private final ResourcePermFilterService resourcePermFilterService;

    /**
     * 求当前用户在指定列资源上被隐藏的 token 集合
     *
     * @param resourceType 列资源类型（{@link #RESOURCE_TYPE_PORTAL_COLUMN} 或
     *                     {@link #RESOURCE_TYPE_PORTAL_PIVOT}）
     * @param portalName   Portal 逻辑名（资源标识）
     * @return 隐藏 token 集合；空集表示不隐藏任何列（默认开放）
     */
    public Set<String> resolveHiddenTokens(String resourceType, String portalName) {
        Set<String> hidden = new LinkedHashSet<>();
        if (FuncUtil.isEmpty(resourceType) || FuncUtil.isEmpty(portalName)) {
            return hidden;
        }
        List<AcResourcePerm> matchedRows = resourcePermFilterService.getMatchedRows(resourceType, portalName);
        if (FuncUtil.isEmpty(matchedRows)) {
            return hidden;
        }
        for (AcResourcePerm row : matchedRows) {
            parseColumns(row.getExtraData(), hidden);
        }
        return hidden;
    }

    /**
     * 便捷判定：带命名空间前缀的 token 是否命中隐藏集合
     */
    public boolean isHidden(Set<String> hidden, String token) {
        return FuncUtil.isNotEmpty(hidden) && hidden.contains(token);
    }

    /**
     * 解析单行 extra_data 的 columns 并入 hidden；空/非法按「无隐藏」跳过（脏数据不连坐整表）
     */
    private void parseColumns(String extraData, Set<String> hidden) {
        if (FuncUtil.isEmpty(extraData)) {
            return;
        }
        try {
            ColumnPolicyExtra extra = JsonUtil.readJson(extraData, ColumnPolicyExtra.class);
            if (FuncUtil.isEmpty(extra) || FuncUtil.isEmpty(extra.getColumns())) {
                return;
            }
            for (String token : extra.getColumns()) {
                if (FuncUtil.isNotEmpty(token)) {
                    hidden.add(token);
                }
            }
        } catch (Exception e) {
            log.warn("[列权限] extra_data 解析失败，跳过该行隐藏配置：{}", extraData, e);
        }
    }
}
