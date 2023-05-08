package com.bidr.platform.controller.admin;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.AdminController;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.service.dict.DictService;
import com.bidr.platform.vo.dict.AddDictReq;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminDictTypeController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/29 14:01
 */

@Api(tags = {"系统字典管理"})
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dict/admin"})
public class AdminDictTypeController extends AdminController<SysDictType, SysDictType> {
    private final DictService dictService;

    @RequestMapping(path = {"/add"}, method = {RequestMethod.POST})
    public Boolean addDict(@RequestBody AddDictReq req) {
        boolean result = dictService.addDict(req);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "添加失败");
        return null;
    }

    @Override
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public Boolean delete(@RequestBody IdReqVO vo) {
        dictService.deleteDict(JsonUtil.readJson(vo.getId(), String.class));
        return null;
    }
}
