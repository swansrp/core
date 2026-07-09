package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BindRepo} 注解的通用处理器。
 * <p>
 * 启动时扫描所有 {@link BaseSqlRepo} Bean，按 entityClass 建立索引。
 * 运行时根据注解上的 entity() 自动定位 Repo，执行批量查询 + 字段提取。
 * <p>
 * 一个 Handler 处理所有 @BindRepo 注解（包括 @BindUser、@BindDept 等通过元注解桥接的快捷注解），
 * 无需为每种实体编写子类。
 *
 * @author Sharp
 * @since 2026/07/09
 */
@Slf4j
@Component
public class BindRepoHandler {

    private static final String VALID_FIELD = "valid";

    /**
     * entityClass → BaseSqlRepo 的索引，延迟初始化
     */
    private volatile Map<Class<?>, BaseSqlRepo<?, ?>> repoIndex;

    /**
     * 批量转换：根据 @BindRepo 注解配置，查询实体并提取目标字段值。
     *
     * @param annotation   字段上的 @BindRepo 注解（可能是 @BindUser/@BindDept 通过 findMergedAnnotation 合成的）
     * @param sourceValues 去重后的源字段值集合
     * @return 源值→目标值 的映射
     */
    public Map<Object, Object> batchConvert(BindRepo annotation, Set<Object> sourceValues) {
        Map<Object, Object> result = new HashMap<>();
        if (FuncUtil.isEmpty(sourceValues)) {
            return result;
        }

        Class<?> entityClass = annotation.entity();
        BaseSqlRepo<?, ?> repo = getRepo(entityClass);
        if (repo == null) {
            log.warn("@BindRepo: 未找到 {} 对应的 BaseSqlRepo Bean，跳过绑定", entityClass.getName());
            return result;
        }

        String matchField = annotation.matchField();
        String extractField = annotation.extractField();
        String matchColumn = resolveColumnName(entityClass, matchField);

        // 构建 WHERE matchColumn IN (sourceValues) [AND valid = '1']
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.in(matchColumn, sourceValues);
        if (ReflectionUtil.existedField(entityClass, VALID_FIELD)) {
            String validColumn = resolveColumnName(entityClass, VALID_FIELD);
            wrapper.eq(validColumn, CommonConst.YES);
        }

        // 批量查询（通过反射调用 select(Wrapper) 绕过泛型约束）
        // 注意：必须用 Wrapper.class 查找方法，不能用 param.getClass()（QueryWrapper.class），
        //       因为 ReflectionUtils.findMethod 做精确类型匹配，select 的参数类型是 Wrapper 而非 QueryWrapper
        Method selectMethod = ReflectionUtil.getMethod(repo.getClass(), "select", Wrapper.class);
        if (selectMethod == null) {
            log.warn("@BindRepo: 未找到 {} 的 select(Wrapper) 方法，跳过绑定", entityClass.getName());
            return result;
        }
        List<?> entities = (List<?>) ReflectionUtil.invoke(repo, selectMethod, wrapper);
        if (FuncUtil.isNotEmpty(entities)) {
            for (Object entity : entities) {
                Object matchValue = ReflectionUtil.getValue(entity, matchField, Object.class);
                Object extractValue = ReflectionUtil.getValue(entity, extractField, Object.class);
                result.put(matchValue, extractValue);
            }
        }
        return result;
    }

    /**
     * 获取实体类对应的 BaseSqlRepo，延迟初始化索引
     */
    private BaseSqlRepo<?, ?> getRepo(Class<?> entityClass) {
        if (repoIndex == null) {
            synchronized (this) {
                if (repoIndex == null) {
                    Map<Class<?>, BaseSqlRepo<?, ?>> index = new ConcurrentHashMap<>();
                    String[] beanNames = BeanUtil.getBeanNamesForType(BaseSqlRepo.class);
                    if (FuncUtil.isNotEmpty(beanNames)) {
                        for (String beanName : beanNames) {
                            Object bean = BeanUtil.getBean(beanName);
                            if (bean instanceof BaseSqlRepo) {
                                BaseSqlRepo<?, ?> repo = (BaseSqlRepo<?, ?>) bean;
                                Class<?> ec = repo.getEntityClass();
                                if (ec != null) {
                                    index.put(ec, repo);
                                }
                            }
                        }
                    }
                    repoIndex = index;
                    log.debug("@BindRepo 索引初始化完成，共 {} 个 BaseSqlRepo", index.size());
                }
            }
        }
        return repoIndex.get(entityClass);
    }

    /**
     * Java 属性名 → 数据库列名
     */
    private String resolveColumnName(Class<?> entityClass, String fieldName) {
        try {
            Field field = ReflectionUtil.getField(entityClass, fieldName);
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField != null) {
                return tableField.value();
            }
            TableId tableId = field.getAnnotation(TableId.class);
            if (tableId != null) {
                return tableId.value();
            }
        } catch (Exception e) {
            // 字段不存在，走兜底
        }
        return StringUtil.camelToUnderline(fieldName);
    }
}
