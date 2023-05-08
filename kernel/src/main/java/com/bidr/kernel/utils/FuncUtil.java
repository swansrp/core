package com.bidr.kernel.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Title: FuncUtil
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/30 10:32
 */
@SuppressWarnings("rawtypes")
public class FuncUtil {
    public static Boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
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
        } else {
            return false;
        }
    }
}
