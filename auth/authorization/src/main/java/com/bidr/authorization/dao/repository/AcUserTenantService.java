package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.mapper.AcUserTenantMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Title: AcUserTenantService
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>ac_user_tenant 是 (user_id, tenant_id) 复合主键关系表，物理删、无软删位；
 * 一个用户可同时归属多个租户，故一律 select 列表、不用 selectOne。</p>
 * <p>🔴 类名硬约束：框架 {@code BaseAdminController.getRepo()} 按
 * {@code Introspector.decapitalize(实体简名)+"Service"} 从容器取 bean，本类必须叫此名。</p>
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Service
public class AcUserTenantService extends BaseSqlRepo<AcUserTenantMapper, AcUserTenant> {

    /**
     * 查询用户的全部租户关系。
     *
     * @param userId 用户id（null ⇒ 空列表，不抛）
     * @return 关系列表（含各自 isAdmin），无数据时返回空列表
     */
    public List<AcUserTenant> listByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AcUserTenant> wrapper = super.getQueryWrapper().eq(AcUserTenant::getUserId, userId);
        return select(wrapper);
    }

    /**
     * 查询用户的租户 id 集合（登录签发烤进 access token 的现查入口）。
     *
     * @param userId 用户id（null ⇒ 空列表，不抛）
     * @return 租户 id 列表，无归属时返回空列表
     */
    public List<Long> listTenantIdsByUserId(Long userId) {
        return collectTenantIds(listByUserId(userId));
    }

    /**
     * 查询租户下的全部成员关系。
     *
     * @param tenantId 租户id（null ⇒ 空列表，不抛）
     * @return 关系列表（含各自 isAdmin），无成员时返回空列表
     */
    public List<AcUserTenant> listByTenantId(Long tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AcUserTenant> wrapper = super.getQueryWrapper().eq(AcUserTenant::getTenantId, tenantId);
        return select(wrapper);
    }

    /**
     * 查询租户下全部成员的用户 id（管理面成员列表用）。
     *
     * @param tenantId 租户id（null ⇒ 空列表，不抛）
     * @return 用户 id 列表，无成员时返回空列表
     */
    public List<Long> listUserIdsByTenantId(Long tenantId) {
        List<AcUserTenant> relations = listByTenantId(tenantId);
        if (FuncUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }
        List<Long> userIds = new ArrayList<>(relations.size());
        for (AcUserTenant relation : relations) {
            userIds.add(relation.getUserId());
        }
        return userIds;
    }

    /**
     * 查询指定用户与指定租户之关系（批乙棒4 加法——成员判权/加移/切管理员的唯一取数口）。
     * <p>复合主键 (user_id, tenant_id) 下至多一行，selectOne 安全（区别于 {@link #listByUserId} 的一对多）。</p>
     *
     * @param userId   用户id（任一入参 null ⇒ null，不抛）
     * @param tenantId 租户id
     * @return 关系行（含 isAdmin）；未加入 ⇒ null
     */
    public AcUserTenant getRelation(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        LambdaQueryWrapper<AcUserTenant> wrapper = super.getQueryWrapper()
                .eq(AcUserTenant::getUserId, userId)
                .eq(AcUserTenant::getTenantId, tenantId);
        return selectOne(wrapper);
    }

    /**
     * 物理删关系行（批乙棒4 加法——移成员）。本表无软删位，形态照 AcUserDept。
     *
     * @param userId   用户id（任一入参 null ⇒ false，不抛）
     * @param tenantId 租户id
     * @return 是否删到行（未加入时删 0 行 ⇒ false）
     */
    public boolean deleteRelation(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }
        LambdaQueryWrapper<AcUserTenant> wrapper = super.getQueryWrapper()
                .eq(AcUserTenant::getUserId, userId)
                .eq(AcUserTenant::getTenantId, tenantId);
        return delete(wrapper);
    }

    /**
     * 关系列表 → 租户 id 列表的收口（判据只此一处，供测试直接断言形态）。
     * <p>包可见性同 AcSsoRedirectService 惯例：不为测试拓宽公开 API。</p>
     */
    static List<Long> collectTenantIds(List<AcUserTenant> relations) {
        if (FuncUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }
        List<Long> tenantIds = new ArrayList<>(relations.size());
        for (AcUserTenant relation : relations) {
            tenantIds.add(relation.getTenantId());
        }
        return tenantIds;
    }
}
