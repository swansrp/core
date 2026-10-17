package com.bidr.platform.controller.admin;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.service.cache.dict.BizDictTreeCacheService;
import com.bidr.platform.service.dict.TreeDictService;
import com.bidr.platform.vo.dict.DictRes;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AdminTreeDictController
 * Description: 树形字典只读查询接口（CRUD操作见 SystemBizTreeDictController）
 *
 * @author Sharp
 * @since 2023/03/28 08:49
 */
@Api(tags = "系统管理 - 树形字典查询")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dict/tree/admin"})
public class AdminTreeDictController {

    private final TreeDictService treeDictService;
    private final BizDictTreeCacheService bizDictTreeCacheService;

    @ApiOperation("获取所有树形字典（代码驱动 + 业务手动创建）")
    @RequestMapping(path = {"/all"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getAllTreeDict() {
        List<KeyValueResVO> result = new ArrayList<>(treeDictService.getAll());
        // 合并业务手动创建的树形字典
        List<DictRes> bizTreeList = bizDictTreeCacheService.getTreeDictList(null);
        if (bizTreeList != null) {
            for (DictRes dict : bizTreeList) {
                result.add(new KeyValueResVO(dict.getValue(), dict.getLabel()));
            }
        }
        return result;
    }

    @ApiOperation("刷新代码驱动树形字典缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh(@RequestBody QuerySysConfigReq req) {
        treeDictService.refresh();
        Resp.notice("系统树形字典修改已生效");
    }

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
}
