package com.bidr.kernel.mybatis.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.bo.DynamicColumn;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.bind.AdvancedQueryBindReq;
import com.bidr.kernel.vo.bind.BindInfoReq;
import com.bidr.kernel.vo.bind.QueryBindReq;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * Title: BaseBindRepo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 13:35
 */
@Slf4j
@Service
@SuppressWarnings("rawtypes,unchecked")
public abstract class BaseBindRepo<ENTITY, BIND, ATTACH, ENTITY_VO, ATTACH_VO> {
    @Resource
    private ApplicationContext applicationContext;

    public List<ATTACH_VO> getBindList(Object entityId) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        Map<String, String> aliasMap = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            wrapper = getAttachPortalService().getJoinWrapper();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), entityId);
        return attachRepo().select(new AdvancedQuery(), new ArrayList<>(), new HashMap<>(0), aliasMap, selectApplyMap,
                wrapper, getAttachVOClass());
    }

    protected Class<ATTACH> getAttachClass() {
        return (Class<ATTACH>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
    }

    protected PortalCommonService<ATTACH, ATTACH_VO> getAttachPortalService() {
        return null;
    }

    protected abstract SFunction<BIND, ?> bindEntityId();

    protected Class<BIND> getBindClass() {
        return (Class<BIND>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected abstract SFunction<BIND, ?> bindAttachId();

    protected abstract SFunction<ATTACH, ?> attachId();

    protected BaseSqlRepo attachRepo() {
        return (BaseSqlRepo) applicationContext.getBean(
                StrUtil.lowerFirst(getAttachClass().getSimpleName()) + "Service");
    }

    protected Class<ATTACH_VO> getAttachVOClass() {
        return (Class<ATTACH_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }

    public int getBindCount(Object entityId) {
        MPJLambdaWrapper<BIND> wrapper = new MPJLambdaWrapper<>(getBindClass());
        wrapper.eq(bindEntityId(), entityId);
        return new Long(getBindRepo().count(wrapper)).intValue();
    }

    protected BaseSqlRepo getBindRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(getBindClass().getSimpleName()) + "Service");
    }

    public PortalCommonService<ATTACH, ATTACH_VO> getPortalService() {
        return getAttachPortalService();
    }

    public IPage<ATTACH_VO> queryAttachList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            wrapper = getAttachPortalService().getJoinWrapper();
            havingFields = getAttachPortalService().getHavingFields();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getEntityId());
        return attachRepo().select(req, aliasMap, havingFields, selectApplyMap, wrapper, getAttachVOClass());
    }

    public IPage<ATTACH_VO> advancedQueryAttachList(AdvancedQueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        Map<String, String> aliasMap = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
            wrapper = getAttachPortalService().getJoinWrapper();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getEntityId());
        return attachRepo().select(req, aliasMap, selectApplyMap, wrapper, getAttachVOClass());
    }

    public IPage<ATTACH> getUnbindList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass()).distinct();
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            wrapper = getAttachPortalService().getJoinWrapper();
            havingFields = getAttachPortalService().getHavingFields();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId());
        wrapper.and(w -> w.isNull(bindEntityId()).or().ne(bindEntityId(), req.getEntityId()));
        return attachRepo().select(req, aliasMap, havingFields, selectApplyMap, wrapper, getAttachVOClass());
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(Object attachId, Object entityId) {
        BIND bindEntity = buildBindEntity(attachId, entityId);
        bindBefore(bindEntity);
        getBindRepo().insertOrUpdate(bindEntity);
    }

    protected BIND buildBindEntity(Object attachId, Object entityId) {
        BIND bindEntity = ReflectionUtil.newInstance(getBindClass());
        LambdaUtil.setValue(bindEntity, bindAttachId(), attachId);
        LambdaUtil.setValue(bindEntity, bindEntityId(), entityId);
        return bindEntity;
    }

    protected void bindBefore(BIND bind) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Object attachId, Object entityId) {
        BIND bindEntity = buildBindEntity(attachId, entityId);
        unBindBefore(bindEntity);
        getBindRepo().deleteById(bindEntity);
    }

    protected void unBindBefore(BIND bind) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindList(List<Object> attachIdList, Object entityId) {
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                unBindBefore(bindEntity);
                getBindRepo().deleteById(bindEntity);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindAll(AdvancedQueryBindReq req) {
        IPage<ATTACH> res = advancedQueryUnbindList(req);
        Validator.assertTrue(res.getPages() < 2, ErrCodeSys.SYS_ERR_MSG,
                "符合条件的数据量过多: " + res.getTotal() + "条, 请设置合理过滤条件");
        List attachIdList = new ArrayList();
        if (FuncUtil.isNotEmpty(res.getRecords())) {
            for (ATTACH record : res.getRecords()) {
                attachIdList.add(attachId().apply(record));
            }
        }
        bindList(attachIdList, req.getEntityId());
    }

    public IPage<ATTACH> advancedQueryUnbindList(AdvancedQueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass()).distinct();
        Map<String, String> aliasMap = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
            wrapper = getAttachPortalService().getJoinWrapper();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId());
        wrapper.and(w -> w.isNull(bindEntityId()).ne(bindEntityId(), req.getEntityId()));
        return attachRepo().select(req, aliasMap, selectApplyMap, wrapper, getAttachVOClass());
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindList(List<Object> attachIdList, Object entityId) {
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                bindBefore(bindEntity);
                getBindRepo().insertOrUpdate(bindEntity);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindAll(Object entityId) {
        LambdaQueryWrapper wrapper = getBindRepo().getQueryWrapper();
        wrapper.eq(bindEntityId(), entityId);
        getBindRepo().delete(wrapper);
    }

    public List<ENTITY> getBindEntityList(Object attachId) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), DbUtil.getTableName(getEntityClass()), entityId(), bindEntityId())
                .eq(bindAttachId(), attachId);
        return entityRepo().selectJoinList(getEntityClass(), wrapper);
    }

    protected Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    protected abstract SFunction<ENTITY, ?> entityId();

    protected BaseSqlRepo entityRepo() {
        return (BaseSqlRepo) applicationContext.getBean(
                StrUtil.lowerFirst(getEntityClass().getSimpleName()) + "Service");
    }

    protected Class<ENTITY_VO> getEntityVOClass() {
        return (Class<ENTITY_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 3);
    }

    public void advancedReplace(AdvancedQueryBindReq req) {
        IPage<ATTACH> res = advancedQueryUnbindList(req);
        Validator.assertTrue(res.getPages() < 2, ErrCodeSys.SYS_ERR_MSG,
                "符合条件的数据量过多: " + res.getTotal() + "条, 请设置合理过滤条件");
        List attachIdList = new ArrayList();
        if (FuncUtil.isNotEmpty(res.getRecords())) {
            for (ATTACH record : res.getRecords()) {
                attachIdList.add(attachId().apply(record));
            }
        }
        replace(attachIdList, req.getEntityId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(List<Object> attachIdList, Object entityId) {
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                unBindBefore(bindEntity);
            }
        }
        Wrapper<BIND> wrapper = (Wrapper<BIND>) getBindRepo().getQueryWrapper().eq(bindEntityId(), entityId);
        getBindRepo().delete(wrapper);
        List<BIND> bindList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                bindBefore(bindEntity);
                bindList.add(bindEntity);
            }
        }
        getBindRepo().saveBatch(bindList);
    }

    public void bindInfo(Object entityId, Object attachId, Object data, boolean strict) {
        BIND bindEntity = (BIND) getBindRepo().selectById(buildBindEntity(attachId, entityId));
        ReflectionUtil.merge(data, bindEntity, !strict);
        getBindRepo().updateById(bindEntity);
    }

    public void bindInfoList(Object entityId, List<BindInfoReq> bindInfoList, boolean strict) {
        List<BIND> bindEntityList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(bindInfoList)) {
            for (BindInfoReq bindInfo : bindInfoList) {
                BIND bindEntity = (BIND) getBindRepo().selectById(buildBindEntity(bindInfo.getAttachId(), entityId));
                ReflectionUtil.merge(bindInfo.getData(), bindEntity, !strict);
                bindEntityList.add(bindEntity);
            }
        }
        getBindRepo().updateById(bindEntityList);
    }
}
