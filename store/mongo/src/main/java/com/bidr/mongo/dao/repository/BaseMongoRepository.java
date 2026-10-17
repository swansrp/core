package com.bidr.mongo.dao.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.utils.ReflectionUtil;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Title: BaseMongoRepository
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 11:19
 */

public class BaseMongoRepository<T> {
    private final Class<T> clazz = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    @Resource
    private MongoTemplate mongoTemplate;

    public void insert(T entity) {
        mongoTemplate.insert(entity);
    }

    public void batchInsert(Collection<T> entityList) {
        mongoTemplate.insertAll(entityList);
    }

    public List<T> select(Query query) {
        return mongoTemplate.find(query, clazz);
    }

    public Page<T> select(Query query, int currentPageNumber, int pageSize) {
        Page<T> page = new Page<>(currentPageNumber, pageSize);
        pageHelper(query, currentPageNumber, pageSize);
        long count = mongoTemplate.count(query, clazz);
        page.setTotal(count);
        if (count != 0) {
            List<T> list = mongoTemplate.find(query, clazz);
            page.setRecords(list);
        }
        return page;
    }

    public Query pageHelper(Query query, int page, int pageSize) {
        if (query == null) {
            query = new Query();
        }
        return query.with(PageRequest.of(page - 1, pageSize));
    }

    public List<T> select(String fieldName, Object value) {
        Query query = new Query(Criteria.where(fieldName).is(value));
        return mongoTemplate.find(query, clazz);
    }

    public List<T> select(Map<String, Object> properties) {
        Query query = buildQuery(properties);
        return mongoTemplate.find(query, clazz);
    }

    public Query buildQuery(Map<String, Object> properties) {
        Query query = new Query();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            query.addCriteria(Criteria.where(entry.getKey()).is(entry.getValue()));
        }
        return query;
    }

    public T selectById(Object id) {
        return mongoTemplate.findById(id, clazz);
    }

    public List<T> selectAll() {
        return mongoTemplate.findAll(clazz);
    }

    public UpdateResult update(Query query, T entity) {
        Update update = buildUpdate(entity);
        return mongoTemplate.updateMulti(query, update, clazz);
    }

    private Update buildUpdate(T entity) {
        return buildUpdate(entity, false);
    }

    private Update buildUpdate(T entity, boolean selective) {
        Update update = new Update();
        Map<String, Object> map = ReflectionUtil.getHashMap(entity);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null || !selective) {
                update.set(entry.getKey(), entry.getValue());
            }
        }
        return update;
    }

    public UpdateResult updateById(T entity) {
        Query query = buildQueryById(entity);
        Update update = buildUpdate(entity);
        return mongoTemplate.updateMulti(query, update, clazz);
    }

    private Query buildQueryById(T entity) {
        Query query = new Query();
        List<Field> declaredFields = ReflectionUtil.getFields(entity);
        for (Field field : declaredFields) {
            String name = field.getName();

            Object value = null;
            value = ReflectionUtil.getValue(entity, field);
            Id idAnnotation = field.getAnnotation(Id.class);
            if (idAnnotation != null) {
                query.addCriteria(Criteria.where(name).is(value));
            }
        }
        return query;
    }

    public UpdateResult updateSelective(Query query, T entity) {
        Update update = buildUpdate(entity, true);
        return mongoTemplate.updateMulti(query, update, clazz);
    }

    public UpdateResult updateSelectiveById(T entity) {
        Query query = buildQueryById(entity);
        Update update = buildUpdate(entity, true);
        return mongoTemplate.updateMulti(query, update, clazz);
    }

    public UpdateResult insertOrUpdate(Query query, T entity) {
        Update update = buildUpdate(entity);
        return mongoTemplate.upsert(query, update, clazz);
    }

    public UpdateResult insertOrUpdateById(T entity) {
        Update update = buildUpdate(entity);
        Query query = buildQueryById(entity);
        return mongoTemplate.upsert(query, update, clazz);
    }

    public UpdateResult insertOrUpdateSelective(Query query, T entity) {
        Update update = buildUpdate(entity, true);
        return mongoTemplate.upsert(query, update, clazz);
    }

    public UpdateResult insertOrUpdateSelectiveById(T entity) {
        Update update = buildUpdate(entity, true);
        Query query = buildQueryById(entity);
        return mongoTemplate.upsert(query, update, clazz);
    }

    public DeleteResult delete(Query query) {
        return mongoTemplate.remove(query, clazz);
    }

    public DeleteResult deleteById(T entity) {
        Query query = buildQueryById(entity);
        return mongoTemplate.remove(query, clazz);
    }

    public long count(Query query) {
        return mongoTemplate.count(query, clazz);
    }

    public long count(String fieldName, Object value) {
        Query query = new Query(Criteria.where(fieldName).is(value));
        return mongoTemplate.count(query, clazz);
    }

    public long count(Map<String, Object> properties) {
        Query query = buildQuery(properties);
        return mongoTemplate.count(query, clazz);
    }

    public boolean exists(Query query) {
        return mongoTemplate.exists(query, clazz);
    }

    public boolean exists(String fieldName, Object value) {
        Query query = new Query(Criteria.where(fieldName).is(value));
        return mongoTemplate.exists(query, clazz);
    }

    public boolean exists(Map<String, Object> properties) {
        Query query = buildQuery(properties);
        return mongoTemplate.exists(query, clazz);
    }

    public Query sortHelper(Query query, Sort.Direction desc, String... fieldName) {
        if (query == null) {
            query = new Query();
        }
        return query.with(Sort.by(desc, fieldName));
    }
}
