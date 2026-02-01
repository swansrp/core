package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcDeptMenu;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.mapper.AcDeptMenuDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门和菜单关联表Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class AcDeptMenuService extends BaseSqlRepo<AcDeptMenuDao, AcDeptMenu> {

    private final RecursionService recursionService;
    private final AcUserDeptService acUserDeptService;

    /**
     * 绑定菜单到部门
     *
     * @param deptId 部门ID
     * @param menuId 菜单ID
     */
    public void bind(String deptId, Long menuId) {
        AcDeptMenu acDeptMenu = new AcDeptMenu();
        acDeptMenu.setDeptId(deptId);
        acDeptMenu.setMenuId(menuId);
        super.insertOrUpdate(acDeptMenu);
    }

    /**
     * 解绑部门菜单
     *
     * @param deptId 部门ID
     * @param menuId 菜单ID
     */
    public void unbind(String deptId, Long menuId) {
        AcDeptMenu acDeptMenu = new AcDeptMenu();
        acDeptMenu.setDeptId(deptId);
        acDeptMenu.setMenuId(menuId);
        super.deleteByMultiId(acDeptMenu);
    }

   /**
     * 获取用户的所有菜单（基于部门权限）
     *
     * @param userId     用户ID
     * @param clientType 客户端类型
     * @return 菜单列表
     */
    public List<AcMenu> getAllMenu(Long userId, String clientType) {
        // 1. 查询用户所属的部门
        LambdaQueryWrapper<AcUserDept> wr = new LambdaQueryWrapper<>();
        wr.eq(AcUserDept::getUserId, userId);
        List<AcUserDept> acUserDepts = acUserDeptService.select(wr);
        
        // 2. 收集部门ID（包括子部门）
        Set<String> deptIds = new HashSet<>();
        for (AcUserDept acUserDept : acUserDepts) {
            // 如果数据权限范围是"本部门及子部门"，则递归查询子部门
            if (DataPermitScopeDict.SUBORDINATE.getValue().equals(acUserDept.getDataScope())) {
                List<String> subDepts = recursionService.getChildList(AcDept::getDeptId, AcDept::getPid, acUserDept.getDeptId());
                if (FuncUtil.isNotEmpty(subDepts)) {
                    deptIds.addAll(subDepts);
                }
            }
            deptIds.add(acUserDept.getDeptId());
        }
        
        // 3. 如果用户没有部门，返回空列表
        if (FuncUtil.isEmpty(deptIds)) {
            return new ArrayList<>();
        }
        
        // 4. 查询部门关联的菜单
        MPJLambdaWrapper<AcDeptMenu> wrapper = super.getMPJLambdaWrapper();
        wrapper.distinct();
        wrapper.selectAll(AcMenu.class);
        wrapper.innerJoin(AcMenu.class, AcMenu::getMenuId, AcDeptMenu::getMenuId);
        wrapper.in(AcDeptMenu::getDeptId, deptIds);
        wrapper.eq(AcMenu::getClientType, clientType);
        wrapper.eq(AcMenu::getStatus, CommonConst.YES);
        wrapper.eq(AcMenu::getVisible, CommonConst.YES);
        wrapper.orderByAsc(AcMenu::getShowOrder);
        
        return super.selectJoinList(AcMenu.class, wrapper);
    }
}
