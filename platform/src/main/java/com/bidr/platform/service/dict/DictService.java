package com.bidr.platform.service.dict;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import com.bidr.platform.vo.dict.AddDictItemReq;
import com.bidr.platform.vo.dict.AddDictReq;
import com.bidr.platform.vo.dict.UpdateDictDefaultReq;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.bidr.platform.constant.err.DictErrorCode.DICT_IS_ALREADY_EXISTED;
import static com.bidr.platform.constant.err.DictErrorCode.DICT_ITEM_IS_ALREADY_EXISTED;

/**
 * Title: DictService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/28 08:52
 */
@Service
@RequiredArgsConstructor
public class DictService {

    @Resource
    private SysDictService sysDictService;
    @Resource
    private SysDictTypeService sysDictTypeService;

    public List<KeyValueResVO> getNameList(String name) {
        List<KeyValueResVO> resList = new ArrayList<>();
        List<SysDictType> sysDictList = sysDictTypeService.getSysDictByTitle(name);
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

    @Transactional(rollbackFor = Exception.class)
    public boolean addDict(AddDictReq req) {
        Validator.assertFalse(sysDictTypeService.existedById(req.getDictName()), DICT_IS_ALREADY_EXISTED,
                req.getDictTitle());
        SysDictType sysDictType = new SysDictType();
        sysDictType.setDictName(req.getDictName());
        sysDictType.setDictTitle(req.getDictTitle());
        return sysDictTypeService.insert(sysDictType);
    }

    public List<SysDict> getSysDictByName(String dictName) {
        return sysDictService.getSysDictByName(dictName);
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
        Validator.assertFalse(sysDictService.existed(req.getDictName(), req.getDictValue()),
                DICT_ITEM_IS_ALREADY_EXISTED, req.getDictTitle());
        return sysDictService.insert(req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(String dictName) {
        sysDictTypeService.deleteById(dictName);
        sysDictService.deleteByDictName(dictName);
    }
}
