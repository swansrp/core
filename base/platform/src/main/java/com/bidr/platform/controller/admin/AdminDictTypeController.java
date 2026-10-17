package com.bidr.platform.controller.admin;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.platform.config.portal.AdminPortal;
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

@Api(tags = "系统管理 - 字典管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/dict/admin"})
public class AdminDictTypeController extends BaseAdminController<SysDictType, SysDictType> {
    private final DictService dictService;

    @RequestMapping(path = {"/add"}, method = {RequestMethod.POST})
    public Boolean addDict(@RequestBody AddDictReq req) {
        boolean result = dictService.addDict(req);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "添加失败");
        return null;
    }

    /**
     * 接管删除：不能走 base 的 deleteById（只删 sys_dict_type，会留下 sys_dict 孤立条目），
     * 改为 override deleteEntity 而非 delete，这样 /delete 与 /delete/list 都能拿到级联与只读校验。
     * 另注：字典类型主键 dict_name 是非数字字符串，历史上这里曾用 JsonUtil.readJson(vo.getId(), String.class)
     * 二次解析，未加引号的字符串不是合法 JSON，解析异常被吞掉后返回 null，导致按 null 主键删除
     * 影响 0 行却仍提示"删除成功"，前端表现为点删除无任何效果。
     */
    @Override
    public void deleteEntity(IdReqVO vo) {
        Validator.assertNotBlank(vo.getId(), ErrCodeSys.SYS_ERR_MSG, "字典类型不能为空");
        dictService.deleteDict(vo.getId());
    }

    /**
     * 内置字典（read_only='1'）以代码 @MetaDict 声明为正源、开机重建，改标题下次启动就会被覆写，一律拒绝；
     * read_only 列同时由后端接管，不让界面手工置 '1'
     */
    @Override
    public void adminBeforeUpdate(SysDictType entity) {
        dictService.assertDictTypeUpdate(entity);
    }
}
