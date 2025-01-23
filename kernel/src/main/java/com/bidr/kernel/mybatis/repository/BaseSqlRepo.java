package com.bidr.kernel.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.inf.*;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.*;
import com.bidr.kernel.vo.query.QueryReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.annotation.CacheConfig;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.bidr.kernel.constant.db.SqlConstant.VALID_FIELD;

/**
 * Title: SqlDeleteRepository
 * Description: Copyright: Copyright (c) 2022 Company: bidr
 *
 * @author Sharp
 */

@CacheConfig(cacheNames = "DB-CACHE", keyGenerator = "cacheKeyByParam")
public class BaseSqlRepo<K extends MyBaseMapper<T>, T> extends BaseMybatisRepo<K, T> implements SqlCountRepo<T>, SqlSelectRepo<T>, SqlInsertRpo<T>, SqlUpdateRepo<T>, SqlDeleteRepo<T>, PortalSelectRepo<T> {

    @Override
    public long count(T entity) {
        QueryWrapper<T> wrapper = getQueryWrapper(entity);
        wrapper.eq(ReflectionUtil.existedField(getEntityClass(), VALID_FIELD), VALID_FIELD, CommonConst.YES);
        return super.getBaseMapper().selectCount(wrapper);
    }

    @Override
    public long count(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        wrapper.eq(ReflectionUtil.existedField(getEntityClass(), VALID_FIELD), VALID_FIELD, CommonConst.YES);
        return super.getBaseMapper().selectCount(wrapper);
    }

    @Override
    public boolean existed(Wrapper<T> wrapper) {
        return super.page(new Page<>(1, 1, false), wrapper).getRecords().size() != 0;
    }

