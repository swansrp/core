package com.bidr.kernel.mybatis.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.bind.QueryBindReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
public abstract class BaseBindRepo<ENTITY, BIND, ATTACH> {
    @Resource
    private ApplicationContext applicationContext;

    public IPage<ATTACH> getBindList(QueryBindReq req) {
        MPJLambdaWrapper<ATTACH> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.leftJoin(getBindClass(), bindAttachId(), attachId()).eq(bindEntityId(), req.getEntityId());
        return attachRepo().selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize(), false),
                getAttachClass(), wrapper);
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

    protected BaseSqlRepo attachRepo() {
        return (BaseSqlRepo) applicationContext.getBean(
                StrUtil.lowerFirst(getAttachClass().getSimpleName()) + "Service");
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
    public void bind(Object masterId, Object salveId) {
        BIND bindEntity = buildBindEntity(masterId, salveId);
        bindRepo().insertOrUpdate(bindEntity);
    }

    private BIND buildBindEntity(Object masterId, Object salveId) {
        BIND bindEntity = ReflectionUtil.newInstance(getBindClass());
        Object masterIdValue = LambdaUtil.getValue(bindAttachId(), masterId);
        String masterIdField = LambdaUtil.getFieldName(bindAttachId());
        Object slaveIdValue = LambdaUtil.getValue(bindEntityId(), salveId);
        String slaveIdField = LambdaUtil.getFieldName(bindEntityId());
        ReflectionUtil.setValue(bindEntity, masterIdField, masterIdValue);
        ReflectionUtil.setValue(bindEntity, slaveIdField, slaveIdValue);
        return bindEntity;
    }

    protected BaseSqlRepo bindRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(getBindClass().getSimpleName()) + "Service");
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Object masterId, Object salveId) {
        BIND bindEntity = buildBindEntity(masterId, salveId);
        bindRepo().deleteById(bindEntity);
    }

    public List<ENTITY> getBindSlaveList(Object masterId) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.leftJoin(getBindClass(), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), entityId(), bindEntityId()).eq(bindEntityId(), masterId);
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
}
