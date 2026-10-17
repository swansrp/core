package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.mapper.AcDeptDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Title: AcDeptService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcDeptService extends BaseSqlRepo<AcDeptDao, AcDept> {
    @Override
    public void truncate() {
        super.baseMapper.truncate();
    }

    public List<AcDept> getDepartmentByStatus(Integer status) {
        LambdaQueryWrapper<AcDept> wrapper = super.getQueryWrapper().eq(AcDept::getStatus, status)
                .orderByAsc(AcDept::getShowOrder);
        return select(wrapper);
    }

    public AcDept getDeptByName(String name) {
        LambdaQueryWrapper<AcDept> wrapper = super.getQueryWrapper().eq(AcDept::getName, name);
        return selectOne(wrapper);
    }

    /**
     * 根据 deptId 集合批量查询部门名称
     *
     * @param deptIdSet deptId 集合
     * @return Map&lt;deptId, deptName&gt;
     */
    public Map<Object, Object> getNamesByDeptIdSet(HashSet<Object> deptIdSet) {
        Map<Object, Object> map = new HashMap<>();
        if (FuncUtil.isNotEmpty(deptIdSet)) {
            LambdaQueryWrapper<AcDept> wrapper = super.getQueryWrapper()
                    .in(AcDept::getDeptId, deptIdSet);
            List<AcDept> depts = select(wrapper);
            Map<String, AcDept> acDeptMap = ReflectionUtil.reflectToMap(depts, AcDept::getDeptId);
            for (Object deptId : deptIdSet) {
                AcDept dept = acDeptMap.get(deptId);
                map.put(deptId, FuncUtil.isNotEmpty(dept) ? dept.getName() : null);
            }
        }
        return map;
    }
}
