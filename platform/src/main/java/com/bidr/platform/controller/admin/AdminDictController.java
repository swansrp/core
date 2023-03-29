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

    @RequestMapping(path = {"/list/dictTitle"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getDictNameList(String name) {
        return dictService.getNameList(name);
    }

    @RequestMapping(path = {"/list/dictName"}, method = {RequestMethod.GET})
    public List<SysDict> getDictByName(String dictName) {
        return dictService.getSysDictByName(dictName);
    }

    @RequestMapping(path = {"/add"}, method = {RequestMethod.POST})
    public Boolean addDict(@RequestBody AddDictItemReq req) {
        boolean result = dictService.addDictItem(req);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "添加失败");
        return null;
    }

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

    @RequestMapping(value = "/default/item/update", method = RequestMethod.POST)
    public Boolean updateDefault(@RequestBody UpdateDictDefaultReq vo) {
        dictService.replaceDefaultDictItem(vo);
        return null;
    }

}
