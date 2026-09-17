package com.bidr.platform.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.portal.AdminPortal;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.dict.DictService;
import com.bidr.platform.vo.dict.AddDictItemReq;
import com.bidr.platform.vo.dict.UpdateDictDefaultReq;
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
@Api(tags = "系统管理 - 字典管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dict/item/admin"})
public class AdminDictController extends BaseAdminOrderController<SysDict, SysDict> {

    private final DictService dictService;

    @ApiOperation("根据字典中文名模糊查询")
    @RequestMapping(path = {"/list/dictTitle"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getDictNameList(String name) {
        return dictService.getNameList(name);
    }

    @ApiOperation("根据字典名查询")
    @RequestMapping(path = {"/list/dictName"}, method = {RequestMethod.GET})
    public List<SysDict> getDictByName(String dictName) {
        return dictService.getSysDictByName(dictName);
    }

    @ApiOperation("添加字典项")
    @RequestMapping(path = {"/add"}, method = {RequestMethod.POST})
    public Boolean addDict(@RequestBody AddDictItemReq req) {
        boolean result = dictService.addDictItem(req);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "添加失败");
        return null;
    }

    @ApiOperation("默认字典项更新")
    @RequestMapping(value = "/default/item/update", method = RequestMethod.POST)
    public Boolean updateDefault(@RequestBody UpdateDictDefaultReq vo) {
        dictService.replaceDefaultDictItem(vo);
        return null;
    }


    @Override
    protected SFunction<SysDict, ?> id() {
        return SysDict::getDictId;
    }

    /**
     * 条目新增前置拦截：内置字典的条目由枚举/动态字典开机重建，不允许界面加；
     * read_only 由所属字典类型接管，不让业务人员在表单上手工填
     */
    @Override
    public void adminBeforeAdd(SysDict entity) {
        dictService.prepareAddDictItem(entity);
    }

    /**
     * 内置字典条目（read_only='1'）改了下一次启动就会被代码覆写，一律拒绝；read_only 由后端接管
     */
    @Override
    public void adminBeforeUpdate(SysDict entity) {
        dictService.assertDictItemUpdate(entity);
    }

    /**
     * /delete 与 /delete/list 都经 base deleteEntity 走到这里，批量删除同样受只读保护
     */
    @Override
    public void adminBeforeDelete(IdReqVO vo) {
        dictService.assertDictItemEditable(vo.getId());
    }

    @Override
    protected SFunction<SysDict, Integer> order() {
        return SysDict::getDictSort;
    }

    @ApiOperation("刷新缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh(@RequestBody QuerySysConfigReq req) {
        dictService.refresh();
        Resp.notice("系统字典修改已生效");
    }
}
