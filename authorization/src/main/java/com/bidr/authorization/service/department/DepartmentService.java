package com.bidr.authorization.service.department;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.vo.department.DepartmentItem;
import com.bidr.authorization.vo.department.DepartmentTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.dict.ActiveStatusDict;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: DepartmentService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/21 18:26
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final AcDeptService acDeptService;


    public List<DepartmentTreeRes> getDeptTree() {
        List<AcDept> res = acDeptService.getDepartmentByStatus(ActiveStatusDict.ACTIVATE.getValue());
        List<DepartmentItem> items = Resp.convert(res, DepartmentItem.class);
        return ReflectionUtil.buildTree(DepartmentTreeRes::setChildren, items, DepartmentItem::getValue,
                DepartmentItem::getPid, null);
    }
}
