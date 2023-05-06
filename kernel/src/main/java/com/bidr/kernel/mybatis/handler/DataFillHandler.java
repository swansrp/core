package com.bidr.kernel.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import com.bidr.kernel.mybatis.anno.AutoUpdate;
import com.bidr.kernel.mybatis.dao.mapper.SequenceDao;
import com.bidr.kernel.utils.FuncUtil;
import com.github.jeffreyning.mybatisplus.util.PlusACUtils;
import com.github.jeffreyning.mybatisplus.util.ReadValueUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Title: DataFillHandler
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/28 11:22
 */
@Slf4j
@Component
public class DataFillHandler implements MetaObjectHandler {

    @Lazy
    @Resource
    private SequenceDao sequenceDao;

    @Override
    public void insertFill(MetaObject metaObject) {
        Object classObj = metaObject.getOriginalObject();
        Field[] declaredFields = classObj.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
            AutoInsert insert = field.getAnnotation(AutoInsert.class);
            if (insert != null) {
                if (FuncUtil.isNotEmpty(metaObject.getValue(field.getName()))) {
                    if (!insert.override()) {
                        continue;
                    }
                }
                handleSql(insert.sql(), field, metaObject);
                handleSeq(insert.seq(), field, metaObject);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Object classObj = metaObject.getOriginalObject();
        Field[] declaredFields = classObj.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
            AutoUpdate update = field.getAnnotation(AutoUpdate.class);
            if (update != null) {
                handleSql(update.sql(), field, metaObject);
            }
        }
    }

    private void handleSql(String sql, Field field, MetaObject metaObject) {
        String fieldName = field.getName();
        if (sql != null && !"".equals(sql)) {
            SqlSessionTemplate sqlSessionTemplate = PlusACUtils.getBean(SqlSessionTemplate.class);
            SqlSession sqlSession = null;
            try {
                sqlSession = SqlSessionUtils.getSqlSession(sqlSessionTemplate.getSqlSessionFactory(),
                        sqlSessionTemplate.getExecutorType(), sqlSessionTemplate.getPersistenceExceptionTranslator());
                ResultSet resultSet = sqlSession.getConnection().createStatement().executeQuery(sql);
                if (resultSet != null) {
                    if (resultSet.next()) {
                        Class fieldType = field.getType();
                        Object fieldVal = ReadValueUtil.readValue(resultSet, fieldType);
                        this.fillStrategy(metaObject, fieldName, fieldVal);
                    }
                }
            } catch (SQLException e) {
                log.error("fill error", e);
                throw new RuntimeException("fill error", e);
            } finally {
                if (sqlSession != null) {
                    SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionTemplate.getSqlSessionFactory());
                }
            }
        }
    }

    private void handleSeq(String sequenceName, Field field, MetaObject metaObject) {
        String fieldName = field.getName();
        if (FuncUtil.isNotEmpty(sequenceName)) {
            String seq = sequenceDao.getSeq(sequenceName);
            this.fillStrategy(metaObject, fieldName, seq);
        }
    }
}

