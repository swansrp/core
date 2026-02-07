package com.bidr.kernel.mybatis.dao.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.config.db.MybatisPlusConfig;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.dao.mapper.RecursionDao;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: RecursionService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/10 15:38
 */
@Service
@RequiredArgsConstructor
public class RecursionService {
    private final RecursionDao recursionDao;

    @SuppressWarnings("unchecked")
    public <T, R, K extends MyBaseMapper<T>> List<R> getChildList(SFunction<T, R> idFunc, SFunction<T, R> pidFunc, R id) {
        Class<T> clazz = LambdaUtil.getRealClass(idFunc);
        List<R> result = new ArrayList<>();
        if (MybatisPlusConfig.SUPPORT_RECURSIVE) {
            TableName tableNameAnno = clazz.getAnnotation(TableName.class);
            Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "不是有效的数据库表实体");
            String tableName = tableNameAnno.value();

            Field idField = LambdaUtil.getField(idFunc);
            String idFieldName = idField.getName();
            TableId idAnno = idField.getAnnotation(TableId.class);
            TableField idFieldAnno = idField.getAnnotation(TableField.class);
            if (idFieldAnno != null) {
                idFieldName = idAnno.value();
            }
            if (idFieldAnno != null) {
                idFieldName = idFieldAnno.value();
            }

            Field pidField = LambdaUtil.getField(pidFunc);
            String pidFieldName = pidField.getName();
            TableField pidAnno = idField.getAnnotation(TableField.class);
            if (pidAnno != null) {
                pidFieldName = pidAnno.value();
            }
            result = JsonUtil.readJson(recursionDao.getChildList(tableName, idFieldName, pidFieldName, id), List.class, id.getClass());
        } else {
            BaseSqlRepo<K, T> service = (BaseSqlRepo<K, T>) BeanUtil.getBean(Introspector.decapitalize(clazz.getSimpleName()) + "Service");
            if (service != null) {
                List<R> pidList = new ArrayList<>();
                pidList.add(id);
                List<T> list;
                while (FuncUtil.isNotEmpty(pidList)) {
                    LambdaQueryWrapper<T> wrapper = service.getQueryWrapper();
                    wrapper.in(pidFunc, pidList);
                    list = service.select(wrapper);
                    pidList.clear();
                    if (FuncUtil.isNotEmpty(list)) {
                        for (T o : list) {
                            result.add(idFunc.apply(o));
                            pidList.add(idFunc.apply(o));
                        }
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T, R, K extends MyBaseMapper<T>> List<R> getParentList(SFunction<T, R> idFunc, SFunction<T, R> pidFunc, R id) {
        Class<T> clazz = LambdaUtil.getRealClass(idFunc);
        List<R> result = new ArrayList<>();
        if (MybatisPlusConfig.SUPPORT_RECURSIVE) {
            TableName tableNameAnno = clazz.getAnnotation(TableName.class);
            Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "不是有效的数据库表实体");
            String tableName = tableNameAnno.value();
            Field idField = LambdaUtil.getField(idFunc);
            String idFieldName = idField.getName();
            TableId idAnno = idField.getAnnotation(TableId.class);
            TableField idFieldAnno = idField.getAnnotation(TableField.class);
            if (idFieldAnno != null) {
                idFieldName = idAnno.value();
            }
            if (idFieldAnno != null) {
                idFieldName = idFieldAnno.value();
            }

            Field pidField = LambdaUtil.getField(pidFunc);
            String pidFieldName = pidField.getName();
            TableField pidAnno = idField.getAnnotation(TableField.class);
            if (pidAnno != null) {
                pidFieldName = pidAnno.value();
            }
            result = recursionDao.getParentList(tableName, idFieldName, pidFieldName, id);
        } else {
            BaseSqlRepo<K, T> service = (BaseSqlRepo<K, T>) BeanUtil.getBean(Introspector.decapitalize(clazz.getSimpleName()) + "Service");
            if (service != null) {
                T o = service.selectById(id.toString());
                R pid = pidFunc.apply(o);
                while (FuncUtil.isNotEmpty(pid)) {
                    o = service.selectById(pid.toString());
                    pid = pidFunc.apply(o);
                    result.add(idFunc.apply(o));
                }
            }
        }
        return result;

    }
}
