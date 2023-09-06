package com.bidr.platform.validate.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.cache.dict.DictCacheService;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

/**
 * Title: DictValidator
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/09/06 09:57
 */
public class DictValidator implements ConstraintValidator<DictValid, Object> {
    /**
     * 目标枚举类
     */
    Class<? extends Dict>[] dictClassArray;

    @Override
    public void initialize(DictValid constraintAnnotation) {
        dictClassArray = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (FuncUtil.isEmpty(dictClassArray)) {
            return true;
        } else if (FuncUtil.isEmpty(value)) {
            return true;
        } else if (value instanceof List) {
            List<?> values = (List<?>) value;
            for (Object val : values) {
                if (!isValidForValue(val, dictClassArray)) {
                    return false;
                }
            }
            return true;
        } else {
            return isValidForValue(value, dictClassArray);
        }
    }

    private boolean isValidForValue(Object value, Class<? extends Dict>[] cls) {
        if (value != null && value.toString().length() > 0 && cls.length > 0) {
            for (Class<? extends Dict> cl : cls) {
                DictCacheService cacheService = BeanUtil.getBean(DictCacheService.class);
                if (FuncUtil.isNotEmpty(cacheService)) {
                    MetaDict metaDict = cl.getAnnotation(MetaDict.class);
                    if (FuncUtil.isEmpty(metaDict)) {
                        return true;
                    } else {
                        String dictName = metaDict.value();
                        try {
                            SysDict dictByValue = cacheService.getDictByValue(dictName, value.toString());
                            return FuncUtil.isNotEmpty(dictByValue);
                        } catch (Exception e) {
                            return false;
                        }
                    }

                } else {
                    return true;
                }
            }
        } else {
            return true;
        }
        return false;
    }

}
