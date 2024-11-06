package com.bidr.platform.service.dict;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.service.cache.DictTreeCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: DictService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 08:52
 */
@Service
@RequiredArgsConstructor
public class TreeDictService {
    public final DictTreeCacheService dictTreeCacheService;

    public List<KeyValueResVO> getAll() {
        return dictTreeCacheService.getAll();
    }

    private List<KeyValueResVO> buildKeyValueListByDictType(List<SysDictType> sysDictList) {
        List<KeyValueResVO> resList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(sysDictList)) {
            for (SysDictType sysDict : sysDictList) {
                KeyValueResVO res = new KeyValueResVO();
                res.setValue(sysDict.getDictName());
                res.setLabel(sysDict.getDictTitle());
                resList.add(res);
            }
        }
        return resList;
    }

    public void refresh() {
        dictTreeCacheService.refresh();
    }
}
