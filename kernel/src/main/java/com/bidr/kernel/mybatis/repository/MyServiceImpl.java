package com.bidr.kernel.mybatis.repository;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.diboot.core.service.BaseService;
import com.diboot.core.service.impl.BaseServiceImpl;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import com.github.jeffreyning.mybatisplus.service.IMppService;
import com.github.yulichang.base.service.MPJJoinService;
import org.apache.ibatis.binding.MapperMethod;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Title: MyServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/02/16 15:05
 */
public class MyServiceImpl<K extends MyBaseMapper<T>, T> extends BaseServiceImpl<K, T> implements IMppService<T>,
        MPJJoinService<T>, BaseService<T> {

    private String getCol(List<TableFieldInfo> fieldList, String attrName) {
        Iterator var3 = fieldList.iterator();

        TableFieldInfo tableFieldInfo;
        String prop;
        do {
            if (!var3.hasNext()) {
                throw new RuntimeException("not found column for " + attrName);
            }

            tableFieldInfo = (TableFieldInfo)var3.next();
            prop = tableFieldInfo.getProperty();
        } while(!prop.equals(attrName));

        return tableFieldInfo.getColumn();
    }

    private Map checkIdCol(Class<?> modelClass, TableInfo tableInfo) {
        List<TableFieldInfo> fieldList = tableInfo.getFieldList();
        Map<String, String> idMap = new HashMap();
        Iterator var5 = fieldList.iterator();

        while(var5.hasNext()) {
            TableFieldInfo fieldInfo = (TableFieldInfo)var5.next();
            Field field = fieldInfo.getField();
            MppMultiId mppMultiId = (MppMultiId)field.getAnnotation(MppMultiId.class);
            if (mppMultiId != null) {
                String attrName = field.getName();
                String colName = this.getCol(fieldList, attrName);
                idMap.put(attrName, colName);
            }
        }

        return idMap;
    }

    public boolean saveOrUpdateByMultiId(T entity) {
        if (null == entity) {
            return false;
        } else {
            Class<?> cls = entity.getClass();
            TableInfo tableInfo = TableInfoHelper.getTableInfo(cls);
            Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!", new Object[0]);
            Map<String, String> idMap = this.checkIdCol(cls, tableInfo);
            Assert.notEmpty(idMap, "entity {} not contain MppMultiId anno", new Object[]{cls.getName()});
            boolean updateFlag = true;
            Iterator var6 = idMap.keySet().iterator();

            while(var6.hasNext()) {
                String attr = (String)var6.next();
                if (com.baomidou.mybatisplus.core.toolkit.StringUtils.checkValNull(attr)) {
                    updateFlag = false;
                    break;
                }
            }

            if (updateFlag) {
                Object obj = this.selectByMultiId(entity);
                if (Objects.isNull(obj)) {
                    updateFlag = false;
                }
            }

            return updateFlag ? this.updateByMultiId(entity) : this.save(entity);
        }
    }

    public boolean saveOrUpdateBatchByMultiId(Collection<T> entityList, int batchSize) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(this.entityClass);
        Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!", new Object[0]);
        Map<String, String> idMap = this.checkIdCol(this.entityClass, tableInfo);
        Assert.notEmpty(idMap, "entity {} not contain MppMultiId anno", new Object[]{this.entityClass.getName()});
        return this.executeBatch(entityList, batchSize, (sqlSession, entity) -> {
            boolean updateFlag = true;
            Iterator var6 = idMap.keySet().iterator();

            while(var6.hasNext()) {
                String attr = (String)var6.next();
                if (com.baomidou.mybatisplus.core.toolkit.StringUtils.checkValNull(attr)) {
                    updateFlag = false;
                    break;
                }
            }

            if (updateFlag) {
                Object obj = this.selectByMultiId(entity);
                if (Objects.isNull(obj)) {
                    updateFlag = false;
                }
            }

            if (updateFlag) {
                MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap();
                param.put("et", entity);
                sqlSession.update(tableInfo.getSqlStatement("updateByMultiId"), param);
            } else {
                sqlSession.insert(tableInfo.getSqlStatement(SqlMethod.INSERT_ONE.getMethod()), entity);
            }

        });
    }

    public boolean updateBatchByMultiId(Collection<T> entityList, int batchSize) {
        String sqlStatement = SqlHelper.table(this.entityClass).getSqlStatement("updateByMultiId");
        return this.executeBatch(entityList, batchSize, (sqlSession, entity) -> {
            MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap();
            param.put("et", entity);
            sqlSession.update(sqlStatement, param);
        });
    }

}
