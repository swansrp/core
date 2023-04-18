package com.bidr.kernel.utils;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.common.func.SetFunc;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.ReflectionUtils;

import javax.validation.constraints.NotNull;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author ruopeng.sha
 */
public class ReflectionUtil {


    private static final Map<String, BeanCopier> beanCopierMap = new ConcurrentHashMap<>();

    private ReflectionUtil() {
    }

    /**
     * @param resultClazz       返回类型
     * @param childrenFieldName 返回类型中下属域名称 如:subList
     * @param dataClazz         源数据类型
     * @param dataList          源数据列表
     * @param fieldName         id
     * @param parentFieldName   pid
     * @param <T>               源数据类型
     * @param <R>               返回类型
     * @return 树形结构
     */
    public static <T, R> List<R> buildTree(Class<R> resultClazz, String childrenFieldName, Class<T> dataClazz,
                                           List<T> dataList, String fieldName, String parentFieldName) {
        Field field = getField(dataClazz, fieldName);
        Validator.assertNotNull(field, ErrCodeSys.PA_DATA_NOT_EXIST, "字段" + fieldName);
        Field parentField = getField(dataClazz, parentFieldName);
        Validator.assertNotNull(parentField, ErrCodeSys.PA_DATA_NOT_EXIST, "字段" + parentFieldName);
        Field childrenField = getField(resultClazz, childrenFieldName);
        Validator.assertNotNull(parentField, ErrCodeSys.PA_DATA_NOT_EXIST, "字段" + childrenFieldName);
        List<T> parentDataList = dataList.stream().filter(data -> getValue(data, parentField) == null)
                .collect(Collectors.toList());
        List<R> res = new ArrayList<>();
        parentDataList.forEach(
                parentData -> res.add(buildTree(resultClazz, childrenField, parentData, dataList, field, parentField)));
        return res;
    }

    public static <T, R> List<R> buildTree(SetFunc<R, List<R>> setChildrenFunc, List<T> dataList,
                                           GetFunc<T, ?> getIdFunc, GetFunc<T, ?> getPidFunc) {
        return buildTree(setChildrenFunc, dataList, getIdFunc, getPidFunc, null);
    }

    public static <T, R> List<R> buildTree(SetFunc<R, List<R>> setChildrenFunc, List<T> dataList,
                                           GetFunc<T, ?> getIdFunc, GetFunc<T, ?> getPidFunc, Object pidValue) {
        LambdaUtil.getRealClass(setChildrenFunc);
        List<T> parentDataList = dataList.stream().filter(data -> Objects.equals(getPidFunc.apply(data), (pidValue)))
                .collect(Collectors.toList());
        List<R> res = new ArrayList<>();
        parentDataList.forEach(
                parentData -> res.add(buildTree(setChildrenFunc, parentData, dataList, getIdFunc, getPidFunc)));
        return res;
    }

