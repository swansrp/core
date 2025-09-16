package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.bidr.kernel.mybatis.dao.repository.SaSequenceService;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * Title: SequenceKey
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/28 17:11
 */
@Component
public class SequenceKey implements IdentifierGenerator {

    @Autowired
    @Lazy
    private SaSequenceService sequenceService;

    @Override
    public Number nextId(Object entity) {
        try {
            String sequence = getSeqName(entity);
            return new BigDecimal(sequence);
        } catch (Exception e) {
            UUID uuid = UUID.randomUUID();
            // 转换为正数的 BigInteger
            return new BigInteger(uuid.toString().replace("-", ""), 16);
        }
    }

    private String getSeqName(Object entity) {
        List<Field> fields = ReflectionUtil.getFields(entity.getClass());
        String tableName = entity.getClass().getAnnotation(TableName.class).value();
        String columnName = "";
        for (Field field : fields) {
            if (field.isAnnotationPresent(TableId.class)) {
                columnName = field.getAnnotation(TableId.class).value();
            }
        }
        String seqName = tableName + "_" + columnName + "_SEQ";
        return sequenceService.getSeq(seqName.toUpperCase());
    }

    @Override
    public String nextUUID(Object entity) {
        try {
            return getSeqName(entity);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
