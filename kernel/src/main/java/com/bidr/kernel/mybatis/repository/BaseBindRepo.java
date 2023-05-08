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
public abstract class BaseBindRepo<MASTER, BIND> {

    private final Class<MASTER> masterClass = (Class<MASTER>) ReflectionUtil.getSuperClassGenericType(this.getClass(),
            0);
    private final Class<BIND> bindClass = (Class<BIND>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    @Resource
    private ApplicationContext applicationContext;

    public IPage<MASTER> getBindList(QueryBindReq req) {
        MPJLambdaWrapper<MASTER> wrapper = new MPJLambdaWrapper<>(masterClass);
        wrapper.leftJoin(bindClass, bindMasterId(), masterId()).eq(bindSlaveId(), req.getSalveId());
        return masterRepo().selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize(), false), masterClass,
                wrapper);
    }

    protected abstract SFunction<BIND, ?> bindMasterId();

    protected abstract SFunction<MASTER, ?> masterId();

    protected abstract SFunction<BIND, ?> bindSlaveId();

    protected BaseSqlRepo masterRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(masterClass.getSimpleName()) + "Service");
    }

    public IPage<MASTER> getUnbindUserList(QueryBindReq req) {
        MPJLambdaWrapper<MASTER> wrapper = new MPJLambdaWrapper<>(masterClass).distinct();
        wrapper.leftJoin(bindClass, bindMasterId(), masterId());
        masterRepo().parseQueryCondition(req, wrapper);
        wrapper.and(w -> w.ne(bindSlaveId(), req.getSalveId()).or(ww -> ww.isNull(bindSlaveId())));
        masterRepo().parseSort(req, wrapper);
        return masterRepo().selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize(), false), masterClass,
                wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(List<?> masterIdList, Object salveId) {
        List<BIND> list = new ArrayList<>();
        for (Object masterId : masterIdList) {
            BIND bindEntity = buildBindEntity(masterId, salveId);
            list.add(bindEntity);
        }
        bindRepo().saveBatch(list);
    }

    private BIND buildBindEntity(Object masterId, Object salveId) {
        BIND bindEntity = ReflectionUtil.newInstance(bindClass);
        Object masterIdValue = LambdaUtil.getValue(bindMasterId(), masterId);
        String masterIdField = LambdaUtil.getFieldName(bindMasterId());
        Object slaveIdValue = LambdaUtil.getValue(bindSlaveId(), salveId);
        String slaveIdField = LambdaUtil.getFieldName(bindSlaveId());
        ReflectionUtil.setValue(bindEntity, masterIdField, masterIdValue);
        ReflectionUtil.setValue(bindEntity, slaveIdField, slaveIdValue);
        return bindEntity;
    }

    protected BaseSqlRepo bindRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(bindClass.getSimpleName()) + "Service");
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(List<?> masterIdList, Object salveId) {
        for (Object masterId : masterIdList) {
            BIND bindEntity = buildBindEntity(masterId, salveId);
            bindRepo().deleteById(bindEntity);
        }
    }
}
