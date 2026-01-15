package com.bidr.platform.service.dict;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.vo.dict.BizDictVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @Nullable
    private final List<BizDictValidator> bizDictValidators;

    public List<BizDictVO> getDict(String bizId, String dictCode) {
        validateBizId(bizId);
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
            wrapper.last("ORDER BY CASE WHEN biz_id = " + bizId + " THEN 2 ELSE 1 END, sort ASC");
        } else {
            wrapper.orderByAsc(SysBizDict::getSort);
        }

        List<SysBizDict> list = sysBizDictService.select(wrapper);

        // 按value去重，企业优先（因为企业排在前面，所以用LinkedHashMap保持顺序，第一个会被保留）
        Map<String, SysBizDict> uniqueMap = new LinkedHashMap<>();
        for (SysBizDict dict : list) {
            uniqueMap.putIfAbsent(dict.getValue(), dict);
        }
        return Resp.convert(new ArrayList<>(uniqueMap.values()), BizDictVO.class);
    }

    private void validateBizId(String bizId) {
        if (bizDictValidators != null) {
            for (BizDictValidator validator : bizDictValidators) {
                validator.validator(bizId);
            }
        }
    }

    public List<KeyValueResVO> getDictList(String bizId, String name) {
        validateBizId(bizId);
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.isNull(SysBizDict::getBizId);
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
        validateBizId(bizId);
        if (FuncUtil.isEmpty(vo.getDictName())) {
            List<KeyValueResVO> dictList = getDictList(bizId, vo.getDictCode());
            Validator.assertNotEmpty(dictList, ErrCodeSys.PA_DATA_NOT_SUPPORT, "字典项: " + vo.getDictCode());
            vo.setDictName(dictList.get(0).getLabel());
        }
        if (FuncUtil.isNotEmpty(bizId)) {
            vo.setBizId(bizId);
            vo.setValue(StringUtil.join(bizId, vo.getValue()));
        } else {
            vo.setBizId(null);
        }
        sysBizDictService.insert(ReflectionUtil.copy(vo, SysBizDict.class));
    }

    public boolean updateDict(BizDictVO vo, String bizId) {
        validateBizId(bizId);
        SysBizDict existing = sysBizDictService.selectById(vo.getId());
        validateExisting(bizId, existing);
        sysBizDictService.updateById(ReflectionUtil.copy(vo, SysBizDict.class));
        return true;
    }

    private void validateExisting(String bizId, SysBizDict existing) {
        Validator.assertNotNull(existing, ErrCodeSys.PA_DATA_NOT_EXIST, "字典");
        // 管理端可修改系统字典
        if (FuncUtil.isEmpty(bizId)) {
            Validator.assertNull(existing.getBizId(), ErrCodeSys.SYS_ERR_MSG, "只可修改系统字典项");
        } else {
            Validator.assertEquals(existing.getBizId(), bizId, ErrCodeSys.SYS_ERR_MSG, "不可修改该业务的字典项");
        }
    }

    public boolean deleteDict(Long id, String bizId) {
        validateBizId(bizId);
        SysBizDict existing = sysBizDictService.selectById(id);
        validateExisting(bizId, existing);
        sysBizDictService.deleteById(id);
        return true;
    }

    public BizDictVO getDictByCode(String dictName, String value) {
        return Resp.convert(sysBizDictService.getDictByCode(dictName, value), BizDictVO.class);
    }

    public void updateDictName(KeyValueResVO req) {
        sysBizDictService.updateDictName(req.getValue(), req.getLabel());
    }
}