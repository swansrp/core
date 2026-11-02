package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.authz.AuthzVersionHolder;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Title: AuthzVersionedSqlRepo
 * Description: 授权语义表仓储基类（T2.3b bump 接线）——继承本类的仓储，任何写方法成功后
 * {@link AuthzVersionHolder#bump()}（授权版本 +1），client 侧缓存按版本失效（秒级收回）。
 *
 * <p><b>为什么落仓储层而不逐 service 补</b>（2026-11-03 棒4 裁定）：授权写入口不止
 * 本模块 service——kernel 通用管理端点（BaseAdminController 的 insert/update/delete/
 * disable，BaseBindController 的 bind/unbind/replace 全家）全部经仓储方法落库，
 * 逐 service/控制器补会在 bind 面漏点。仓储层单一拦截面＝本模块全部写路径
 * （service 直调、kernel 通用端点、种子/初始化）全覆盖，取证 grep 见棒4 报告。</p>
 *
 * <p><b>bump 时机语义</b>：写语句成功即 bump（可能先于外层事务提交）——外层事务回滚时
 * 版本已 +1，效果＝客户端多失效一次重拉同一数据，<b>只会多余失效、绝不产生 stale</b>
 * （失效方向安全）。同一逻辑写经多层方法（updateById→update）可能 bump 多次，版本为
 * 单调递增计数器，多次 +1 无语义影响。</p>
 *
 * <p><b>🔴 接入纪律（红线）</b>：只允许「授权语义」表仓储继承本类——菜单/角色/资源/
 * 用户角色及菜单绑定链（ac_menu、ac_role、ac_role_menu、ac_user_role、ac_user_menu、
 * ac_dept_menu、ac_group_menu、ac_resource_perm）。业务数据（用户/租户/部门/项目等）
 * 仓储一律不接——业务 CRUD 不触发授权缓存失效。</p>
 *
 * <p><b>未覆盖面</b>（如实声明）：IService 接口 default 批量方法（removeByIds/
 * saveOrUpdateBatch 等，default 方法无法 super 委托）与 MPP 的 deleteByMultiId/
 * updateByMultiId 接口 default 方法不经本类拦截——当前模块对授权表的该类调用仅
 * {@code super.deleteByMultiId} 两处（AcUserRoleService.unbind / AcDeptMenuService.unbind，
 * 已在方法内显式补 bump），grep 全量证据见棒4 报告「bump 写点清单」。</p>
 *
 * @author coder (棒4, T2.3b 2026-11-03)
 * @since 2026-11-03
 */
public abstract class AuthzVersionedSqlRepo<K extends MyBaseMapper<T>, T> extends BaseSqlRepo<K, T> {

    /** 写成功后 bump（fail-safe：回滚只多失效不 stale） */
    private void bumpOnWrite(boolean success) {
        if (success) {
            AuthzVersionHolder.bump();
        }
    }

    // ---------------- insert 面 ----------------

    @Override
    public boolean insert(T entity) {
        boolean result = super.insert(entity);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean insert(Collection<T> entity) {
        boolean result = super.insert(entity);
        bumpOnWrite(result);
        return result;
    }

    // 注：save(T)/saveBatch(Collection)/update(Wrapper)/update(T,Wrapper) 是 IService 的
    // default 方法（MP 3.5.2），无法 super 委托——它们内部经 this 虚调用落到本类已拦截的
    // saveBatch(Collection,int)/updateById/update 面；对授权表零直接调用（取证 grep 见报告）。

    @Override
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        boolean result = super.saveBatch(entityList, batchSize);
        bumpOnWrite(result);
        return result;
    }

    // ---------------- update 面 ----------------

    @Override
    public boolean updateById(T entity) {
        boolean result = super.updateById(entity);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean updateById(T entity, boolean ignoreNull) {
        boolean result = super.updateById(entity, ignoreNull);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean updateById(List<T> entityList) {
        boolean result = super.updateById(entityList);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper) {
        boolean result = super.update(entity, wrapper);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper, boolean ignoreNull) {
        boolean result = super.update(entity, wrapper, ignoreNull);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean update(T entity, Map<String, Object> propertyMap) {
        boolean result = super.update(entity, propertyMap);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean update(T entity, Map<String, Object> propertyMap, boolean ignoreNull) {
        boolean result = super.update(entity, propertyMap, ignoreNull);
        bumpOnWrite(result);
        return result;
    }

    // 注：update(Wrapper)/update(T,Wrapper) 为 IService default 方法，无法 super 委托；
    // 经 BaseSqlRepo.update(...) 面调用时已被上方重载拦截，直接调用面为零（见报告取证）。

    // ---------------- insertOrUpdate 面 ----------------

    @Override
    public boolean insertOrUpdate(T entity, UpdateWrapper<T> wrapper) {
        boolean result = super.insertOrUpdate(entity, wrapper);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean insertOrUpdate(T entity) {
        boolean result = super.insertOrUpdate(entity);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean insertOrUpdate(Collection<T> entity) {
        boolean result = super.insertOrUpdate(entity);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean insertOrUpdate(T entity, SFunction<T, ?> field, SFunction<T, ?>... fields) {
        boolean result = super.insertOrUpdate(entity, field, fields);
        bumpOnWrite(result);
        return result;
    }

    // ---------------- delete 面 ----------------

    @Override
    public boolean delete(T entity) {
        boolean result = super.delete(entity);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean delete(Wrapper<T> wrapper) {
        boolean result = super.delete(wrapper);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean delete(Map<String, Object> propertyMap) {
        boolean result = super.delete(propertyMap);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean delete(List<T> idList) {
        boolean result = super.delete(idList);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean deleteById(Serializable id) {
        boolean result = super.deleteById(id);
        bumpOnWrite(result);
        return result;
    }

    @Override
    public boolean deleteById(T entity) {
        boolean result = super.deleteById(entity);
        bumpOnWrite(result);
        return result;
    }
}