    @Override
    public boolean existed(Map<String, Object> propertyMap) {
        Wrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(1, 1, false), wrapper).getRecords().size() != 0;
    }

    @Override
    public boolean existedById(T entity) {
        if (super.multiIdExisted()) {
            return super.selectByMultiId(entity) != null;
        } else {
            return super.getById(super.getId(entity)) != null;
        }
    }

    @Override
    public boolean existedById(Serializable id) {
        return super.getById(id) != null;
    }

    @Override
    public boolean insert(T entity) {
        return super.save(entity);
    }

    @Override
    public boolean insert(Collection<T> entity) {
        return super.saveBatch(entity);
    }

    @Override
    public List<T> select() {
        return super.list();
    }

    @Override
    public Page<T> select(long currentPage, long pageSize) {
        return super.page(new Page<>(currentPage, pageSize));
    }

    @Override
    public Page<T> select(QueryReqVO req) {
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize()));
    }

    @Override
    public List<T> select(Wrapper<T> wrapper) {
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize) {
        return super.page(new Page<>(currentPage, pageSize), wrapper);
    }

    @Override
    public <VO> Page<VO> select(QueryConditionReq req, Map<String, String> aliasMap, Collection<String> havingFields,
                                MPJLambdaWrapper<T> wrapper, Class<VO> vo) {
        if (FuncUtil.isEmpty(wrapper)) {
            wrapper = new MPJLambdaWrapper<T>(entityClass);
        }
        parseGeneralQuery(req.getConditionList(), aliasMap, havingFields, wrapper);
        parseSort(req.getSortList(), aliasMap, wrapper);
        return selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize()), vo, wrapper);
    }

    @Override
    public <VO> List<VO> select(List<ConditionVO> conditionList, List<SortVO> sortList, Map<String, String> aliasMap,
                                Collection<String> havingFields, MPJLambdaWrapper<T> wrapper, Class<VO> vo) {
        if (FuncUtil.isEmpty(wrapper)) {
            wrapper = new MPJLambdaWrapper<T>(entityClass);
        }
        parseGeneralQuery(conditionList, aliasMap, havingFields, wrapper);
        parseSort(sortList, aliasMap, wrapper);
        return selectJoinList(vo, wrapper);
    }

    @Override
    public <VO> Page<VO> select(AdvancedQueryReq req, Map<String, String> aliasMap, Class<VO> vo) {
        return select(req, aliasMap, null, vo);
    }

    @Override
    public <VO> Page<VO> select(AdvancedQueryReq req, Map<String, String> aliasMap, MPJLambdaWrapper<T> wrapper,
                                Class<VO> vo) {
        if (FuncUtil.isEmpty(wrapper)) {
            wrapper = new MPJLambdaWrapper<T>(entityClass);
        }
        parseAdvancedQuery(req, aliasMap, wrapper);
        return selectJoinListPage(new Page(req.getCurrentPage(), req.getPageSize()), vo, wrapper);
    }

    @Override
    public <VO> List<VO> select(AdvancedQuery condition, List<SortVO> sortList, Map<String, String> aliasMap,
                                MPJLambdaWrapper<T> wrapper, Class<VO> vo) {
        if (FuncUtil.isEmpty(wrapper)) {
            wrapper = new MPJLambdaWrapper<T>(entityClass);
        }
        if (FuncUtil.isNotEmpty(condition)) {
            parseAdvancedQuery(condition, aliasMap, wrapper);
        }
        parseSort(sortList, aliasMap, wrapper);
        return selectJoinList(vo, wrapper);
    }

    @Override
    public Page<T> select(Wrapper<T> wrapper, QueryReqVO req) {
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize()), wrapper);
    }

    @Override
    public Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize, boolean searchCount) {
        return super.page(new Page<>(currentPage, pageSize, searchCount), wrapper);
    }

    @Override
    public Page<T> select(Wrapper<T> wrapper, QueryReqVO req, boolean searchCount) {
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize(), searchCount), wrapper);
    }

    @Override
    public List<T> select(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(Map<String, Object> propertyMap, long currentPage, long pageSize) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(currentPage, pageSize), wrapper);
    }

    @Override
    public Page<T> select(Map<String, Object> propertyMap, QueryReqVO req) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize()), wrapper);
    }

    @Override
    public Page<T> select(Map<String, Object> propertyMap, long currentPage, long pageSize, boolean searchCount) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(currentPage, pageSize, searchCount), wrapper);
    }

    @Override
    public Page<T> select(Map<String, Object> propertyMap, QueryReqVO req, boolean searchCount) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize(), searchCount), wrapper);
    }

    @Override
    public List<T> select(String propertyName, List<?> propertyList) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(String propertyName, List<?> propertyList, long currentPage, long pageSize) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.page(new Page<>(currentPage, pageSize), wrapper);
    }

    @Override
    public Page<T> select(String propertyName, List<?> propertyList, QueryReqVO req) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize()), wrapper);
    }

    @Override
    public Page<T> select(String propertyName, List<?> propertyList, long currentPage, long pageSize,
                          boolean searchCount) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.page(new Page<>(currentPage, pageSize, searchCount), wrapper);
    }

    @Override
    public Page<T> select(String propertyName, List<?> propertyList, QueryReqVO req, boolean searchCount) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.page(new Page<>(req.getCurrentPage(), req.getPageSize(), searchCount), wrapper);
    }

    @Override
    public T selectOne(Wrapper<T> wrapper) {
        return super.getOne(wrapper);
    }

    @Override
    public T selectOne(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.getOne(wrapper);
    }

    @Override
    public T selectById(T entity) {
        if (super.multiIdExisted()) {
            return super.selectByMultiId(entity);
        } else {
            return super.getById(super.getId(entity));
        }
    }

    @Override
    public T selectById(Serializable id) {
        return super.getById(id);
    }

    @Override
    public <VO> VO selectById(Serializable id, MPJLambdaWrapper<T> wrapper, Class<VO> voClass) {
        wrapper.eq(super.getIdField(), id);
        return super.selectJoinOne(voClass, wrapper);
    }

    @Override
    public T selectByMultiId(T entity) {
        return super.selectByMultiId(entity);
    }

    @Override
    public boolean delete(T entity) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(entity);
        return super.remove(wrapper);
    }

    @Override
    public boolean delete(Wrapper<T> wrapper) {
        return super.remove(wrapper);
    }

    @Override
    public boolean delete(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.remove(wrapper);
    }

    @Override
    public boolean delete(List<T> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return false;
        }
        if (super.multiIdExisted()) {
            return super.removeBatchByIds(idList);
        } else {
            if (idList.get(0).getClass().equals(entityClass)) {
                List<Serializable> _idList = new ArrayList<>();
                for (T id : idList) {
                    _idList.add(super.getId(id));
                }
                return super.removeBatchByIds(_idList);
            } else {
                return super.removeBatchByIds(idList);
            }
        }
    }

    @Override
    public boolean deleteById(Serializable id) {
        return removeById(id);
    }

    @Override
    public boolean deleteById(T entity) {
        if (super.multiIdExisted()) {
            return super.deleteByMultiId(entity);
        } else {
            return super.removeById(super.getId(entity));
        }
    }

    @Override
    public boolean disable(T entity) {
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            ReflectionUtil.setValue(entity, VALID_FIELD, CommonConst.NO);
            return updateById(entity);
        }
        return false;
    }

    @Override
    public boolean disable(Wrapper<T> wrapper) {
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            T entity = ReflectionUtil.newInstance(getEntityClass());
            ReflectionUtil.setValue(entity, VALID_FIELD, CommonConst.NO);
            return update(entity, wrapper);
        }
        return false;
    }

    @Override
    public boolean disable(Map<String, Object> propertyMap) {
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            T entity = ReflectionUtil.newInstance(getEntityClass());
            ReflectionUtil.setValue(entity, VALID_FIELD, CommonConst.NO);
            UpdateWrapper<T> wrapper = super.getUpdateWrapperByMap(propertyMap);
            return update(entity, wrapper);
        }
        return false;
    }

    @Override
    public boolean disableById(Serializable id) {
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            T entity = selectById(id);
            ReflectionUtil.setValue(entity, VALID_FIELD, CommonConst.NO);
            return updateById(entity);
        }
        return false;
    }

    @Override
    public boolean disableById(T entity) {
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            if (multiIdExisted()) {
                T newEntity = selectByMultiId(entity);
                ReflectionUtil.setValue(newEntity, VALID_FIELD, CommonConst.NO);
                return updateByMultiId(newEntity);
            } else {
                T newEntity = selectById(entity);
                ReflectionUtil.setValue(newEntity, VALID_FIELD, CommonConst.NO);
                return updateById(newEntity);
            }
        }
        return false;
    }

    @Override
    public boolean updateById(T entity) {
        return updateById(entity, true);
    }

    @Override
    public boolean updateById(T entity, boolean ignoreNull) {
        UpdateWrapper<T> wrapper = super.getIdWrapper(entity);
        return update(entity, wrapper, ignoreNull);
    }

    @Override
    public boolean updateById(List<T> entityList) {
        if (super.multiIdExisted()) {
            return super.updateBatchByMultiId(entityList);
        } else {
            return super.updateBatchById(entityList);
        }
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper) {
        return update(entity, wrapper, true);
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper, boolean ignoreNull) {
        if (!ignoreNull) {
            super.fillUpdateWrapper(entity, wrapper);
            return super.update(wrapper);
        }
        return super.update(entity, wrapper);
    }

    @Override
    public boolean update(T entity, Map<String, Object> propertyMap) {
        return update(entity, propertyMap, true);
    }

    @Override
    public boolean update(T entity, Map<String, Object> propertyMap, boolean ignoreNull) {
        UpdateWrapper<T> wrapper = super.getUpdateWrapperByMap(propertyMap);
        if (!ignoreNull) {
            super.fillUpdateWrapper(entity, wrapper);
        }
        return update(entity, wrapper);
    }

    @Override
    public boolean insertOrUpdate(T entity, UpdateWrapper<T> wrapper) {
        if (wrapper == null) {
            if (super.multiIdExisted()) {
                return super.saveOrUpdateByMultiId(entity);
            } else {
                return super.saveOrUpdate(entity);
            }
        } else {
            return super.saveOrUpdate(entity, wrapper);
        }
    }

    @Override
    public boolean insertOrUpdate(T entity) {
        return insertOrUpdate(entity, null);
    }

    @Override
    public boolean insertOrUpdate(Collection<T> entity) {
        if (super.multiIdExisted()) {
            return super.saveOrUpdateBatchByMultiId(entity);
        } else {
            return super.saveOrUpdateBatch(entity);
        }
    }

    @Override
    public boolean insertOrUpdate(T entity, SFunction<T, ?> field, SFunction<T, ?>... fields) {
        LambdaUpdateWrapper<T> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(field, field.apply(entity));
        if (FuncUtil.isNotEmpty(fields)) {
            for (SFunction<T, ?> f : fields) {
                wrapper.eq(f, f.apply(entity));
            }
        }
        return saveOrUpdate(entity, wrapper);
    }

    @Override
    public long count(Wrapper<T> wrapper) {
        return super.getBaseMapper().selectCount(wrapper);
    }
}
