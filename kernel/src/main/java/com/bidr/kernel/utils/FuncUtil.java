package com.bidr.kernel.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;
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
            } else if (List.class.isAssignableFrom(clazz)) {
                return CollectionUtils.isEmpty((List) obj);
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
}
