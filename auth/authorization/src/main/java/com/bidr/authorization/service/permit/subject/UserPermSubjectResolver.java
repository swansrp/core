package com.bidr.authorization.service.permit.subject;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * Title: UserPermSubjectResolver
 * Description: 用户维度解析——主体标识即当前用户编码
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermSubjectResolver implements PermSubjectResolver {

    @Override
    public ResourceSubjectTypeDict subjectType() {
        return ResourceSubjectTypeDict.USER;
    }

    @Override
    public Set<String> resolveSubjectIds(PermSubjectContext context) {
        String customerNumber = context.getCustomerNumber();
        if (FuncUtil.isEmpty(customerNumber)) {
            return Collections.emptySet();
        }
        return Collections.singleton(customerNumber);
    }
}
