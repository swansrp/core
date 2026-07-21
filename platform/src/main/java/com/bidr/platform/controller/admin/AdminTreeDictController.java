package com.bidr.platform.controller.admin;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.service.cache.dict.BizDictTreeCacheService;
import com.bidr.platform.service.dict.BizDictService;
import com.bidr.platform.service.dict.TreeDictService;
import com.bidr.platform.vo.dict.BizDictVO;
import com.bidr.platform.vo.dict.DictRes;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Title: AdminTreeDictController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 08:49
 */
@Api(tags = "系统管理 - 树形字典管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dict/tree/admin"})
public class AdminTreeDictController {

    private final TreeDictService treeDictService;
    private final BizDictTreeCacheService bizDictTreeCacheService;
    private final BizDictService bizDictService;

    @ApiOperation("获取所有代码驱动树形字典")
    @RequestMapping(path = {"/all"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getAllTreeDict() {
        return treeDictService.getAll();
    }

    @ApiOperation("刷新代码驱动树形字典缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh(@RequestBody QuerySysConfigReq req) {
        treeDictService.refresh();
        Resp.notice("系统树形字典修改已生效");
    }

    // ==================== 业务树形字典（sys_biz_dict 自引用树） ====================

    @ApiOperation("获取业务树形字典（树结构）")
    @GetMapping("/biz")
    public List<TreeDict> getBizTreeDict(@RequestParam String dictCode) {
        return bizDictTreeCacheService.getTreeDict(dictCode);
    }

    @ApiOperation("获取所有业务树形字典列表")
    @GetMapping("/biz/list")
    public List<DictRes> getBizTreeDictList(@RequestParam(required = false) String keyword) {
        return bizDictTreeCacheService.getTreeDictList(keyword);
    }

    @ApiOperation("刷新业务树形字典缓存")
    @PostMapping("/biz/refresh")
    public List<TreeDict> refreshBizTreeDict(@RequestParam String dictCode) {
        return bizDictTreeCacheService.refreshSingle(dictCode);
    }

    // ==================== 树形字典管理 ====================

    @ApiOperation("创建树形字典（手动模式，插入默认根节点）")
    @PostMapping("/biz/tree/create")
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
    @PostMapping("/biz/tree/delete")
    public void deleteTreeDict(@RequestParam String dictCode) {
        Validator.assertNotBlank(dictCode, ErrCodeSys.PA_PARAM_NULL, "字典编码");
        bizDictService.deleteTreeDictByCode(dictCode);
        bizDictTreeCacheService.refreshSingle(dictCode);
        Resp.notice("树形字典删除成功");
    }

    // ==================== 树形字典节点管理 ====================

    @ApiOperation("添加树形字典节点")
    @PostMapping("/biz/item/add")
    public void addTreeNode(@RequestBody BizDictVO vo) {
        Validator.assertNotBlank(vo.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        Validator.assertNotBlank(vo.getLabel(), ErrCodeSys.PA_PARAM_NULL, "节点名称");
        Validator.assertNotBlank(vo.getValue(), ErrCodeSys.PA_PARAM_NULL, "节点值");
        // 树形自引用：parent_dict_code = dict_code
        vo.setParentDictCode(vo.getDictCode());
        bizDictService.addDict(vo, null);
        bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        Resp.notice("节点添加成功");
    }

    @ApiOperation("更新树形字典节点")
    @PostMapping("/biz/item/update")
    public void updateTreeNode(@RequestBody BizDictVO vo) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "ID");
        bizDictService.updateDict(vo, null);
        // 刷新缓存
        if (vo.getDictCode() != null) {
            bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        }
        Resp.notice("节点更新成功");
    }

    @ApiOperation("删除树形字典节点")
    @PostMapping("/biz/item/delete")
    public void deleteTreeNode(@RequestParam Long id, @RequestParam String dictCode) {
        bizDictService.deleteDict(id, null);
        bizDictTreeCacheService.refreshSingle(dictCode);
        Resp.notice("节点删除成功");
    }

    @ApiOperation("移动树形字典节点（拖拽变更父节点/排序）")
    @PostMapping("/biz/item/move")
    public void moveTreeNode(@RequestBody BizDictVO vo) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "ID");
        Validator.assertNotBlank(vo.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        bizDictService.updateDict(vo, null);
        bizDictTreeCacheService.refreshSingle(vo.getDictCode());
        Resp.notice("节点移动成功");
    }
}