    private static <R, T> R buildTree(SetFunc<R, List<R>> setChildrenFunc, T parentData, List<T> dataList,
                                      GetFunc<T, ?> getIdFunc, GetFunc<T, ?> getPidFunc) {
        Class<R> resultClazz = LambdaUtil.getRealClass(setChildrenFunc);
        R res = ReflectionUtil.copy(parentData, resultClazz);
        List<T> list = dataList.stream()
                .filter(data -> Objects.equals(getIdFunc.apply(parentData), getPidFunc.apply(data)))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(list)) {
            setChildrenFunc.apply(res, null);
        } else {
            List<R> childrenList = new ArrayList<>();
            for (T childrenData : list) {
                childrenList.add(buildTree(setChildrenFunc, childrenData, dataList, getIdFunc, getPidFunc));
            }
            setChildrenFunc.apply(res, childrenList);
        }
        return res;
    }

    public static Class<?> getSuperClassGenericType(Class<?> clazz, int index) {
        Type genType = clazz.getGenericSuperclass();
        if (!(genType instanceof ParameterizedType)) {
            return Object.class;
        }
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (index >= params.length || index < 0) {
            return Object.class;
        }
        if (!(params[index] instanceof Class<?>)) {
            return Object.class;
        }
        return (Class<?>) params[index];
    }

    private static <R, T> R buildTree(Class<R> resultClazz, Field children, T parentData, List<T> dataList, Field field,
                                      Field parentField) {
        R res = ReflectionUtil.copy(parentData, resultClazz);
        List<T> list = dataList.stream()
                .filter(data -> Objects.equals(getValue(parentData, field), getValue(data, parentField)))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(list)) {
            setValue(children, res, null);
        } else {
            List<R> childrenList = new ArrayList<>();
            for (T childrenData : list) {
                childrenList.add(buildTree(resultClazz, children, childrenData, dataList, field, parentField));
            }
            setValue(children, res, childrenList);
        }
        return res;
    }

    public static <T> T copyProperties(Object source, Class<T> targetClazz) {
        T target = ReflectionUtil.newInstance(targetClazz);
        copyProperties(source, target);
        return target;
    }

    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                 IllegalAccessException e) {
            throw new ServiceException("创建对象失败", e);
        }
    }

    public static void copyProperties(Object source, Object target) {
        if (source == null) {
            return;
        }
        String beanKey = generateKey(source.getClass(), target.getClass());
        BeanCopier copier;
        if (!beanCopierMap.containsKey(beanKey)) {
            copier = BeanCopier.create(source.getClass(), target.getClass(), false);
            beanCopierMap.put(beanKey, copier);
        } else {
            copier = beanCopierMap.get(beanKey);
        }
        copier.copy(source, target, null);
    }

    private static String generateKey(Class<?> class1, Class<?> class2) {
        return class1.toString() + class2.toString();
    }

    public static boolean existedField(Class<?> aClass, String fieldName) {
        List<Field> fieldList = getFields(aClass);
        for (Field field : fieldList) {
            if (fieldName.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    public static List<Field> getFields(Class<?> aClass) {
        List<Field> fieldList = new ArrayList<>();
        Class<?> tempClass = aClass;
        // 当父类为null的时候说明到达了最上层的父类(Object类).
        while (tempClass != null) {
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            // 得到父类,然后赋给自己
            tempClass = tempClass.getSuperclass();
        }
        return fieldList;
    }

    public static <T> T deltaCopy(Object source, T dis) {
        List<Field> fields = getFields(source);
        for (Field field : fields) {
            Object o = getValue(source, field);
            try {
                Field targetField = getField(dis, field.getName());
                setValue(targetField, dis, o);
            } catch (Exception ignored) {
            }
        }
        return dis;
    }

    public static List<Field> getFields(@NotNull Object obj) {
        Class<?> aClass = obj.getClass();
        return getFields(aClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object target, Field field) {
        if (!field.isAccessible()) {
            try {
                Method getFieldMethod = target.getClass()
                        .getMethod("get" + StringUtil.firstUpperCamelCase(field.getName()));
                return (T) getFieldMethod.invoke(target);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            return (T) field.get(target);
        } catch (Exception ex) {
            return null;
        }
    }

    public static Field getField(@NotNull Object obj, String fieldName) {
        Class<?> aClass = obj.getClass();
        return getField(aClass, fieldName);
    }

    public static boolean setValue(Field field, Object target, Object value) {
        Method setFieldMethod;
        try {
            setFieldMethod = target.getClass()
                    .getMethod("set" + StringUtil.firstUpperCamelCase(field.getName()), field.getType());
            setFieldMethod.invoke(target, value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new ServiceException("设置对象单个属性值发生异常", e);
        }
        return true;
    }

    public static Field getField(Class<?> aClass, String fieldName) {
        List<Field> fieldList = getFields(aClass);
        for (Field field : fieldList) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        throw new ServiceException("没找到指定字段: " + fieldName);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object target, String fieldName, Class<T> clazz) {
        try {
            Field field = getField(target, fieldName);
            if (!field.isAccessible()) {
                Method getFieldMethod = target.getClass().getMethod("get" + StringUtils.capitalize(field.getName()));
                return (T) getFieldMethod.invoke(target);
            } else {
                return (T) field.get(target);
            }
        } catch (Exception e) {
            return null;

        }
    }

    public static Map<String, Field> getFieldMap(Class<?> aClass) {
        Map<String, Field> fieldMap = new LinkedHashMap<>();
        getFieldMap(fieldMap, aClass);
        return fieldMap;
    }

    private static void getFieldMap(Map<String, Field> fieldMap, Class<?> aClass) {
        if (aClass != null) {
            getFieldMap(fieldMap, aClass.getSuperclass());
            for (Field field : aClass.getDeclaredFields()) {
                fieldMap.put(field.getName(), field);
            }
        }
    }

    public static <T> T copyAddNameFix(Object source, Class<T> clazz, String prefix, String suffix) {
        T obj = newInstance(clazz);
        List<Field> targetFields = getFields(clazz);
        List<Field> fields = getFields(source);
        Map<String, Object> targetFieldNameValueMap = new HashMap<>(fields.size());
        for (Field field : fields) {
            Object o = getValue(source, field);
            String newName = prefix + field.getName() + suffix;
            targetFieldNameValueMap.put(StringUtils.lowerCase(newName), o);
        }
        for (Field field : targetFields) {
            setValue(field, obj, targetFieldNameValueMap.get(StringUtils.lowerCase(field.getName())));
        }
        return obj;
    }

    public static <T> T copySubNameFix(Object source, Class<T> clazz, String prefix, String suffix) {
        T obj = newInstance(clazz);
        List<Field> targetFields = getFields(clazz);
        List<Field> fields = getFields(source);
        Map<String, Object> targetFieldNameValueMap = new HashMap<>(fields.size());
        for (Field field : fields) {
            Object o = getValue(source, field);
            String newName = field.getName();
            if (newName.startsWith(prefix)) {
                newName = newName.substring(prefix.length());
            }
            if (newName.endsWith(suffix)) {
                newName = newName.substring(0, newName.length() - suffix.length());
            }
            targetFieldNameValueMap.put(StringUtils.lowerCase(newName), o);
        }
        for (Field field : targetFields) {
            setValue(field, obj, targetFieldNameValueMap.get(StringUtils.lowerCase(field.getName())));
        }
        return obj;

    }

    public static boolean setFieldValue(@NotNull Object target, @NotNull String fieldName, @NotNull Object value) {
        return setFieldValue(target, fieldName, value, true);
    }

    public static boolean setFieldValue(@NotNull Object target, @NotNull String fieldName, @NotNull Object value,
                                        boolean traceable) {
        return setFieldValue(target, fieldName, value, traceable, false);
    }

    public static boolean setFieldValue(@NotNull Object target, @NotNull String fieldName, @NotNull Object value,
                                        boolean traceable, boolean includeParent) {
        Field field = searchField(target.getClass(), fieldName, traceable, includeParent);
        if (field != null) {
            return setValue(field, target, value);
        }
        return false;
    }

    private static Field searchField(Class<?> c, String targetField, boolean traceable, boolean includeParent) {
        do {
            Field[] fields;
            if (includeParent) {
                fields = c.getFields();
            } else {
                fields = c.getDeclaredFields();
            }
            for (Field f : fields) {
                if (f.getName().equals(targetField)) {
                    return f;
                }
            }
            c = c.getSuperclass();
            traceable = traceable && c != Object.class;
        } while (traceable);
        return null;
    }

    public static <K, T> List<T> copyList(Collection<K> source, Class<T> clazz) {
        List<T> resList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(source)) {
            source.forEach(item -> {
                T obj;
                if (item instanceof Map) {
                    obj = (T) copy((Map) item, clazz);
                } else {
                    obj = copy(item, clazz);
                }
                resList.add(obj);
            });
        }
        return resList;
    }

    public static <T> T copy(Map<String, Object> source, Class<T> clazz) {
        T obj = newInstance(clazz);
        if (MapUtils.isNotEmpty(source)) {
            Map<String, String> fieldMap = ReflectionUtil.getFieldDisplayMap(clazz);
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (fieldMap.containsKey(entry.getKey())) {
                    ReflectionUtil.setValue(obj, entry.getKey(), entry.getValue());
                }
            }
        }
        return obj;
    }

    public static <T> T copy(Object source, Class<T> clazz) {
        T obj = newInstance(clazz);
        copyProperties(source, obj);
        return obj;
    }

    public static Map<String, String> getFieldDisplayMap(Class<?> aClass) {
        List<Field> fieldList = getFields(aClass);
        LinkedHashMap<String, String> res = new LinkedHashMap<>(fieldList.size());
        for (Field field : fieldList) {
            try {
                String name = field.getName();
                String value = name;
                ApiModelProperty apiModelAnnotation = field.getAnnotation(ApiModelProperty.class);
                JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
                if (jsonPropertyAnnotation != null && StringUtils.isNotEmpty((jsonPropertyAnnotation).value())) {
                    name = jsonPropertyAnnotation.value();
                }
                if (apiModelAnnotation != null && StringUtils.isNotEmpty((apiModelAnnotation).value())) {
                    value = apiModelAnnotation.value();
                }
                res.put(name, value);
            } catch (SecurityException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    public static void setValue(Object target, String fieldName, Object value) {
        try {
            Field field = getField(target, fieldName);
            if (!field.isAccessible()) {
                Method setFieldMethod = target.getClass()
                        .getMethod("set" + StringUtils.capitalize(field.getName()), field.getType());
                setFieldMethod.invoke(target, value);
            } else {
                field.set(target, value);
            }
        } catch (Exception e) {
            throw new ServiceException("赋值失败");
        }
    }

    public static <K, T> Set<T> copySet(Collection<K> source, Class<T> clazz) {
        Set<T> resSet = new LinkedHashSet<>();
        if (CollectionUtils.isNotEmpty(source)) {
            source.forEach(item -> {
                T obj = newInstance(clazz);
                copyProperties(item, obj);
                resSet.add(obj);
            });
        }
        return resSet;
    }

    public static <T> List<T> getFieldList(List<?> list, String fieldName, Class<T> clazz) {
        if (CollectionUtils.isEmpty(list)) {
            return new ArrayList<>();
        }
        List<T> res = new ArrayList<>();
        list.forEach(item -> {
            res.add(getFieldValue(item, fieldName));
        });
        return res;
    }

    public static <T> T getFieldValue(@NotNull Object object, @NotNull String fullName)
            throws SecurityException, IllegalArgumentException {
        return getFieldValue(object, fullName, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(@NotNull Object object, @NotNull String fieldName, boolean traceable)
            throws SecurityException, IllegalArgumentException {
        Field field;
        String[] fieldNames = fieldName.split("\\.");
        for (String targetField : fieldNames) {
            field = searchField(object.getClass(), targetField, traceable, false);
            if (field == null) {
                return null;
            }
            object = getValue(object, field);
        }
        return (T) object;
    }

    public static <T, R> List<R> getFieldList(List<T> list, GetFunc<T, R> getFunc) {
        if (CollectionUtils.isEmpty(list)) {
            return new ArrayList<>();
        }
        List<R> res = new ArrayList<>();
        list.forEach(item -> {
            res.add(getFunc.apply(item));
        });
        return res;
    }

    public static Map<String, String> getFieldDisplayMap(@NotNull Object obj) {
        Class<?> aClass = obj.getClass();
        return getFieldDisplayMap(aClass);
    }

    public static Map<String, Object> getHashMap(@NotNull Object obj) {
        Class<?> aClass = obj.getClass();
        List<Field> fieldList = getFields(aClass);
        LinkedHashMap<String, Object> res = new LinkedHashMap<>(fieldList.size());
        for (Field field : fieldList) {
            String name = field.getName();
            Object value = getValue(obj, field);
            if (value != null) {
                JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
                if (jsonPropertyAnnotation != null && StringUtils.isNotEmpty((jsonPropertyAnnotation).value())) {
                    res.put(jsonPropertyAnnotation.value(), value);
                } else {
                    res.put(name, value);
                }
            }
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> getHashMap(@NotNull Object obj, Class<T> clazz) {
        Class<?> aClass = obj.getClass();
        List<Field> fieldList = getFields(aClass);
        LinkedHashMap<String, T> res = new LinkedHashMap<>(fieldList.size());
        for (Field field : fieldList) {
            String name = field.getName();
            T value;
            if (clazz.equals(String.class)) {
                value = (T) StringUtil.parse(getValue(obj, field));
            } else {
                value = JsonUtil.readJson(getValue(obj, field), clazz);
            }
            if (value != null) {
                JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
                if (jsonPropertyAnnotation != null && StringUtils.isNotEmpty((jsonPropertyAnnotation).value())) {
                    res.put(jsonPropertyAnnotation.value(), value);
                } else {
                    res.put(name, value);
                }
            }
        }
        return res;
    }

    public static Map<String, Object> getLinkedHashMap(@NotNull Object obj) {
        LinkedHashMap<String, Object> res = new LinkedHashMap<>();
        Class<?> aClass = obj.getClass();
        List<Field> fieldList = getFields(aClass);
        for (Field field : fieldList) {
            String name = field.getName();
            Object value = getValue(obj, field);
            if (value != null) {
                if (field.getType().equals(Date.class)) {
                    value = convertDateFormat(field, (Date) value);
                }
            }
            JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
            if (jsonPropertyAnnotation != null && StringUtils.isNotEmpty((jsonPropertyAnnotation).value())) {
                res.put(jsonPropertyAnnotation.value(), value);
            } else {
                res.put(name, value);
            }
        }
        return res;
    }

    private static String convertDateFormat(Field field, Date value) {
        SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.UK);
        String res = format.format(value);
        DateTimeFormat dateTimeFormatAnnotation = field.getAnnotation(DateTimeFormat.class);
        if (dateTimeFormatAnnotation != null && StringUtils.isNotEmpty((dateTimeFormatAnnotation).pattern())) {
            res = DateUtil.formatDate(value, (dateTimeFormatAnnotation).pattern());
        }
        return res;
    }

    public static LinkedMultiValueMap<String, Object> getLinkedMultiValueMap(@NotNull Object obj) {
        LinkedMultiValueMap<String, Object> res = new LinkedMultiValueMap<>();
        Class<?> aClass = obj.getClass();
        List<Field> fieldList = getFields(aClass);
        for (Field field : fieldList) {
            String name = field.getName();
            Object value = getValue(obj, field);
            if (field.getType().equals(Date.class) && value != null) {
                value = convertDateFormat(field, (Date) value);
            }
            JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
            if (jsonPropertyAnnotation != null && StringUtils.isNotEmpty((jsonPropertyAnnotation).value())) {
                res.add(jsonPropertyAnnotation.value(), value);
            } else {
                res.add(name, value);
            }

        }
        return res;
    }

    public static <T> Map<String, Map<String, T>> reflectToDuplicateMap(List<T> list, String[] outFieldArr,
                                                                        String[] inFieldArr) {
        Map<String, Map<String, T>> outMap = new TreeMap<>();
        for (T t : list) {
            String outKey = getFieldKey(outFieldArr, t);
            Map<String, T> inMap = outMap.computeIfAbsent(outKey, k -> new TreeMap<>());
            String inKey = getFieldKey(inFieldArr, t);
            inMap.put(inKey, t);
        }
        return outMap;
    }

    private static <T> String getFieldKey(String[] outFieldArr, T t) {
        String[] fieldValueArr = new String[outFieldArr.length];
        for (int i = 0; i < outFieldArr.length; i++) {
            fieldValueArr[i] = getFieldValue(t, outFieldArr[i]).toString();
        }
        return StringUtil.join(fieldValueArr);
    }

    public static <T> Map<String, T> reflectToMap(Collection<T> list, String... fieldArr) {
        Map<String, T> map = new TreeMap<>();
        for (T t : list) {
            String fieldValueArr = getFieldKey(fieldArr, t);
            String key = StringUtil.join(fieldValueArr);
            map.put(key, t);
        }
        return map;
    }

    public static <T, K> Map<K, T> reflectToMap(Collection<T> list, String fieldName, Class<K> clazz) {
        Map<K, T> map = new HashMap<>(list.size());
        for (T t : list) {
            K key = getFieldValue(t, fieldName);
            map.put(key, t);
        }
        return map;
    }

    public static Object invoke(Object obj, String methodName, Object... paramArray) {
        List<Class<?>> classList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(paramArray)) {
            for (Object param : paramArray) {
                classList.add(param.getClass());
            }
        }
        if (FuncUtil.isNotEmpty(classList)) {
            return ReflectionUtils.invokeMethod(
                    getMethod(obj.getClass(), methodName, classList.toArray(new Class<?>[0])), obj, paramArray);
        } else {
            return ReflectionUtils.invokeMethod(getMethod(obj.getClass(), methodName), obj, paramArray);
        }

    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypeArr) {
        return ReflectionUtils.findMethod(clazz, methodName, paramTypeArr);
    }

    @SuppressWarnings("rawtypes")
    public static Method getMethod(Class clazz, String methodName) {
        return ReflectionUtils.findMethod(clazz, methodName);
    }

    public static Object invoke(Object obj, Method method, Object... param) {
        return ReflectionUtils.invokeMethod(method, obj, param);
    }

    public static Method getMethod(Object obj, String methodName, Class<?>... paramTypeArr) {
        return ReflectionUtils.findMethod(obj.getClass(), methodName, paramTypeArr);
    }
}
