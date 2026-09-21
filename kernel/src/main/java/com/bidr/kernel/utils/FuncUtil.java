package com.bidr.kernel.utils;

import com.bidr.kernel.common.func.GetFunc;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Title: FuncUtil
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 10:32
 */
@SuppressWarnings("rawtypes, unchecked")
public class FuncUtil {
    public static <T> boolean notEqualsObj(T obj1, T obj2, GetFunc<T, ?>... funcArray) {
        return !equalsObj(obj1, obj2, funcArray);
    }

    public static <T> boolean equalsObj(T obj1, T obj2, GetFunc<T, ?>... funcArray) {
        if (FuncUtil.isNotEmpty(funcArray)) {
            if (obj1 == null) {
                return obj2 == null;
            } else if (obj2 == null) {
                return false;
            } else {
                for (GetFunc<T, ?> func : funcArray) {
                    if (FuncUtil.notEquals(func.apply(obj1), func.apply(obj2))) {
                        return false;
                    }
                }
            }
        } else {
            return equals(obj1, obj2);
        }
        return true;
    }

    public static Boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    public static Boolean notEquals(Object obj1, Object obj2) {
        return !equals(obj1, obj2);
    }

    public static Boolean equals(Object obj1, Object obj2) {
        if (obj1 == null) {
            return obj2 == null;
        } else if (obj2 == null) {
            return false;
        } else if (obj1.getClass().equals(obj2.getClass())) {
            Class<?> clazz = obj1.getClass();
            if (String.class.isAssignableFrom(clazz)) {
                return StringUtils.equals((String) obj1, (String) obj2);
            } else if (Collection.class.isAssignableFrom(clazz)) {
                return CollectionUtils.isEqualCollection((Collection) obj1, (Collection) obj2);
            } else if (obj1.getClass().isArray()) {
                return CollectionUtils.isEqualCollection(Collections.singletonList(obj1),
                        Collections.singletonList(obj2));
            } else {
                return obj1.equals(obj2);
            }
        } else if (Number.class.isAssignableFrom(obj1.getClass()) && Number.class.isAssignableFrom(obj2.getClass())) {
            return obj1.toString().equals(obj2.toString());
        } else {
            return false;
        }
    }

    public static Boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        } else {
            Class<?> clazz = obj.getClass();
            if (String.class.isAssignableFrom(clazz)) {
                return StringUtils.isEmpty((String) obj);
            } else if (Collection.class.isAssignableFrom(clazz)) {
                return CollectionUtils.isEmpty((Collection) obj);
            } else if (Map.class.isAssignableFrom(clazz)) {
                return MapUtils.isEmpty((Map) obj);
            } else if (obj.getClass().isArray()) {
                return ((Object[]) obj).length == 0;
            } else if (Iterator.class.isAssignableFrom(clazz)) {
                return IteratorUtils.isEmpty((Iterator) obj);
            }
        }
        return false;
    }

    /**
     * 判断值是否可直接作为一个 SQL 值使用（绑定成单个参数或内联成单个字面量）
     * 空串是合法值（对应库里存 '' 的维度），因此只把 null 与集合/数组/Map/迭代器这类嵌套结构判为不可用
     *
     * @param obj 待判断值
     * @return true 表示可作为单个 SQL 值
     */
    public static Boolean isScalar(Object obj) {
        if (obj == null) {
            return false;
        }
        Class<?> clazz = obj.getClass();
        return !Collection.class.isAssignableFrom(clazz)
                && !Map.class.isAssignableFrom(clazz)
                && !Iterator.class.isAssignableFrom(clazz)
                && !clazz.isArray();
    }

    /**
     * 条件值清洗：只保留可作为单个 SQL 值的元素
     * 查询条件由前端拼装，配置脏数据里可能出现嵌套数组（如 value:[[]]）；这类值一旦被送进 SQL 生成器，
     * 命名参数会展开成零个占位符、字面量内联会输出空串，最终拼出 `col = ` / `IN ()` 这种残缺语句让数据库直接语法报错
     *
     * @param values 原始条件值
     * @return 清洗后的可绑定值列表（原顺序保留，入参为空时返回空列表）
     */
    public static List<Object> scalarValues(Collection<?> values) {
        if (isEmpty(values)) {
            return new ArrayList<>();
        }
        List<Object> scalarValues = new ArrayList<>(values.size());
        for (Object value : values) {
            if (isScalar(value)) {
                scalarValues.add(value);
            }
        }
        return scalarValues;
    }
}
