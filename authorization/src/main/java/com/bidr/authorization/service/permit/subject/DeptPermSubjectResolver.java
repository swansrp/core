package com.bidr.authorization.service.permit.subject;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.authorization.service.permit.DataScopeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Title: DeptPermSubjectResolver
 * Description: 部门维度解析——按 dataScope 沿部门树向下展开后的可见部门集合
 * <p>
 * 一个用户可同时归属多个部门（ac_user_dept 为 user_id + dept_id 复合主键），
 * 故此处返回的是<b>集合</b>而非单个部门；展开结果已按部门有效性收敛。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptPermSubjectResolver implements PermSubjectResolver {

    private final DataScopeResolver dataScopeResolver;

    @Override
    public ResourceSubjectTypeDict subjectType() {
        return ResourceSubjectTypeDict.DEPT;
    }

    @Override
    public Set<String> resolveSubjectIds(PermSubjectContext context) {
        return dataScopeResolver.resolveEffectiveDeptIds(context.getUserId());
    }
}
