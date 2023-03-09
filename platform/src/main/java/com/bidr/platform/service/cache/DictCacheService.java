package com.bidr.platform.service.cache;

import cn.hutool.core.collection.CollectionUtil;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.repository.SysDictService;
import org.apache.commons.collections4.CollectionUtils;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Title: DictCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 13:22
 */
@Service
public class DictCacheService extends DynamicMemoryCache<SysDict> {
    @Resource
    private SysDictService sysDictService;
    @Resource
    private WebApplicationContext webApplicationContext;

    @Override
    protected List<SysDict> getCacheData() {
        return sysDictService.getSysDictCache();
    }

    @Override
    protected Object getCacheKey(SysDict dict) {
        return dict.getDictName() + dict.getDictName();
    }

    @SuppressWarnings("rawtypes")
    private void addDictEnum(List<SysDict> list) {
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaDictClass = reflections.getTypesAnnotatedWith(MetaDict.class);
        for (Class<?> clazz : metaDictClass) {
            if (Enum.class.isAssignableFrom(clazz) && Dict.class.isAssignableFrom(clazz)) {
                for (Object enumItem : clazz.getEnumConstants()) {
                    if (enumItem instanceof Dict) {
                        String show = ((Dict) enumItem).getShow();
                        if (!StringUtil.convertSwitch(show)) {
                            continue;
                        }
                    }
                    SysDict item = new SysDict();
                    item.setDictName(clazz.getAnnotation(MetaDict.class).value());
                    String itemId = ((Enum) enumItem).name();
                    item.setDictValue(itemId);
                    Dict dict = (Dict) enumItem;
                    item.setDictValue(StringUtil.parse(dict.getValue()));
                    item.setDictLabel(dict.getLabel());
                    item.setShow(dict.getShow());
                    item.setRemark(ReflectionUtil.getValue(enumItem, "label", String.class));
                    list.add(item);
                }
            } else if (IDynamicDict.class.isAssignableFrom(clazz)) {
                try {
                    IDynamicDict dynamicDictService = (IDynamicDict) webApplicationContext.getBean(clazz);
                    List<SysDict> dynamicDictList = dynamicDictService.generate();
                    CollectionUtil.sort(dynamicDictList, new Comparator<SysDict>() {

                        @Override
                        public int compare(SysDict dict1, SysDict dict2) {
                            return dict1.getDictSort() - dict2.getDictSort();
                        }
                    });
                    if (CollectionUtils.isNotEmpty(dynamicDictList)) {
                        list.addAll(dynamicDictList);
                    }
                } catch (BeansException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
