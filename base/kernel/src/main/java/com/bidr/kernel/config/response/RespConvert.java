package com.bidr.kernel.config.response;

import com.bidr.kernel.common.convert.Convert;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: RespConvert
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/11/14 9:19
 */
@SuppressWarnings("unchecked")
public class RespConvert {

    public static <T, VO> void fieldConvert(T entity, Class<VO> voClass) {
        fieldConvert(entity, voClass, false);
    }

    public static <T, VO> void fieldAfterConvert(T entity, Class<VO> voClass) {
        fieldConvert(entity, voClass, true);
    }

    private static <T, VO> void fieldConvert(T entity, Class<VO> voClass, boolean afterBind) {
        ReflectionUtil.getFields(voClass).stream().filter((field) -> field.getAnnotation(Convert.class) != null)
                .forEach(field -> {
                    Convert convert = field.getAnnotation(Convert.class);
                    Object value;
                    if (!FuncUtil.equals(convert.after(), afterBind)) {
                        return;
                    }
                    if (FuncUtil.isNotEmpty(convert.field())) {
                        value = ReflectionUtil.getValue(entity, convert.field(), Object.class);
                    } else {
                        value = ReflectionUtil.getValue(entity, field);
                    }
                    if (FuncUtil.isEmpty(value) && convert.ignoreNull()) {
                        return;
                    }
                    if (convert.passEntity()) {
                        // passEntity模式：field值 + 整个VO对象
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(), value, entity);
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            value = ReflectionUtil.invoke(convert.util(), convert.method(), value, entity);
                        }
                    } else {
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(), value);
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            value = ReflectionUtil.invoke(convert.util(), convert.method(), value);
                        }
                    }
                    ReflectionUtil.setValue(field, entity, value);
                });
    }

    public static <T, VO> void fieldConvert(List<T> entityList, Class<VO> voClass) {
        fieldConvert(entityList, voClass, false);
    }

    public static <T, VO> void fieldAfterConvert(List<T> entityList, Class<VO> voClass) {
        fieldConvert(entityList, voClass, true);
    }


    private static <T, VO> void fieldConvert(List<T> entityList, Class<VO> voClass, boolean afterBind) {
        List<Field> fields = ReflectionUtil.getFields(voClass);
        if (FuncUtil.isNotEmpty(fields)) {
            if (FuncUtil.isNotEmpty(entityList)) {
                Map<Field, Map<Object, Object>> batchValueMap = new HashMap<>(fields.size());
                for (T entity : entityList) {
                    if (FuncUtil.isNotEmpty(entity)) {
                        for (Field field : fields) {
                            Convert convert = field.getAnnotation(Convert.class);
                            if (FuncUtil.isEmpty(convert) || !FuncUtil.equals(convert.after(), afterBind)) {
                                continue;
                            }
                            Object value;
                            if (FuncUtil.isNotEmpty(convert.field())) {
                                value = ReflectionUtil.getValue(entity, convert.field(), Object.class);
                            } else {
                                value = ReflectionUtil.getValue(entity, field);
                            }
                            if (FuncUtil.isEmpty(value) && convert.ignoreNull()) {
                                continue;
                            }
                            if (convert.passEntity()) {
                                // passEntity模式不支持batch，直接处理
                                if (FuncUtil.isNotEmpty(convert.bean())) {
                                    value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(),
                                            value, entity);
                                } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                                    value = ReflectionUtil.invoke(convert.util(), convert.method(), value, entity);
                                }
                                ReflectionUtil.setValue(field, entity, value);
                            } else if (convert.batch()) {
                                Map<Object, Object> covertValueMap = batchValueMap.getOrDefault(field,
                                        new HashMap<>(entityList.size()));
                                covertValueMap.put(value, null);
                                batchValueMap.put(field, covertValueMap);
                            } else {
                                if (FuncUtil.isNotEmpty(convert.bean())) {
                                    value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(),
                                            value);
                                } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                                    value = ReflectionUtil.invoke(convert.util(), convert.method(), value);
                                }
                                ReflectionUtil.setValue(field, entity, value);
                            }
                        }
                    }
                }
                if (FuncUtil.isNotEmpty(batchValueMap)) {
                    for (Map.Entry<Field, Map<Object, Object>> fieldMapEntry : batchValueMap.entrySet()) {
                        Field field = fieldMapEntry.getKey();
                        Map<Object, Object> covertValueMap = fieldMapEntry.getValue();
                        Convert convert = field.getAnnotation(Convert.class);
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            covertValueMap = (Map<Object, Object>) ReflectionUtil.invoke(
                                    BeanUtil.getBean(convert.bean()), convert.method(),
                                    new HashSet<>(covertValueMap.keySet()));
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            covertValueMap = (Map<Object, Object>) ReflectionUtil.invoke(convert.util(),
                                    convert.method(), covertValueMap.keySet());
                        }
                        batchValueMap.put(field, covertValueMap);
                    }
                    for (T entity : entityList) {
                        for (Map.Entry<Field, Map<Object, Object>> fieldMapEntry : batchValueMap.entrySet()) {
                            Field field = fieldMapEntry.getKey();
                            Map<Object, Object> covertValueMap = fieldMapEntry.getValue();
                            ReflectionUtil.setValue(field, entity,
                                    covertValueMap.get(ReflectionUtil.getValue(entity, field)));
                        }
                    }
                }
            }
        }
    }

    // ======================== @Accept 异名复制 ========================

    /**
     * @Accept 异名复制：从源对象读取 name 指定字段的值写入 VO 标注字段。
     * 须在同名 copy 之后、其他绑定（@BindRepo 关联键可能依赖本步骤填充）之前执行。
     */
    public static void acceptConvert(Object vo, Object source) {
        if (FuncUtil.isEmpty(vo) || FuncUtil.isEmpty(source)) {
            return;
        }
        for (Field field : ReflectionUtil.getFields(vo.getClass())) {
            Accept accept = field.getAnnotation(Accept.class);
            if (accept == null) {
                continue;
            }
            Object value = ReflectionUtil.getValue(source, accept.name(), Object.class);
            if (value != null && !field.getType().isInstance(value)) {
                // 源字段类型与目标字段不一致时（如 Long userId -> String id）转为字符串
                value = String.valueOf(value);
            }
            ReflectionUtil.setValue(field, vo, value);
        }
    }

    // ======================== @BindRepo 字段绑定 ========================

    /**
     * 缓存的 BindRepoHandler，避免每次转换都扫描 Spring 容器
     */
    private static volatile BindRepoHandler cachedHandler;
    private static volatile boolean handlerInitialized = false;

    // ======================== @BindDict 字典绑定 ========================

    /**
     * 缓存的 DictBinder SPI 实现，避免每次转换都扫描 Spring 容器
     */
    private static volatile DictBinder cachedDictBinder;
    private static volatile boolean dictBinderInitialized = false;

    /**
     * 获取 DictBinder SPI 实现。
     * 采用双重检查锁保证线程安全，仅在首次调用时扫描容器。
     *
     * @return DictBinder，可能为 null（上层未提供字典实现时跳过字典绑定）
     */
    private static DictBinder getDictBinder() {
        if (!dictBinderInitialized) {
            synchronized (RespConvert.class) {
                if (!dictBinderInitialized) {
                    String[] beanNames = BeanUtil.getBeanNamesForType(DictBinder.class);
                    if (FuncUtil.isNotEmpty(beanNames)) {
                        cachedDictBinder = (DictBinder) BeanUtil.getBean(beanNames[0]);
                    }
                    dictBinderInitialized = true;
                }
            }
        }
        return cachedDictBinder;
    }

    /**
     * 单实体 @BindDict 字典绑定
     *
     * @param entity  实体
     * @param voClass VO 类型
     * @param <T>     实体类型
     * @param <VO>    VO 类型
     */
    public static <T, VO> void dictBindConvert(T entity, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entity)) {
            return;
        }
        List<T> list = new ArrayList<>(1);
        list.add(entity);
        dictBindConvert(list, voClass);
    }

    /**
     * 列表 @BindDict 字典绑定。
     * <p>
     * 扫描 VO 上所有 @BindDict 字段，逐注解调用 {@link DictBinder} SPI 完成翻译回填。
     *
     * @param entityList 实体列表
     * @param voClass    VO 类型
     * @param <T>        实体类型
     * @param <VO>       VO 类型
     */
    public static <T, VO> void dictBindConvert(List<T> entityList, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entityList)) {
            return;
        }
        DictBinder dictBinder = getDictBinder();
        if (dictBinder == null) {
            return;
        }
        List<Field> fields = ReflectionUtil.getFields(voClass);
        if (FuncUtil.isEmpty(fields)) {
            return;
        }
        for (Field field : fields) {
            BindDict bindDict = field.getAnnotation(BindDict.class);
            if (bindDict == null) {
                continue;
            }
            String sourceFieldName = FuncUtil.isNotEmpty(bindDict.field()) ? bindDict.field() : field.getName();
            dictBinder.bindItemLabel(entityList, field.getName(), sourceFieldName, bindDict.type());
        }
    }

    /**
     * 获取 BindRepoHandler Bean。
     * 采用双重检查锁保证线程安全，仅在首次调用时扫描容器。
     *
     * @return BindRepoHandler，可能为 null
     */
    private static BindRepoHandler getBindRepoHandler() {
        if (!handlerInitialized) {
            synchronized (RespConvert.class) {
                if (!handlerInitialized) {
                    String[] beanNames = BeanUtil.getBeanNamesForType(BindRepoHandler.class);
                    if (FuncUtil.isNotEmpty(beanNames)) {
                        cachedHandler = (BindRepoHandler) BeanUtil.getBean(beanNames[0]);
                    }
                    handlerInitialized = true;
                }
            }
        }
        return cachedHandler;
    }

    /**
     * 单实体 @BindRepo 绑定
     *
     * @param entity  实体
     * @param voClass VO 类型
     * @param <T>     实体类型
     * @param <VO>    VO 类型
     */
    public static <T, VO> void customBindConvert(T entity, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entity)) {
            return;
        }
        List<T> list = new ArrayList<>(1);
        list.add(entity);
        customBindConvert(list, voClass);
    }

    /**
     * 列表 @BindRepo 绑定。
     * <p>
     * 扫描 VO 上所有字段，对于带有 @BindRepo 注解（包括 @BindUser、@BindDept 等通过元注解桥接的）的字段，
     * 批量收集源字段值，一次性查询后回填，避免 N+1。
     *
     * @param entityList 实体列表
     * @param voClass    VO 类型
     * @param <T>        实体类型
     * @param <VO>       VO 类型
     */
    public static <T, VO> void customBindConvert(List<T> entityList, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entityList)) {
            return;
        }
        BindRepoHandler handler = getBindRepoHandler();
        if (handler == null) {
            return;
        }
        List<Field> fields = ReflectionUtil.getFields(voClass);
        if (FuncUtil.isEmpty(fields)) {
            return;
        }

        for (Field field : fields) {
            BindRepo bindRepo = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
            if (bindRepo == null) {
                continue;
            }
            // condition 非空时优先走关联表达式引擎（支持任意字段数 + 任意级中间表 join）
            if (FuncUtil.isNotEmpty(bindRepo.condition())) {
                bindByCondition(handler, bindRepo, field, entityList);
                continue;
            }
            String sourceFieldName = bindRepo.sourceField();
            String sourceField2Name = bindRepo.sourceField2();
            boolean dual = FuncUtil.isNotEmpty(sourceField2Name);

            // 收集所有实体的源字段值（复合匹配时为 "v1||v2" 组合键）
            Set<Object> sourceValues = new HashSet<>();
            for (T entity : entityList) {
                if (FuncUtil.isNotEmpty(entity)) {
                    Object value = sourceValue(entity, sourceFieldName, sourceField2Name, dual);
                    if (FuncUtil.isNotEmpty(value)) {
                        sourceValues.add(value);
                    }
                }
            }
            if (sourceValues.isEmpty()) {
                continue;
            }

            // 非 List 字段但字段类型即实体类型 → 装载首个匹配的完整实体（BindEntity 形态）
            boolean elementIsEntity = bindWholeEntity(field, bindRepo);
            if (!List.class.isAssignableFrom(field.getType()) && elementIsEntity) {
                Map<Object, List<Object>> listMap = handler.batchConvertList(bindRepo, sourceValues);
                for (T entity : entityList) {
                    if (FuncUtil.isNotEmpty(entity)) {
                        Object value = sourceValue(entity, sourceFieldName, sourceField2Name, dual);
                        if (FuncUtil.isNotEmpty(value)) {
                            List<Object> bound = listMap.get(value);
                            ReflectionUtil.setValue(field, entity, FuncUtil.isNotEmpty(bound) ? bound.get(0) : null);
                        }
                    }
                }
                continue;
            }

            // 字段类型为 List 时执行一对多绑定，否则执行单值绑定
            if (List.class.isAssignableFrom(field.getType())) {
                Map<Object, List<Object>> listMap = handler.batchConvertList(bindRepo, sourceValues);
                for (T entity : entityList) {
                    if (FuncUtil.isNotEmpty(entity)) {
                        Object value = sourceValue(entity, sourceFieldName, sourceField2Name, dual);
                        if (FuncUtil.isNotEmpty(value)) {
                            List<Object> bound = listMap.get(value);
                            // 元素类型非实体 → 装载 extractField 值列表（BindFieldList 形态）
                            ReflectionUtil.setValue(field, entity,
                                    toBoundValue(bound, bindRepo.extractField(), elementIsEntity));
                        }
                    }
                }
                continue;
            }

            // 批量查询
            Map<Object, Object> convertMap = handler.batchConvert(bindRepo, sourceValues);
            if (FuncUtil.isEmpty(convertMap)) {
                continue;
            }

            // 回写到每个实体
            for (T entity : entityList) {
                if (FuncUtil.isNotEmpty(entity)) {
                    Object value = sourceValue(entity, sourceFieldName, sourceField2Name, dual);
                    if (FuncUtil.isNotEmpty(value)) {
                        Object converted = convertMap.get(value);
                        if (converted != null && field.getType() == String.class && !(converted instanceof String)) {
                            // extractField 原始类型与目标字段不一致时（如 Integer dataScope -> String 字段）转为字符串
                            converted = String.valueOf(converted);
                        }
                        ReflectionUtil.setValue(field, entity, converted);
                    }
                }
            }
        }
    }

    /**
     * condition 关联绑定：调用 {@link BindRepoHandler#batchConvertByCondition} 得到「源 VO → 目标实体列表」映射后回填。
     * <p>
     * 按字段形态分派：List 且元素为实体 → 装<b>全部匹配实体</b>；List 且元素为基本类型 →
     * 装 <b>extractField 值列表</b>（BindFieldList 形态）；非 List 且字段类型为实体 → 装<b>首个完整实体</b>
     * （BindEntity 形态）；其余非 List → 取首个目标实体的 {@code extractField} 值（类型不符时转 String）。
     *
     * @param handler    绑定处理器
     * @param bindRepo   字段上的 @BindRepo 注解（condition 非空）
     * @param field      目标字段
     * @param entityList 源 VO 实体列表
     * @param <T>        实体类型
     */
    private static <T> void bindByCondition(BindRepoHandler handler, BindRepo bindRepo, Field field, List<T> entityList) {
        Map<Object, List<Object>> listMap = handler.batchConvertByCondition(bindRepo, entityList);
        boolean isList = List.class.isAssignableFrom(field.getType());
        boolean elementIsEntity = bindWholeEntity(field, bindRepo);
        String extractField = bindRepo.extractField();
        for (T entity : entityList) {
            if (FuncUtil.isEmpty(entity)) {
                continue;
            }
            List<Object> bound = listMap.get(entity);
            if (isList) {
                ReflectionUtil.setValue(field, entity, toBoundValue(bound, extractField, elementIsEntity));
            } else if (elementIsEntity) {
                // BindEntity 形态：字段类型即实体类型，装载首个完整实体
                ReflectionUtil.setValue(field, entity, FuncUtil.isNotEmpty(bound) ? bound.get(0) : null);
            } else {
                Object value = null;
                if (FuncUtil.isNotEmpty(bound)) {
                    value = ReflectionUtil.getValue(bound.get(0), extractField, Object.class);
                    if (value != null && field.getType() == String.class && !(value instanceof String)) {
                        value = String.valueOf(value);
                    }
                }
                ReflectionUtil.setValue(field, entity, value);
            }
        }
    }

    /**
     * 判定绑定目标是否装载<b>完整实体</b>：List 字段取其元素类型、非 List 字段取字段类型本身，
     * 该类型可由 {@code entity()} 赋值（同类/子类）时返回 true。
     * <p>
     * 四种形态分派：true + List → 装实体列表（一对多）；true + 非 List → 装首个完整实体（BindEntity）；
     * false + List → 装 extractField 值列表（BindFieldList）；false + 非 List → 装 extractField 单值（BindField）。
     * raw List（无泛型）或元素为 Object 时返回 false（List 场景退回装载实体列表的既有行为，保持兼容）。
     */
    private static boolean bindWholeEntity(Field field, BindRepo bindRepo) {
        Class<?> elementType;
        if (List.class.isAssignableFrom(field.getType())) {
            Type generic = field.getGenericType();
            if (generic instanceof ParameterizedType) {
                Type[] args = ((ParameterizedType) generic).getActualTypeArguments();
                elementType = args.length == 1 && args[0] instanceof Class ? (Class<?>) args[0] : null;
            } else {
                elementType = null;
            }
        } else {
            elementType = field.getType();
        }
        return elementType != null && elementType != Object.class
                && bindRepo.entity().isAssignableFrom(elementType);
    }

    /**
     * 将匹配到的目标实体列表转换为字段落装值：实体元素直接透传（保持既有一对多行为）；
     * 非实体元素映射为 {@code extractField} 值列表（BindFieldList 形态，null 值跳过）。
     */
    private static List<Object> toBoundValue(List<Object> bound, String extractField, boolean elementIsEntity) {
        if (elementIsEntity) {
            return bound != null ? bound : new ArrayList<>();
        }
        List<Object> values = new ArrayList<>();
        if (FuncUtil.isNotEmpty(bound)) {
            for (Object item : bound) {
                Object v = ReflectionUtil.getValue(item, extractField, Object.class);
                if (v != null) {
                    values.add(v);
                }
            }
        }
        return values;
    }

    /**
     * 读取源字段值；复合匹配时返回 "v1||v2" 组合键（与 {@link BindRepoHandler} 的键规则一致）
     *
     * @param entity           实体
     * @param sourceFieldName  第一源字段名
     * @param sourceField2Name 第二源字段名
     * @param dual             是否复合匹配
     * @param <T>              实体类型
     * @return 匹配键
     */
    private static <T> Object sourceValue(T entity, String sourceFieldName, String sourceField2Name, boolean dual) {
        Object value = ReflectionUtil.getValue(entity, sourceFieldName, Object.class);
        if (dual) {
            Object value2 = ReflectionUtil.getValue(entity, sourceField2Name, Object.class);
            return (value == null ? "" : String.valueOf(value)) + "||"
                    + (value2 == null ? "" : String.valueOf(value2));
        }
        return value;
    }

}
