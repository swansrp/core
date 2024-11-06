package com.bidr.platform.service.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.dict.MetaTreeDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.constant.dict.IDynamicTree;
import org.apache.commons.collections4.CollectionUtils;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.*;

/**
 * Title: DictTreeCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/30 13:25
 */
@Service
public class DictTreeCacheService extends DynamicMemoryCache<List<TreeDict>> {
    @Resource
    private WebApplicationContext webApplicationContext;
    @Value("${my.base-package}")
    private String basePackage;

    @Override
    protected Map<String, List<TreeDict>> getCacheData() {
        Map<String, List<TreeDict>> map = new HashMap<>();
        Reflections reflections = new Reflections(basePackage);
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

    public List<KeyValueResVO> getAll() {
        Map<String, List<TreeDict>> cacheData = super.getAllCache();
        List<KeyValueResVO> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(cacheData)) {
            for (List<?> value : cacheData.values()) {
                if (FuncUtil.isNotEmpty(value)) {
                    TreeDict treeDict = JsonUtil.readJson(value.get(0), TreeDict.class);
                    resList.add(new KeyValueResVO(treeDict.getTreeType(), treeDict.getTreeTitle()));
                }
            }
        }
        return resList;
    }
}
