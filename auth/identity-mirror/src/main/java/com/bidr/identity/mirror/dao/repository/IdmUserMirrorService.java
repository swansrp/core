package com.bidr.identity.mirror.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.mapper.IdmUserMirrorMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IdmUserMirrorService
 * Description: 用户身份镜像 repository（T2.3d）。取数口收敛给
 * {@code IdentityMirrorSyncService}（全量比对/单用户作用域写）与 {@code MirrorBindRepoResolver}
 * （@BindUser SPI 兜底查询通道）。业务键查询（登录名/编码）供子系统登录闸与成员同步按名查人。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmUserMirrorService extends BaseSqlRepo<IdmUserMirrorMapper, IdmUserMirror> {

    /**
     * 镜像全量（同步比对用；镜像表＝上游有效行投影，行集小，直接拉平）。
     *
     * @return 全部镜像行，无数据时返回空列表
     */
    public List<IdmUserMirror> listAll() {
        return select();
    }

    /**
     * 按登录名取用户镜像行（UK(user_name)，至多一行；登录链是 best-effort，不抛）。
     *
     * @param userName 登录名（null/空 ⇒ null）
     * @return 镜像行；无此人 ⇒ null
     */
    public IdmUserMirror findByUserName(String userName) {
        if (userName == null || userName.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<IdmUserMirror> wrapper = super.getQueryWrapper()
                .eq(IdmUserMirror::getUserName, userName);
        return selectOne(wrapper);
    }

    /**
     * 按用户编码取用户镜像行（UK(customer_number)，至多一行；同上不抛）。
     *
     * @param customerNumber 用户编码（null/空 ⇒ null）
     * @return 镜像行；无此人 ⇒ null
     */
    public IdmUserMirror findByCustomerNumber(String customerNumber) {
        if (customerNumber == null || customerNumber.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<IdmUserMirror> wrapper = super.getQueryWrapper()
                .eq(IdmUserMirror::getCustomerNumber, customerNumber);
        return selectOne(wrapper);
    }
}
