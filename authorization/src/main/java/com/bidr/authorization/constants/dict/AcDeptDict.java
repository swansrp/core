package com.bidr.authorization.constants.dict;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Title: AcDeptDict
 * Description: 部门动态字典（deptId → name）
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/6/22
 */
@Component
@MetaDict(value = "AC_DEPT_DICT", remark = "部门字典")
@RequiredArgsConstructor
public class AcDeptDict implements IDynamicDict {

    private final AcDeptService acDeptService;

    @Override
    public Collection<SysDict> generate() {
        List<KeyValueResVO> dictList = getDict();
        Map<String, SysDict> map = new LinkedHashMap<>(0);
        if (FuncUtil.isNotEmpty(dictList)) {
            int i = 0;
            for (KeyValueResVO keyValue : dictList) {
                SysDict dict = buildSysDict(keyValue.getValue(), keyValue.getLabel(), i++);
                map.put(dict.getDictValue(), dict);
            }
        }
        return map.values();
    }

    /**
     * 拉取部门列表（deptId → name）
     *
     * @return 部门键值对列表
     */
    public List<KeyValueResVO> getDict() {
        List<KeyValueResVO> resList = new ArrayList<>();
        List<AcDept> deptList = acDeptService.select(acDeptService.getQueryWrapper()
                .eq(AcDept::getStatus, CommonConst.YES).orderByAsc(AcDept::getShowOrder));
        if (FuncUtil.isNotEmpty(deptList)) {
            for (AcDept dept : deptList) {
                resList.add(new KeyValueResVO(dept.getDeptId(), dept.getName()));
            }
        }
        return resList;
    }
}
