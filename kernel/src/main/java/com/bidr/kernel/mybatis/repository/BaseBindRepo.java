package com.bidr.kernel.mybatis.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.bind.QueryBindReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
        wrapper.selectAll(getAttachClass()).select(bindEntityId()).leftJoin(getBindClass(), bindAttachId(), attachId())
                .eq(bindEntityId(), entityId);
        return attachRepo().selectJoinList(getAttachVOClass(), wrapper);
    }

    protected Class<ATTACH> getAttachClass() {
        return (Class<ATTACH>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
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

    public IPage<ATTACH_VO> queryBindList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectAll(getAttachClass()).select(bindEntityId()).leftJoin(getBindClass(), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getEntityId());
        return attachRepo().selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize(), false),
                getAttachVOClass(), wrapper);
    }

    public IPage<ATTACH> getUnbindList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass()).distinct();
        wrapper.leftJoin(getBindClass(), bindAttachId(), attachId());
        attachRepo().parseQueryCondition(req, wrapper);
        wrapper.and(w -> w.ne(bindEntityId(), req.getEntityId()).or(ww -> ww.isNull(bindEntityId())));
        attachRepo().parseSort(req, wrapper);
        return attachRepo().selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize(), false),
                getAttachClass(), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(Object attachId, Object entityId) {
        BIND bindEntity = buildBindEntity(attachId, entityId);
        bindRepo().insertOrUpdate(bindEntity);
    }

    private BIND buildBindEntity(Object attachId, Object entityId) {
        BIND bindEntity = ReflectionUtil.newInstance(getBindClass());
        LambdaUtil.setValue(bindEntity, bindAttachId(), attachId);
        LambdaUtil.setValue(bindEntity, bindEntityId(), entityId);
        return bindEntity;
    }

    protected BaseSqlRepo bindRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(getBindClass().getSimpleName()) + "Service");
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Object attachId, Object entityId) {
        BIND bindEntity = buildBindEntity(attachId, entityId);
        bindRepo().deleteById(bindEntity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindList(List<Object> attachIdList, Object entityId) {
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                bindRepo().deleteById(bindEntity);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(List<Object> attachIdList, Object entityId) {
        Wrapper<BIND> wrapper = (Wrapper<BIND>) bindRepo().getQueryWrapper().eq(bindEntityId(), entityId);
        bindRepo().delete(wrapper);
        List<BIND> bindList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                bindList.add(bindEntity);
            }
        }
        bindRepo().saveBatch(bindList);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindList(List<Object> attachIdList, Object entityId) {
        if (FuncUtil.isNotEmpty(attachIdList)) {
            for (Object attachId : attachIdList) {
                BIND bindEntity = buildBindEntity(attachId, entityId);
                bindRepo().insertOrUpdate(bindEntity);
            }
        }
    }

    public List<ENTITY> getBindEntityList(Object attachId) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.leftJoin(getBindClass(), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), entityId(), bindEntityId()).eq(bindAttachId(), attachId);
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
}
