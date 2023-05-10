package com.bidr.kernel.mybatis.dao.repository;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.dao.mapper.RecursionDao;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public <T> List getChildList(SFunction<T, ?> idFunc, SFunction<T, ?> pidFunc, Object id) {
        Class<T> clazz = LambdaUtil.getRealClass(idFunc);
        TableName tableNameAnno = clazz.getAnnotation(TableName.class);
        Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "不是有效的数据库表实体");
        String tableName = tableNameAnno.value();
        String idFieldName = LambdaUtil.getFieldName(idFunc);
        String pidFieldName = LambdaUtil.getFieldName(pidFunc);
        return recursionDao.getChildList(tableName, idFieldName, pidFieldName, id);
    }

    public <T> List getParentList(SFunction<T, ?> idFunc, SFunction<T, ?> pidFunc, Object id) {
        Class<T> clazz = LambdaUtil.getRealClass(idFunc);
        TableName tableNameAnno = clazz.getAnnotation(TableName.class);
        Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "不是有效的数据库表实体");
        String tableName = tableNameAnno.value();
        String idFieldName = LambdaUtil.getFieldName(idFunc);
        String pidFieldName = LambdaUtil.getFieldName(pidFunc);
        return recursionDao.getParentList(tableName, idFieldName, pidFieldName, id);
    }
}
