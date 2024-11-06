package com.bidr.platform.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.portal.AdminPortal;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.dict.TreeDictService;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AdminDictController
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

    @ApiOperation("根据字典中文名模糊查询")
    @RequestMapping(path = {"/all"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getAllTreeDict() {
        return treeDictService.getAll();
    }

    @ApiOperation("刷新缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh(@RequestBody QuerySysConfigReq req) {
        treeDictService.refresh();
        Resp.notice("系统树形字典修改已生效");
    }
}
