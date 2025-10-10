package com.bidr.kernel.mybatis.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.bidr.kernel.vo.portal.Query;
import com.bidr.kernel.vo.query.QueryReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

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
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), entityId);
        return select(new Query(), wrapper);
    }

    protected Class<ATTACH> getAttachClass() {
        return (Class<ATTACH>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
    }

    protected Class<BIND> getBindClass() {
        return (Class<BIND>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected abstract SFunction<BIND, ?> bindAttachId();

    protected abstract SFunction<ATTACH, ?> attachId();

    protected abstract SFunction<BIND, ?> bindEntityId();

    protected List<ATTACH_VO> select(Query query, MPJLambdaWrapper<ATTACH> wrapper) {
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            wrapper = getAttachPortalService().getJoinWrapper();
            havingFields = getAttachPortalService().getHavingFields();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        return attachRepo().select(query, aliasMap, havingFields, selectApplyMap, wrapper, getAttachVOClass());
    }

    public PortalCommonService<ATTACH, ATTACH_VO> getPortalService() {
        return getAttachPortalService();
    }

    protected PortalCommonService<ATTACH, ATTACH_VO> getAttachPortalService() {
        return null;
    }

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

    public IPage<ATTACH_VO> queryAttachList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getEntityId());
        return query(new Query(req), req, wrapper);
    }

    protected Page<ATTACH_VO> query(Query query, QueryReqVO page, MPJLambdaWrapper<ATTACH> wrapper) {
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getAttachPortalService().getAliasMap();
            wrapper = getAttachPortalService().getJoinWrapper();
            havingFields = getAttachPortalService().getHavingFields();
            selectApplyMap = getAttachPortalService().getSelectApplyMap();
        } else {
            wrapper.selectAll(getAttachClass()).select(bindEntityId());
        }
        return attachRepo().select(query, page.getCurrentPage(), page.getPageSize(), aliasMap, havingFields,
                selectApplyMap, wrapper, getAttachVOClass());
    }

    public IPage<ATTACH_VO> advancedQueryAttachList(AdvancedQueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getEntityId());
        return query(new Query(req), req, wrapper);
    }

    public IPage<ATTACH_VO> getUnbindList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass()).distinct();
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()),
                on -> on.eq(bindAttachId(), attachId()).eq(bindEntityId(), req.getEntityId()));
        wrapper.isNull(bindEntityId());
        return query(new Query(req), req, wrapper);
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
        IPage<ATTACH_VO> res = advancedQueryUnbindList(req);
        Validator.assertTrue(res.getPages() < 2, ErrCodeSys.SYS_ERR_MSG,
                "符合条件的数据量过多: " + res.getTotal() + "条, 请设置合理过滤条件");
        List attachIdList = new ArrayList();
        if (FuncUtil.isNotEmpty(res.getRecords())) {
            for (ATTACH_VO record : res.getRecords()) {
                ATTACH attach = ReflectionUtil.copy(record, getAttachClass());
                attachIdList.add(attachId().apply(attach));
            }
        }
        bindList(attachIdList, req.getEntityId());
    }

    public IPage<ATTACH_VO> advancedQueryUnbindList(AdvancedQueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass()).distinct();
        wrapper.leftJoin(getBindClass(), DbUtil.getTableName(getBindClass()),
                on -> on.eq(bindAttachId(), attachId()).eq(bindEntityId(), req.getEntityId()));
        wrapper.isNull(bindEntityId());
        return query(new Query(req), req, wrapper);
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

    @Transactional(rollbackFor = Exception.class)
    public void advancedReplace(AdvancedQueryBindReq req) {
        IPage<ATTACH_VO> res = advancedQueryUnbindList(req);
        Validator.assertTrue(res.getPages() < 2, ErrCodeSys.SYS_ERR_MSG,
                "符合条件的数据量过多: " + res.getTotal() + "条, 请设置合理过滤条件");
        List attachIdList = new ArrayList();
        if (FuncUtil.isNotEmpty(res.getRecords())) {
            for (ATTACH_VO record : res.getRecords()) {
                ATTACH attach = ReflectionUtil.copy(record, getAttachClass());
                attachIdList.add(attachId().apply(attach));
            }
        }
        replace(attachIdList, req.getEntityId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(List<Object> attachIdList, Object entityId) {
        // 查询旧绑定
        List<BIND> oldBinds = getBindRepo().list((Wrapper) getBindRepo().getQueryWrapper().eq(bindEntityId(), entityId));

        // 旧的 attachId 集合（转为字符串避免类型不匹配）
        Set<String> oldAttachIds = oldBinds.stream()
                .map(bind -> String.valueOf(LambdaUtil.getValue(bind, bindAttachId())))
                .collect(Collectors.toSet());

        // 新的 attachId 集合（转为字符串）
        Set<String> newAttachIds = new HashSet<>();
        if (attachIdList != null && !attachIdList.isEmpty()) {
            attachIdList.forEach(id -> newAttachIds.add(String.valueOf(id)));
        }

        // 需要解绑的 attachId
        Set<String> toUnbind = new HashSet<>();
        for (String id : oldAttachIds) {
            if (!newAttachIds.contains(id)) {
                toUnbind.add(id);
            }
        }

        // 需要绑定的 attachId
        Set<String> toBind = new HashSet<>();
        for (String id : newAttachIds) {
            if (!oldAttachIds.contains(id)) {
                toBind.add(id);
            }
        }


        if (!toUnbind.isEmpty()) {
            // 解绑逻辑
            toUnbind.forEach(attachId -> {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                unBindBefore(bindEntity);
            });
            LambdaQueryWrapper wrapper = getBindRepo().getQueryWrapper();
            wrapper.eq(bindEntityId(), entityId);
            wrapper.in(bindAttachId(), toUnbind);
            getBindRepo().delete(wrapper);
        }

        // 绑定逻辑
        List<BIND> newBindList = toBind.stream()
                .map(attachId -> {
                    BIND bindEntity = buildBindEntity(attachId, entityId);
                    bindBefore(bindEntity);
                    return bindEntity;
                })
                .collect(Collectors.toList());

        if (!newBindList.isEmpty()) {
            getBindRepo().saveBatch(newBindList);
        }
    }

    public void bindInfo(Object entityId, Object attachId, Object data, boolean strict) {
        BIND bindEntity = (BIND) getBindRepo().selectById(buildBindEntity(attachId, entityId));
        if (FuncUtil.isNotEmpty(bindEntity)) {
            ReflectionUtil.merge(data, bindEntity, !strict);
            getBindRepo().updateById(bindEntity);
        } else {
            bindEntity = buildBindEntity(attachId, entityId);
            ReflectionUtil.merge(data, bindEntity, !strict);
            getBindRepo().insert(bindEntity);
        }
    }

    public void bindInfoList(Object entityId, List<BindInfoReq> bindInfoList, boolean strict) {
        List<BIND> bindEntityList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(bindInfoList)) {
            for (BindInfoReq bindInfo : bindInfoList) {
                BIND bindEntity = (BIND) getBindRepo().selectById(buildBindEntity(bindInfo.getAttachId(), entityId));
                if (FuncUtil.isNotEmpty(bindEntity)) {
                    ReflectionUtil.merge(bindInfo.getData(), bindEntity, !strict);
                    bindEntityList.add(bindEntity);
                } else {
                    bindEntity = buildBindEntity(bindInfo.getAttachId(), entityId);
                    ReflectionUtil.merge(bindInfo.getData(), bindEntity, !strict);
                    getBindRepo().insert(bindEntity);
                }
            }
        }
        getBindRepo().updateById(bindEntityList);
    }
}
