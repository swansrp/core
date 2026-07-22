package com.bidr.platform.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.dict.BizDictTreeCacheService;
import com.bidr.platform.service.dict.BizDictService;
import com.bidr.platform.vo.dict.BizDictVO;
import com.bidr.platform.vo.dict.TreeDictMoveReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 业务树形字典管理（CRUD操作）
 *
 * @author Sharp
 * @since 2026/07/22
 */
@Api(tags = "系统基础 - 业务树形字典管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/biz/tree/dict"})
public class SystemBizTreeDictController {

    private final BizDictService bizDictService;
    private final BizDictTreeCacheService bizDictTreeCacheService;

    // ==================== 树形字典管理 ====================

    @ApiOperation("创建树形字典（手动模式，插入默认根节点）")
    @PostMapping("/tree/create")
    public void createTreeDict(@RequestBody BizDictVO vo) {
        Validator.assertNotBlank(vo.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        Validator.assertNotBlank(vo.getDictName(), ErrCodeSys.PA_PARAM_NULL, "字典名称");
        // 插入默认根节点
        vo.setValue("0");
        vo.setLabel(vo.getDictName() + "(根)");
        vo.setParentDictCode(vo.getDictCode());
        vo.setParentValue(null);
        vo.setSort(0);
        bizDictService.addDict(vo, null);
        bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        Resp.notice("树形字典创建成功");
    }

    @ApiOperation("删除整棵树形字典")
    @PostMapping("/tree/delete")
    public void deleteTreeDict(@RequestParam String dictCode) {
        Validator.assertNotBlank(dictCode, ErrCodeSys.PA_PARAM_NULL, "字典编码");
        bizDictService.deleteTreeDictByCode(dictCode);
        bizDictTreeCacheService.refreshSingle(dictCode);
        Resp.notice("树形字典删除成功");
    }

    // ==================== 树形字典节点管理 ====================

    @ApiOperation("添加树形字典节点")
    @PostMapping("/item/add")
    public void addTreeNode(@RequestBody BizDictVO vo) {
        Validator.assertNotBlank(vo.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        Validator.assertNotBlank(vo.getLabel(), ErrCodeSys.PA_PARAM_NULL, "节点名称");
        Validator.assertNotBlank(vo.getValue(), ErrCodeSys.PA_PARAM_NULL, "节点值");
        // 树形自引用：parent_dict_code = dict_code
        vo.setParentDictCode(vo.getDictCode());
        // 自动设置排序号 = 当前父节点下子节点数量
        vo.setSort(bizDictService.countTreeChildren(vo.getDictCode(), vo.getParentValue()));
        bizDictService.addDict(vo, null);
        bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        Resp.notice("节点添加成功");
    }

    @ApiOperation("更新树形字典节点")
    @PostMapping("/item/update")
    public void updateTreeNode(@RequestBody BizDictVO vo) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "ID");
        bizDictService.updateDict(vo, null);
        if (vo.getDictCode() != null) {
            bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        }
        Resp.notice("节点更新成功");
    }

    @ApiOperation("删除树形字典节点")
    @PostMapping("/item/delete")
    public void deleteTreeNode(@RequestParam Long id, @RequestParam String dictCode) {
        bizDictService.deleteDict(id, null);
        bizDictTreeCacheService.refreshSingle(dictCode);
        Resp.notice("节点删除成功");
    }

    @ApiOperation("移动树形字典节点（拖拽变更父节点/批量排序）")
    @PostMapping("/item/move")
    public void moveTreeNode(@RequestBody TreeDictMoveReq req) {
        Validator.assertNotNull(req.getMovedId(), ErrCodeSys.PA_PARAM_NULL, "ID");
        Validator.assertNotBlank(req.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        bizDictService.moveTreeNode(req);
        bizDictTreeCacheService.refreshSingle(req.getDictCode());
        Resp.notice("节点移动成功");
    }
}
