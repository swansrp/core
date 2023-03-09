package com.bidr.kernel.mybatis.repository;

import com.bidr.kernel.mybatis.repository.inf.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.inf.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.annotation.CacheConfig;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Title: SqlDeleteRepository
 * Description: Copyright: Copyright (c) 2022 Company: bidr
 *
 * @author Sharp
 */

@CacheConfig(cacheNames = "DB-CACHE", keyGenerator = "cacheKeyByParam")
public class BaseSqlRepo<K extends MyBaseMapper<T>, T> extends BaseMybatisRepo<K, T> implements SqlCountRepo<T>,
        SqlSelectRepo<T>, SqlInsertRpo<T>, SqlUpdateRepo<T>, SqlDeleteRepo<T> {

    @Override
    public long count(T entity) {
        Wrapper<T> wrapper = getQueryWrapper(entity);
        return super.getBaseMapper().selectCount(wrapper);
    }

    @Override
    public long count(Map<String, Object> propertyMap) {
        Wrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
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
    public Page<T> select(int currentPage, int pageSize) {
        return super.page(new Page<>(currentPage, pageSize));
    }

    @Override
    public List<T> select(Wrapper<T> wrapper) {
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(Wrapper<T> wrapper, int currentPage, int pageSize) {
        return super.page(new Page<>(currentPage, pageSize), wrapper);
    }

    @Override
    public List<T> select(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(Map<String, Object> propertyMap, int currentPage, int pageSize) {
        QueryWrapper<T> wrapper = super.getQueryWrapperByMap(propertyMap);
        return super.page(new Page<>(currentPage, pageSize), wrapper);
    }

    @Override
    public List<T> select(String propertyName, List<?> propertyList) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.list(wrapper);
    }

    @Override
    public Page<T> select(String propertyName, List<?> propertyList, int currentPage, int pageSize) {
        QueryWrapper<T> wrapper = super.getQueryWrapper(propertyName, propertyList);
        return super.page(new Page<>(currentPage, pageSize), wrapper);
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
    public boolean deleteByIdList(List<T> idList) {
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
    public boolean updateById(T entity) {
        return updateById(entity, true);
    }

    @Override
    public long count(Wrapper<T> wrapper) {
        return super.getBaseMapper().selectCount(wrapper);
    }

    @Override
    public boolean updateById(T entity, boolean ignoreNull) {
        UpdateWrapper<T> wrapper = super.getIdWrapper(entity);
        return update(entity, wrapper, ignoreNull);
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper) {
        return update(entity, wrapper, true);
    }

    @Override
    public boolean update(T entity, UpdateWrapper<T> wrapper, boolean ignoreNull) {
        if (!ignoreNull) {
            super.fillUpdateWrapper(entity, wrapper);
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
        if (super.multiIdExisted()) {
            return super.saveOrUpdateByMultiId(entity);
        } else {
            return super.saveOrUpdate(entity, wrapper);
        }
    }

    @Override
    public boolean insertOrUpdate(Collection<T> entity, UpdateWrapper<T> wrapper) {
        return false;
    }


}
