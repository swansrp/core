package com.bidr.platform.service.dict;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.aop.RedisPublish;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.vo.dict.AddDictItemReq;
import com.bidr.platform.vo.dict.AddDictReq;
import com.bidr.platform.vo.dict.DictRes;
import com.bidr.platform.vo.dict.UpdateDictDefaultReq;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.bidr.platform.constant.err.DictErrorCode.DICT_IS_ALREADY_EXISTED;
import static com.bidr.platform.constant.err.DictErrorCode.DICT_ITEM_IS_ALREADY_EXISTED;

/**
 * Title: DictService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 08:52
 */
@Service
@RequiredArgsConstructor
public class DictService {
    public final DictCacheService dictCacheService;
    private final SysDictService sysDictService;
    private final SysDictTypeService sysDictTypeService;
    private final SysBizDictService sysBizDictService;

    public List<KeyValueResVO> getNameList(String name) {
        List<SysDictType> sysDictList = sysDictTypeService.getSysDictByTitle(name);
        List<SysBizDict> bizDictList = sysBizDictService.getBizDictListByTitle(name);
        return buildKeyValueListByDictType(sysDictList, bizDictList);
    }

    private List<KeyValueResVO> buildKeyValueListByDictType(List<SysDictType> sysDictList, List<SysBizDict> bizDictList) {
        List<KeyValueResVO> resList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(sysDictList)) {
            for (SysDictType sysDict : sysDictList) {
                KeyValueResVO res = new KeyValueResVO();
                res.setValue(sysDict.getDictName());
                res.setLabel(sysDict.getDictTitle());
                resList.add(res);
            }
        }
        if (CollectionUtils.isNotEmpty(bizDictList)) {
            for (SysBizDict bizDict : bizDictList) {
                KeyValueResVO res = new KeyValueResVO();
                res.setValue(bizDict.getDictCode());
                res.setLabel(bizDict.getDictName());
                resList.add(res);
            }
        }
        return resList;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean addDict(AddDictReq req) {
        Validator.assertFalse(sysDictTypeService.existedById(req.getDictName()), DICT_IS_ALREADY_EXISTED, req.getDictTitle());
        SysDictType sysDictType = new SysDictType();
        sysDictType.setDictName(req.getDictName());
        sysDictType.setDictTitle(req.getDictTitle());
        return sysDictTypeService.insert(sysDictType);
    }

    public List<SysDict> getSysDictByName(String dictName) {
        List<SysDict> res = new ArrayList<>();
        List<SysBizDict> sysBizDictList = sysBizDictService.getBizDictListByTitle(dictName);
        if (CollectionUtils.isNotEmpty(sysBizDictList)) {
            for (SysBizDict sysBizDict : sysBizDictList) {
                res.add(buildBySysBizDict(sysBizDict));
            }
        }
        List<SysDict> sysDictByName = sysDictService.getSysDictByName(dictName);
        if (CollectionUtils.isNotEmpty(sysDictByName)) {
            res.addAll(sysDictByName);
        }
        return res;
    }


    /**
     * 根据字典编码获取字典项列表
     */
    public List<SysBizDict> getBizDictItemsByCode(String dictCode) {
        return sysBizDictService.getBizDictItemsByCode(dictCode);
    }

    private SysDict buildBySysBizDict(SysBizDict sysBizDict) {
        SysDict sysDict = new SysDict();
        sysDict.setDictId(sysBizDict.getId().toString());
        sysDict.setDictLabel(sysBizDict.getLabel());
        sysDict.setDictValue(sysBizDict.getValue());
        sysDict.setShow(CommonConst.YES);
        return sysDict;
    }

    public void replaceDefaultDictItem(UpdateDictDefaultReq vo) {
        List<SysDict> entityList = new ArrayList<>();
        SysDict defaultDict = sysDictService.getDefaultDict(vo.getDictName());
        if (defaultDict != null) {
            SysDict sysDict = new SysDict();
            sysDict.setDictId(defaultDict.getDictId());
            sysDict.setIsDefault(CommonConst.NO);
            entityList.add(sysDict);
        }
        SysDict sysDict = new SysDict();
        sysDict.setDictId(vo.getDictId());
        sysDict.setIsDefault(CommonConst.YES);
        entityList.add(sysDict);
        sysDictService.updateBatchById(entityList);

    }

    @Transactional(rollbackFor = Exception.class)
    public boolean addDictItem(AddDictItemReq req) {
        dictCacheService.cachePrepare(req.getDictName());
        Validator.assertFalse(sysDictService.existed(req.getDictName(), req.getDictValue()), DICT_ITEM_IS_ALREADY_EXISTED, req.getDictTitle());
        return sysDictService.insert(req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(String dictName) {
        sysDictTypeService.deleteById(dictName);
        sysDictService.deleteByDictName(dictName);
    }

    public List<DictRes> getSysDictByLabel(String dictName, String label) {
        dictCacheService.cachePrepare(dictName);
        List<SysDict> sysDictList = sysDictService.getSysDictByLabel(dictName, label);
        return buildKeyValueListByDict(sysDictList);
    }

    private List<DictRes> buildKeyValueListByDict(List<SysDict> sysDictList) {
        List<DictRes> resList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(sysDictList)) {
            for (SysDict sysDict : sysDictList) {
                DictRes res = new DictRes();
                res.setValue(sysDict.getDictValue());
                res.setLabel(sysDict.getDictLabel());
                res.setShow(sysDict.getShow());
                resList.add(res);
            }
        }
        return resList;
    }

    @RedisPublish
    public void refresh() {
        dictCacheService.refresh();
    }
}
