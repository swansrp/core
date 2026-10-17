package com.bidr.platform.service.dict;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.aop.RedisPublish;
import com.bidr.platform.constant.err.DictErrorCode;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import com.bidr.platform.service.cache.dict.BizDictTreeCacheService;
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
    private final DynamicDictService dynamicDictService;
    private final BizDictTreeCacheService bizDictTreeCacheService;

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
        // 置默认同样是持久化修改，内置字典改了会在下一次启动被代码覆写，先拦截
        assertDictTypeEditable(vo.getDictName());
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
        prepareAddDictItem(req);
        dictCacheService.cachePrepare(req.getDictName());
        Validator.assertFalse(sysDictService.existed(req.getDictName(), req.getDictValue()), DICT_ITEM_IS_ALREADY_EXISTED, req.getDictTitle());
        return sysDictService.insert(req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(String dictName) {
        assertDictTypeEditable(dictName);
        sysDictTypeService.deleteById(dictName);
        sysDictService.deleteByDictName(dictName);
        // 类型与条目已从库中删除，同步注销内存缓存，避免下拉继续命中残留数据
        dictCacheService.unregister(dictName);
    }

    /**
     * 校验字典类型可界面维护：read_only='1' 的内置字典以代码（@MetaDict）为正源、开机重建，
     * 改标题/删类型都会在下一次启动被覆写或造成运行期缓存不一致，因此一律拒绝。
     */
    public void assertDictTypeEditable(String dictName) {
        SysDictType dictType = sysDictTypeService.selectById(dictName);
        Validator.assertNotNull(dictType, DictErrorCode.DICT_IS_NOT_EXISTED, dictName);
        Validator.assertFalse(CommonConst.YES.equals(dictType.getReadOnly()), DictErrorCode.DICT_IS_READ_ONLY,
                dictType.getDictTitle());
    }

    /**
     * 类型修改前置校验：除了内置字典不可改，read_only 本身也不允许从界面写。
     * 它是「这条数据是否归代码（@MetaDict）管」的标记，手工把 '0' 改成 '1' 会让这条
     * 记录在下次开机被 cleanDeprecatedDictType 当成代码已删除的僵尸字典清掉。
     */
    public void assertDictTypeUpdate(SysDictType entity) {
        assertDictTypeEditable(entity.getDictName());
        entity.setReadOnly(CommonConst.NO);
    }

    /**
     * 校验字典条目可界面维护（内置字典的条目由枚举/动态字典生成，改了下次启动就没了）
     */
    public void assertDictItemEditable(String dictId) {
        SysDict sysDict = sysDictService.selectById(dictId);
        Validator.assertNotNull(sysDict, DictErrorCode.DICT_IS_NOT_EXISTED, dictId);
        Validator.assertFalse(CommonConst.YES.equals(sysDict.getReadOnly()), DictErrorCode.DICT_IS_READ_ONLY,
                sysDict.getDictTitle());
    }

    /**
     * 条目修改前置校验：同类型，read_only 归后端管，不让界面手工置 '1'
     */
    public void assertDictItemUpdate(SysDict entity) {
        assertDictItemEditable(entity.getDictId());
        entity.setReadOnly(CommonConst.NO);
    }

    /**
     * 新增条目前置校验：只允许往手工（非只读）字典里加条目，read_only 由所属类型接管，
     * 不让业务人员在表单上手工填（历史上该列必填，只能靠人填对）。
     */
    public void prepareAddDictItem(SysDict sysDict) {
        SysDictType dictType = sysDictTypeService.selectById(sysDict.getDictName());
        Validator.assertNotNull(dictType, DictErrorCode.DICT_IS_NOT_EXISTED, sysDict.getDictName());
        Validator.assertFalse(CommonConst.YES.equals(dictType.getReadOnly()), DictErrorCode.DICT_IS_READ_ONLY,
                dictType.getDictTitle());
        sysDict.setReadOnly(CommonConst.NO);
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
        // 先按动态字典配置重新执行SQL，将结果写入 sys_biz_dict
        dynamicDictService.refreshDynamicDictData();
        // 再刷新内存缓存
        dictCacheService.refresh();
        // 刷新树形字典缓存
        bizDictTreeCacheService.refreshAll();
    }
}
