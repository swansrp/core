package com.bidr.authorization.service.permit.subject;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: RolePermSubjectResolver
 * Description: 角色维度解析——用户持有的角色id（来自主体上下文，无需查库）
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermSubjectResolver implements PermSubjectResolver {

    @Override
    public ResourceSubjectTypeDict subjectType() {
        return ResourceSubjectTypeDict.ROLE;
    }

    @Override
    public Set<String> resolveSubjectIds(PermSubjectContext context) {
        List<Long> roleIds = context.getRoleIds();
        if (FuncUtil.isEmpty(roleIds)) {
            return Collections.emptySet();
        }
        Set<String> res = new HashSet<>(roleIds.size());
        for (Long roleId : roleIds) {
            if (FuncUtil.isNotEmpty(roleId)) {
                res.add(String.valueOf(roleId));
            }
        }
        return res;
    }
}
