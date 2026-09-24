package com.bidr.authorization.service.permit.subject;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Title: PermSubjectResolver
 * Description: 资源授权主体解析器——把「当前用户」映射为某一主体维度下用户所持有的主体标识集合
 * <p>
 * {@code ac_resource_perm} 一张表用 {@code subject_type} 承载角色/用户/用户组/部门四种授权主体，
 * 每种主体的「用户持有哪些标识」取法各不相同（用户组、部门还要按 dataScope 沿树向下展开）。
 * 把这一步抽象成解析器后，权限过滤核心只做「授权标识 ∩ 用户持有标识」的集合判定，
 * <b>新增授权维度 = 新增一个实现类</b>，不需要改动过滤逻辑。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
public interface PermSubjectResolver {

    /**
     * 本解析器负责的主体类型
     */
    ResourceSubjectTypeDict subjectType();

    /**
     * 解析当前用户在该维度持有的全部主体标识
     *
     * @param context 当前用户身份上下文
     * @return 主体标识集合（与 ac_resource_perm.subject_id 同口径的字符串），无数据返回空集合
     */
    Set<String> resolveSubjectIds(PermSubjectContext context);

    /**
     * 命中描述，用于权限判定日志追踪
     */
    default String hitLabel(String matchedSubjectId) {
        return subjectType().getLabel() + "匹配 subjectId=" + matchedSubjectId;
    }

    /**
     * 当前用户身份上下文（一次请求解析一次，避免逐条资源重复查库）
     */
    @Getter
    @RequiredArgsConstructor
    class PermSubjectContext {
        /**
         * 用户id，ac_user_group / ac_user_dept 的关联键
         */
        private final Long userId;
        /**
         * 用户编码，USER 维度的主体标识
         */
        private final String customerNumber;
        /**
         * 用户拥有的角色id列表
         */
        private final List<Long> roleIds;
    }
}
