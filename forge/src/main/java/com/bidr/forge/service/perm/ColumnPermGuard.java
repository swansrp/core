package com.bidr.forge.service.perm;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Title: ColumnPermGuard
 * Description: 低代码 Portal 的列级权限守卫——按 ac_resource_perm 剔除当前用户无权查看的列
 * <p>
 * 列级授权以列配置表的主键为资源：{@code resourceType=sys_dataset_column/sys_matrix_column}，
 * {@code resourceId} 为列配置行 id。这样同一套通用资源权限表既管得住「哪个数据集可见」，
 * 也管得住「数据集里哪几列可见」，无需为列权限另建表。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnPermGuard {

    /**
     * 列级授权的资源类型：数据集列配置
     */
    public static final String RESOURCE_TYPE_DATASET_COLUMN = "sys_dataset_column";
    /**
     * 列级授权的资源类型：矩阵列配置
     */
    public static final String RESOURCE_TYPE_MATRIX_COLUMN = "sys_matrix_column";

    private final ResourcePermFilterService resourcePermFilterService;

    /**
     * 求当前用户有权查看的列 id 集合
     * <p>
     * 返回 {@code null} 表示<b>跳过列过滤</b>：线程上下文没有登录用户（定时任务、内部调用、
     * ChatBI 后台取数）。此时若按「无身份 → 无任何列」处理，会得到空别名映射并让整条查询直接失败，
     * 影响面远大于收益；而这类入口本身也不在浏览器可见范围内。
     * </p>
     *
     * @param resourceType 资源类型（{@link #RESOURCE_TYPE_DATASET_COLUMN} 或 {@link #RESOURCE_TYPE_MATRIX_COLUMN}）
     * @param columnIds    待判定的列配置 id
     * @return 有权的列 id（字符串形式）集合；null 表示不过滤
     * @throws NoticeException 列非空但有权列为空（全部无权）时直接拒绝，不给下游生成 {@code SELECT *} 的机会
     */
    public Set<String> retainAccessibleColumns(String resourceType, Collection<Long> columnIds) {
        if (FuncUtil.isEmpty(columnIds)) {
            return new HashSet<>();
        }
        if (FuncUtil.isEmpty(AccountContext.getOperator())) {
            log.debug("[列权限] 线程上下文无登录用户，跳过列过滤：resourceType={}, 列数={}", resourceType, columnIds.size());
            return null;
        }
        Set<String> ids = columnIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        if (FuncUtil.isEmpty(ids)) {
            return new HashSet<>();
        }
        Set<String> permitted = new HashSet<>(resourcePermFilterService.filterAccessibleIds(resourceType, new ArrayList<>(ids)));
        // fail-closed：全部列都无权时不能返回空集，必须直接拒绝。
        // 因为下游 SQL 构建器对「列清单为空」的兜底是 SELECT *（MatrixSqlBuilder#buildSelectColumns、
        // DatasetSqlBuilder#buildSelectColumns），空 aliasMap 会退化成整表全列输出，反而泄漏更多数据。
        if (FuncUtil.isEmpty(permitted)) {
            log.warn("[列权限] 用户 {} 对 resourceType={} 的 {} 列均无授权，拒绝本次访问", AccountContext.getOperator(), resourceType, ids.size());
            throw new NoticeException(ErrCodeSys.SYS_PERMIT_ERROR, "无该数据配置的列查看权限：" + resourceType);
        }
        return permitted;
    }
}
