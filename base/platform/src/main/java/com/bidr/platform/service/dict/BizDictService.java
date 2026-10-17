package com.bidr.platform.service.dict;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.vo.dict.BizDictRes;
import com.bidr.platform.vo.dict.BizDictVO;
import com.bidr.platform.vo.dict.TreeDictMoveReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<BizDictVO> getDict(String bizId, String dictCode, String parentValue) {
        validateBizId(bizId);
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        if (FuncUtil.isNotEmpty(bizId)) {
            // 查询系统公共字典或企业私有字典
            wrapper.nested(w -> w.eq(SysBizDict::getBizId, bizId).or().isNull(SysBizDict::getBizId));
        } else {
            wrapper.isNull(SysBizDict::getBizId);
        }
        wrapper.eq(FuncUtil.isNotEmpty(parentValue), SysBizDict::getParentValue, parentValue);
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

    /**
     * 统计树形字典指定父节点下的子节点数量
     */
    public int countTreeChildren(String dictCode, String parentValue) {
        return sysBizDictService.countTreeChildren(dictCode, parentValue);
    }

    public boolean updateDict(BizDictVO vo, String bizId) {
        validateBizId(bizId);
        SysBizDict existing = sysBizDictService.selectById(vo.getId());
        validateExisting(bizId, existing);
        sysBizDictService.updateById(ReflectionUtil.copy(vo, SysBizDict.class));
        return true;
    }

    /**
     * 批量更新字典项排序（一次事务提交所有 id-sort，保证原子性）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDictItemOrder(List<IdOrderReqVO> orderList) {
        if (FuncUtil.isEmpty(orderList)) {
            return;
        }
        List<SysBizDict> entityList = new ArrayList<>();
        for (IdOrderReqVO req : orderList) {
            if (req.getId() == null || req.getShowOrder() == null) {
                continue;
            }
            SysBizDict entity = new SysBizDict();
            entity.setId(Long.parseLong(StringUtil.parse(req.getId())));
            entity.setSort(req.getShowOrder());
            entityList.add(entity);
        }
        if (FuncUtil.isNotEmpty(entityList)) {
            sysBizDictService.updateById(entityList);
        }
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

    /**
     * 移动树形字典节点：更新被移动节点的 parentValue，并批量更新目标父节点下所有子节点的 sort
     */
    public void moveTreeNode(TreeDictMoveReq req) {
        // 1. 更新被移动节点的 parentValue
        SysBizDict moved = sysBizDictService.selectById(req.getMovedId());
        Validator.assertNotNull(moved, ErrCodeSys.PA_DATA_NOT_EXIST, "字典");
        moved.setParentValue(req.getParentValue());
        sysBizDictService.updateById(moved);

        // 2. 批量更新同级节点的 sort
        if (FuncUtil.isNotEmpty(req.getSiblings())) {
            for (TreeDictMoveReq.NodeSort item : req.getSiblings()) {
                SysBizDict entity = new SysBizDict();
                entity.setId(item.getId());
                entity.setSort(item.getSort());
                sysBizDictService.updateById(entity);
            }
        }
    }

    /**
     * 删除整棵树形字典（删除指定dictCode下所有 parent_dict_code=dictCode 的记录）
     */
    public void deleteTreeDictByCode(String dictCode) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysBizDict> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        wrapper.eq(SysBizDict::getParentDictCode, dictCode);
        wrapper.isNull(SysBizDict::getBizId);
        sysBizDictService.remove(wrapper);
    }

    /**
     * 删除整个业务字典（删除指定dictCode下所有 bizId=null 的记录）
     */
    public void deleteBizDictByCode(String dictCode) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysBizDict> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        wrapper.isNull(SysBizDict::getBizId);
        sysBizDictService.remove(wrapper);
    }

    public BizDictVO getDictByCode(String dictName, String value) {
        return Resp.convert(sysBizDictService.getDictByCode(dictName, value), BizDictVO.class);
    }

    public void updateDictName(KeyValueResVO req) {
        sysBizDictService.updateDictName(req.getValue(), req.getLabel());
    }

    public String getDictExisted(String name, String code) {
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.nested(w -> w.eq(SysBizDict::getDictCode, code).or().eq(SysBizDict::getDictName, name));
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        return StringUtil.convertSwitch(sysBizDictService.exists(wrapper));
    }

    /**
     * 根据字典名称模糊查询字典列表
     *
     * @param dictName 字典名称
     * @return 字典列表（包含字典项）
     */
    public List<BizDictRes> searchByDictName(String dictName) {
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.and(FuncUtil.isNotEmpty(dictName), w -> w
                .like(SysBizDict::getDictName, dictName)
                .or()
                .like(SysBizDict::getDictCode, dictName));
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysBizDict::getSort);
        List<SysBizDict> list = sysBizDictService.select(wrapper);
        return groupDictList(list);
    }

    /**
     * 根据字典项名称模糊查询字典列表
     *
     * @param itemName 字典项名称（label）
     * @return 字典列表（包含字典项）
     */
    public List<BizDictRes> searchByDictItemName(String itemName) {
        MPJLambdaWrapper<SysBizDict> wrapper = sysBizDictService.getMPJLambdaWrapper();
        wrapper.like(FuncUtil.isNotEmpty(itemName), SysBizDict::getLabel, itemName);
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysBizDict::getSort);
        List<SysBizDict> list = sysBizDictService.select(wrapper);
        return groupDictList(list);
    }

    /**
     * 将字典项列表按dictCode分组
     */
    private List<BizDictRes> groupDictList(List<SysBizDict> list) {
        Map<String, BizDictRes> resultMap = new LinkedHashMap<>();
        for (SysBizDict dict : list) {
            BizDictRes res = resultMap.computeIfAbsent(dict.getDictCode(), code -> {
                BizDictRes newRes = new BizDictRes();
                newRes.setDictCode(code);
                newRes.setDictName(dict.getDictName());
                newRes.setDictItemList(new ArrayList<>());
                return newRes;
            });
            res.getDictItemList().add(Resp.convert(dict, BizDictVO.class));
        }
        return new ArrayList<>(resultMap.values());
    }
}