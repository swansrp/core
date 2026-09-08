package com.bidr.platform.service.cache.dict;

import com.bidr.kernel.config.response.DictBinder;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.dao.entity.SysDict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: DictBindProvider
 * Description: {@link DictBinder} SPI 的平台字典实现，为 @BindDict 注解提供翻译数据源（走平台字典缓存）。
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictBindProvider implements DictBinder {

    private final DictCacheService dictCacheService;

    @Override
    public void bindItemLabel(List<?> voList, String setFieldName, String getFieldName, String type) {
        if (FuncUtil.isNotEmpty(voList)) {
            for (Object vo : voList) {
                Object value = ReflectionUtil.getValue(vo, getFieldName, Object.class);
                if (FuncUtil.isNotEmpty(value)) {
                    if (String.class.isAssignableFrom(value.getClass())) {
                        String[] array = ((String) value).split(",");
                        List<String> labelList = new ArrayList<>();
                        for (String s : array) {
                            try {
                                SysDict dict = dictCacheService.getDictByValue(type, s);
                                labelList.add(dict.getDictLabel());
                            } catch (ServiceException e) {
                                log.warn(e.getMessage());
                            }
                        }
                        if (FuncUtil.isNotEmpty(labelList)) {
                            ReflectionUtil.setFieldValue(vo, setFieldName, StringUtil.joinWith(",", labelList), true, false);
                        }
                    } else {
                        SysDict dict = dictCacheService.getDictByValue(type, value.toString());
                        ReflectionUtil.setFieldValue(vo, setFieldName, dict.getDictLabel());
                    }
                }
            }
        }
    }
}
