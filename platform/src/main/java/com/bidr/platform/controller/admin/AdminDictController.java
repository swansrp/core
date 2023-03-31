package com.bidr.platform.controller.admin;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.service.dict.DictService;
import com.bidr.platform.vo.dict.AddDictItemReq;
import com.bidr.platform.vo.dict.UpdateDictDefaultReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AdminDictController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/28 08:49
 */
@Api(tags = {"系统字典管理"})
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/dict/item/admin"})
public class AdminDictController extends BaseAdminController<SysDict> {

    private final DictService dictService;
    private final SysDictService sysDictService;

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

    @ApiOperation("字典项顺序更新")
    @RequestMapping(value = "/order/update", method = RequestMethod.POST)
    public Boolean updateOrder(@RequestBody List<IdOrderReqVO> idOrderReqVOList) {
        List<SysDict> entityList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(idOrderReqVOList)) {
            for (IdOrderReqVO acMenu : idOrderReqVOList) {
                SysDict entity = new SysDict();
                entity.setDictId(Long.valueOf(acMenu.getId().toString()));
                entity.setDictSort(acMenu.getShowOrder());
                entityList.add(entity);
            }
            getRepo().updateBatchById(entityList);
        }
        return null;
    }
    @ApiOperation("默认字典项更新")
    @RequestMapping(value = "/default/item/update", method = RequestMethod.POST)
    public Boolean updateDefault(@RequestBody UpdateDictDefaultReq vo) {
        dictService.replaceDefaultDictItem(vo);
        return null;
    }

}
