package com.bidr.platform.service.dict;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.vo.dict.BizDictVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Title: BizDictService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/1/13 23:47
 */
@Service
@RequiredArgsConstructor
public class BizDictService {

    private final SysBizDictService sysBizDictService;

    public List<BizDictVO> getDict(String bizId, String dictCode) {
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        if (FuncUtil.isNotEmpty(bizId)) {
            // 查询系统公共字典或企业私有字典
            wrapper.nested(w -> w.eq(SysBizDict::getBizId, bizId).or().isNull(SysBizDict::getBizId));
        } else {
            wrapper.isNull(SysBizDict::getBizId);
        }
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        if (FuncUtil.isNotEmpty(bizId)) {
            // 企业字典排在前面，再按sort排序
            wrapper.last("ORDER BY CASE WHEN biz_id = " + bizId + " THEN 1 ELSE 2 END, sort ASC");
        } else {
            wrapper.orderByAsc(SysBizDict::getSort);
        }

        List<SysBizDict> list = sysBizDictService.select(wrapper);

        // 按value去重，企业优先（因为企业排在前面，所以用LinkedHashMap保持顺序，第一个会被保留）
        Map<String, SysBizDict> uniqueMap = new LinkedHashMap<>();
        for (SysBizDict dict : list) {
            uniqueMap.putIfAbsent(dict.getValue(), dict);
        }
        return Resp.convert(uniqueMap.values().stream().collect(Collectors.toList()), BizDictVO.class);
    }

    public List<KeyValueResVO> getDictList(String bizId, String name) {
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        if (FuncUtil.isNotEmpty(bizId)) {
            // 查询系统公共字典或企业私有字典
            wrapper.nested(w -> w.eq(SysBizDict::getBizId, bizId).or().isNull(SysBizDict::getBizId));
        } else {
            wrapper.isNull(SysBizDict::getBizId);
        }
        if (FuncUtil.isNotEmpty(name)) {
            wrapper.nested(w -> w.like(SysBizDict::getDictCode, name).or().like(SysBizDict::getDictName, name));
        }
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        wrapper.selectAs(SysBizDict::getDictCode, KeyValueResVO::getValue);
        wrapper.selectAs(SysBizDict::getDictName, KeyValueResVO::getLabel);
        wrapper.groupBy(SysBizDict::getDictCode, SysBizDict::getDictName);
        return sysBizDictService.selectJoinList(KeyValueResVO.class, wrapper);
    }

    public BizDictVO getById(Long id) {
        return Resp.convert(sysBizDictService.selectById(id), BizDictVO.class);
    }

    public void addDict(BizDictVO vo, String bizId) {
        if (FuncUtil.isNotEmpty(bizId)) {
            vo.setBizId(bizId);
        } else {
            vo.setBizId(null);
        }
        sysBizDictService.insert(ReflectionUtil.copy(vo, SysBizDict.class));
    }

    public boolean updateDict(BizDictVO vo, String bizId) {
        SysBizDict existing = sysBizDictService.selectById(vo.getId());
        if (existing == null) {
            return false;
        }
        // 管理端可修改系统字典
        if (FuncUtil.isEmpty(bizId)) {
            if (!FuncUtil.isEmpty(existing.getBizId())) {
                // 管理端只能修改系统字典
                return false;
            }
        } else {
            // 企业端只能修改自己的字典
            if (!bizId.equals(existing.getBizId())) {
                return false;
            }
        }
        sysBizDictService.updateById(ReflectionUtil.copy(vo, SysBizDict.class));
        return true;
    }

    public boolean deleteDict(Long id, String bizId) {
        SysBizDict existing = sysBizDictService.selectById(id);
        if (existing == null) {
            return false;
        }
        // 管理端可修改系统字典
        if (FuncUtil.isEmpty(bizId)) {
            if (!FuncUtil.isEmpty(existing.getBizId())) {
                // 管理端只能修改系统字典
                return false;
            }
        } else {
            // 企业端只能修改自己的字典
            if (!bizId.equals(existing.getBizId())) {
                return false;
            }
        }
        sysBizDictService.deleteById(id);
        return true;
    }
}