package com.bidr.authorization.service.permit.subject;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.authorization.service.permit.DataScopeResolver;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: GroupPermSubjectResolver
 * Description: 用户组维度解析——按 dataScope 沿组树向下展开后的可见组集合
 * <p>
 * 不限定 groupType：{@code ac_resource_perm} 里只存了组 id，未记录该组属于哪棵树，
 * 因此按「用户在全部组树上的视野并集」判定。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPermSubjectResolver implements PermSubjectResolver {

    private final DataScopeResolver dataScopeResolver;

    @Override
    public ResourceSubjectTypeDict subjectType() {
        return ResourceSubjectTypeDict.GROUP;
    }

    @Override
    public Set<String> resolveSubjectIds(PermSubjectContext context) {
        Set<Long> groupIds = dataScopeResolver.resolveEffectiveGroupIds(context.getUserId(), null);
        if (FuncUtil.isEmpty(groupIds)) {
            return Collections.emptySet();
        }
        Set<String> res = new HashSet<>(groupIds.size());
        for (Long groupId : groupIds) {
            if (FuncUtil.isNotEmpty(groupId)) {
                res.add(String.valueOf(groupId));
            }
        }
        return res;
    }
}
