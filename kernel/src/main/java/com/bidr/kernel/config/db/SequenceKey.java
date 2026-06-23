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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: SequenceKey
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/28 17:11
 */
@Component
public class SequenceKey implements IdentifierGenerator {

    /**
     * 缓存实体类对应的序列名，Optional.empty() 表示该实体类无有效序列
     */
    private final ConcurrentHashMap<Class<?>, Optional<String>> seqNameCache = new ConcurrentHashMap<>();
    @Autowired
    @Lazy
    private SaSequenceService sequenceService;

    @Override
    public Number nextId(Object entity) {
        Optional<String> seqNameOpt = seqNameCache.computeIfAbsent(entity.getClass(), this::resolveSeqName);
        if (seqNameOpt.isPresent()) {
            String sequence = sequenceService.getSeq(seqNameOpt.get());
            return new BigDecimal(sequence);
        }
        UUID uuid = UUID.randomUUID();
        // 转换为正数的 BigInteger
        return new BigInteger(uuid.toString().replace("-", ""), 16);
    }

    @Override
    public String nextUUID(Object entity) {
        Optional<String> seqNameOpt = seqNameCache.computeIfAbsent(entity.getClass(), this::resolveSeqName);
        if (seqNameOpt.isPresent()) {
            return sequenceService.getSeq(seqNameOpt.get());
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析实体类的序列名，若序列不存在则返回 Optional.empty()
     */
    private Optional<String> resolveSeqName(Class<?> entityClass) {
        try {
            String tableName = entityClass.getAnnotation(TableName.class).value();
            List<Field> fields = ReflectionUtil.getFields(entityClass);
            String columnName = "";
            for (Field field : fields) {
                if (field.isAnnotationPresent(TableId.class)) {
                    columnName = field.getAnnotation(TableId.class).value();
                }
            }
            String seqName = (tableName + "_" + columnName + "_SEQ").toUpperCase();
            if (sequenceService.existedById(seqName)) {
                return Optional.of(seqName);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
