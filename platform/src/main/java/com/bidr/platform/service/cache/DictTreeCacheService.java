package com.bidr.platform.service.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.dict.MetaTreeDict;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.constant.dict.IDynamicTree;
import org.apache.commons.collections4.CollectionUtils;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: DictTreeCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 13:25
 */
@Service
public class DictTreeCacheService extends DynamicMemoryCache<List<TreeDict>> {
    @Resource
    private WebApplicationContext webApplicationContext;

    @Override
    protected Map<String, List<TreeDict>> getCacheData() {
        Map<String, List<TreeDict>> map = new HashMap<>();
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaDictClass = reflections.getTypesAnnotatedWith(MetaTreeDict.class);
        for (Class<?> clazz : metaDictClass) {
            if (IDynamicTree.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(MetaTreeDict.class)) {
                String treeType = clazz.getAnnotation(MetaTreeDict.class).value();
                String treeTitle = clazz.getAnnotation(MetaTreeDict.class).remark();
                try {
                    IDynamicTree dynamicTreeDictService = (IDynamicTree) webApplicationContext.getBean(clazz);
                    List<TreeDict> dynamicTreeDictList = dynamicTreeDictService.generate(treeType, treeTitle);
                    if (CollectionUtils.isNotEmpty(dynamicTreeDictList)) {
                        map.put(treeType, dynamicTreeDictList);
                    }
                } catch (BeansException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }

}
