package com.bidr.kernel.utils;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import org.apache.commons.collections4.MapUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: DictEnumUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/11/29 17:21
 */
public class DictEnumUtil {

    private static final Map<String, Map<Object, String>> ENUM_MAP = new HashMap<>();

    public static <E extends Enum<E>> E getEnumByValue(Object value, Class<E> clazz) {
        return getValueMap(value, "value", clazz);
    }

    private static <E extends Enum<E>> E getValueMap(Object obj, String objFieldName, Class<E> clazz) {
        return getValueMap(obj, objFieldName, clazz, null);
    }

    private static <E extends Enum<E>> E getValueMap(Object obj, String objFieldName, Class<E> clazz, E defaultEnum) {
        Map<Object, String> valueMap = ENUM_MAP.getOrDefault(clazz.getName() + objFieldName, new HashMap<>(16));
        if (MapUtils.isEmpty(valueMap)) {
            synchronized (ENUM_MAP) {
                for (Enum<E> en : EnumSet.allOf(clazz)) {
                    valueMap.put(ReflectionUtil.getValue(en, objFieldName, Object.class), en.name());
                }
                ENUM_MAP.put(clazz.getName() + objFieldName, valueMap);
            }
        }
        E res = null;
        try {
            res = Enum.valueOf(clazz, valueMap.get(obj));
        } catch (Exception e) {
            if (FuncUtil.isNotEmpty(defaultEnum)) {
                res = defaultEnum;
            } else {
                Validator.assertException(e);
            }
        }
        Validator.assertNotNull(res, ErrCodeSys.SYS_ERR_MSG, "字典反射失败");
        return res;
    }

    public static <E extends Enum<E>> E getEnumByValue(Object value, Class<E> clazz, E defaultEnum) {
        return getValueMap(value, "value", clazz, defaultEnum);
    }

    public static <E extends Enum<E>> E getEnumByLabel(String label, Class<E> clazz) {
        return getValueMap(label, "label", clazz);
    }

    public static <E extends Enum<E>> E getEnumByOrder(Integer order, Class<E> clazz) {
        return getValueMap(order, "order", clazz);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E getEnum(Object obj, String objFieldName, Class<?> enumClazz) {
        return getValueMap(obj, objFieldName, (Class<E>) enumClazz);
    }

    public static <E extends Enum<E>> E getEnum(Object obj, String objFieldName, Class<?> enumClazz, E defaultEnum) {
        return getValueMap(obj, objFieldName, (Class<E>) enumClazz, defaultEnum);
    }

}
