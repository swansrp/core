package com.bidr.platform.service.cache;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.repository.SysDictService;
import com.diboot.core.entity.Dictionary;
import com.diboot.core.service.DictionaryServiceExtProvider;
import com.diboot.core.vo.DictionaryVO;
import com.diboot.core.vo.LabelValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: DictBindProvider
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/31 11:04
 */
@Service
@RequiredArgsConstructor
public class DictBindProvider implements DictionaryServiceExtProvider {

    private final DictCacheService dictCacheService;
    private final SysDictService sysDictService;

    @Override
    public void bindItemLabel(List voList, String setFieldName, String getFieldName, String type) {
        if (FuncUtil.isNotEmpty(voList)) {
            for (Object vo : voList) {
                Object value = ReflectionUtil.getValue(vo, getFieldName, Object.class);
                SysDict dict = dictCacheService.getDictByValue(type, value.toString());
                ReflectionUtil.setFieldValue(vo, setFieldName, dict.getDictLabel());
            }
        }
    }

    @Override
    public List<LabelValue> getLabelValueList(String dictType) {
        return null;
    }

    @Override
    public boolean existsDictType(String dictType) {
        return FuncUtil.isNotEmpty(dictCacheService.getKeyValue(dictType));
    }

    @Override
    public boolean createDictAndChildren(DictionaryVO dictionaryVO) {
        return false;
    }

    @Override
    public List<Dictionary> getDictDefinitionList() {
        return null;
    }

    @Override
    public List<DictionaryVO> getDictDefinitionVOList() {
        return null;
    }
}
