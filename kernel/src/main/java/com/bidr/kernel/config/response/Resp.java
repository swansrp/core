package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.diboot.core.binding.Binder;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: R
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/13 13:42
 */
@Data
public class Resp {
    public static void notice(String noticeFormat, Object... parameter) {
        throw new NoticeException(String.format(noticeFormat, parameter));
    }

    public static void notice(Object resp, String noticeFormat, Object... parameter) {
        throw new NoticeException(resp, String.format(noticeFormat, parameter));
    }

    /**
     * 将制定源数据转换成目标
     *
     * @param entity  源数据
     * @param voClass 目标数据类型
     * @param <T>     源数据类型
     * @param <VO>    目标数据类型
     * @return 目标数据
     */
    public static <T, VO> VO convert(T entity, Class<VO> voClass) {
        return Binder.convertAndBindRelations(entity, voClass);
    }

    /**
     * 根据给定列表dict 将entity进行填充 并转换成目标列表
     *
     * @param entityList 源数据列表
     * @param voClass    目标数据类型
     * @param dictList   给定列表
     * @param dictField  给定列表匹配字段
     * @param dataFiled  源数据匹配点断
     * @param <T>        源数据类型
     * @param <VO>       目标数据类型
     * @param <E>        给定列表类型
     * @return 目标数据列表
     */
    @SuppressWarnings("unchecked")
    public static <T, VO, E> List<VO> convert(List<T> entityList, Class<VO> voClass, List<E> dictList,
                                              GetFunc<E, ?> dictField, GetFunc<T, ?> dataFiled) {
        if (FuncUtil.isNotEmpty(entityList)) {
            return convert(entityList, voClass, dictList, dictField, dataFiled,
                    (Class<T>) entityList.get(0).getClass());
        } else {
            return Binder.convertAndBindRelations(new ArrayList<>(new LinkedHashMap<>().values()), voClass);
        }
    }

    /**
     * 根据给定列表dict 将entity进行填充 并转换成目标列表
     *
     * @param entityList  源数据列表
     * @param voClass     目标数据类型
     * @param dictList    给定列表
     * @param dictField   给定列表匹配字段
     * @param dataFiled   源数据匹配点断
     * @param entityClazz 源数据类型
     * @param <T>         源数据类型
     * @param <VO>        目标数据类型
     * @param <E>         给定列表类型
     * @return 目标数据列表
     */
    public static <T, VO, E> List<VO> convert(List<T> entityList, Class<VO> voClass, List<E> dictList,
                                              GetFunc<E, ?> dictField, GetFunc<T, ?> dataFiled, Class<T> entityClazz) {
        Map<Object, T> map = new LinkedHashMap<>();
        if (FuncUtil.isNotEmpty(dictList)) {
            for (E dict : dictList) {
                T entity = ReflectionUtil.newInstance(entityClazz);
                Field field = LambdaUtil.getFieldByGetFunc(dataFiled);
                Object dictValue = JsonUtil.readJson(dictField.apply(dict), field.getType());
                ReflectionUtil.setValue(field, entity, dictValue);
                map.put(dictValue, entity);
            }
        }
        if (FuncUtil.isNotEmpty(entityList)) {
            for (T entity : entityList) {
                map.put(dataFiled.apply(entity), entity);
            }
        }
        return Binder.convertAndBindRelations(new ArrayList<>(map.values()), voClass);
    }

    /**
     * 将源数据列表换成目标列表
     *
     * @param entityList 源数据列表
     * @param voClass    目标数据类型
     * @param <T>        源数据类型
     * @param <VO>       目标数据类型
     * @return 目标数据列表
     */
    public static <T, VO> List<VO> convert(List<T> entityList, Class<VO> voClass) {
        return Binder.convertAndBindRelations(entityList, voClass);
    }

    public static <T, R> Page<R> convert(IPage<T> page, Class<R> clazz) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<R> targetList = Binder.convertAndBindRelations(page.getRecords(), clazz);
        res.setRecords(targetList);
        return res;
    }

    public static <T, R> Page<R> convert(IPage<T> page, List<R> targetList) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        res.setRecords(targetList);
        return res;
    }
}
