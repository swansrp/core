package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.dao.mapper.AcResourcePermMapper;
import com.bidr.authorization.vo.perm.ResourcePermSaveBySubjectReq;
import com.bidr.authorization.vo.perm.ResourcePermSaveReq;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通用资源权限表Service
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Service
public class AcResourcePermService extends BaseSqlRepo<AcResourcePermMapper, AcResourcePerm> {

    /**
     * 查询某资源的全部授权记录
     */
    public List<AcResourcePerm> getByResource(String resourceType, String resourceId) {
        LambdaQueryWrapper<AcResourcePerm> wrapper = super.getQueryWrapper()
                .eq(AcResourcePerm::getResourceType, resourceType)
                .eq(AcResourcePerm::getResourceId, resourceId);
        return select(wrapper);
    }

    /**
     * 查询某资源是否有授权记录
     */
    public boolean hasPermRecords(String resourceType, String resourceId) {
        LambdaQueryWrapper<AcResourcePerm> wrapper = super.getQueryWrapper()
                .eq(AcResourcePerm::getResourceType, resourceType)
                .eq(AcResourcePerm::getResourceId, resourceId);
        return existed(wrapper);
    }

    /**
     * 批量查询多个资源的授权记录
     */
    public List<AcResourcePerm> getByResourceIds(String resourceType, List<String> resourceIds) {
        LambdaQueryWrapper<AcResourcePerm> wrapper = super.getQueryWrapper()
                .eq(AcResourcePerm::getResourceType, resourceType)
                .in(AcResourcePerm::getResourceId, resourceIds);
        return select(wrapper);
    }

    /**
     * 删除某资源的全部授权记录
     */
    public void deleteByResource(String resourceType, String resourceId) {
        LambdaQueryWrapper<AcResourcePerm> wrapper = super.getQueryWrapper()
                .eq(AcResourcePerm::getResourceType, resourceType)
                .eq(AcResourcePerm::getResourceId, resourceId);
        delete(wrapper);
    }

    /**
     * 全量覆盖保存某资源的授权配置（事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePerms(ResourcePermSaveReq req, String operator) {
        String resourceType = req.getResourceType();
        String resourceId = req.getResourceId();

        // 先删除原有授权
        deleteByResource(resourceType, resourceId);

        // 批量插入新授权
        List<AcResourcePerm> permList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(req.getPerms())) {
            for (ResourcePermSaveReq.PermItem item : req.getPerms()) {
                AcResourcePerm perm = new AcResourcePerm();
                perm.setResourceType(resourceType);
                perm.setResourceId(resourceId);
                perm.setSubjectType(item.getSubjectType());
                perm.setSubjectId(item.getSubjectId());
                perm.setCreateBy(operator);
                permList.add(perm);
            }
        }
        if (FuncUtil.isNotEmpty(permList)) {
            insert(permList);
        }
    }

    /**
     * 查询某主体已授权的资源ID列表（反向查询）
     */
    public List<String> listResourceIdsBySubject(String resourceType, Integer subjectType, String subjectId) {
        LambdaQueryWrapper<AcResourcePerm> wrapper = super.getQueryWrapper()
                .eq(AcResourcePerm::getResourceType, resourceType)
                .eq(AcResourcePerm::getSubjectType, subjectType)
                .eq(AcResourcePerm::getSubjectId, subjectId);
        List<AcResourcePerm> list = select(wrapper);
        if (FuncUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().map(AcResourcePerm::getResourceId).collect(Collectors.toList());
    }

    /**
     * 按主体全量设置授权资源（反向配置，事务）
     * <p>
     * 只增删该主体的授权行，不影响其他主体：
     * toAdd = 传入集合 - 当前集合 → insert；toRemove = 当前集合 - 传入集合 → delete
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveBySubject(ResourcePermSaveBySubjectReq req, String operator) {
        String resourceType = req.getResourceType();
        Integer subjectType = req.getSubjectType();
        String subjectId = req.getSubjectId();

        Set<String> targetIds = new HashSet<>(req.getResourceIds() == null ? new ArrayList<>() : req.getResourceIds());
        Set<String> currentIds = new HashSet<>(listResourceIdsBySubject(resourceType, subjectType, subjectId));

        // 需新增：target 有、current 无
        List<AcResourcePerm> toAdd = new ArrayList<>();
        for (String resourceId : targetIds) {
            if (!currentIds.contains(resourceId)) {
                AcResourcePerm perm = new AcResourcePerm();
                perm.setResourceType(resourceType);
                perm.setResourceId(resourceId);
                perm.setSubjectType(subjectType);
                perm.setSubjectId(subjectId);
                perm.setCreateBy(operator);
                toAdd.add(perm);
            }
        }
        if (FuncUtil.isNotEmpty(toAdd)) {
            insert(toAdd);
        }

        // 需删除：current 有、target 无
        Set<String> toRemove = new HashSet<>();
        for (String resourceId : currentIds) {
            if (!targetIds.contains(resourceId)) {
                toRemove.add(resourceId);
            }
        }
        if (FuncUtil.isNotEmpty(toRemove)) {
            LambdaQueryWrapper<AcResourcePerm> deleteWrapper = super.getQueryWrapper()
                    .eq(AcResourcePerm::getResourceType, resourceType)
                    .eq(AcResourcePerm::getSubjectType, subjectType)
                    .eq(AcResourcePerm::getSubjectId, subjectId)
                    .in(AcResourcePerm::getResourceId, toRemove);
            delete(deleteWrapper);
        }
    }
}
