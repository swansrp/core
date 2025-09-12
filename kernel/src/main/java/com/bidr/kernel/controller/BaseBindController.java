package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.bind.*;
import com.bidr.kernel.vo.portal.Query;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * Title: BaseBindController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 17:02
 */
public abstract class BaseBindController<ENTITY, BIND, ATTACH, ENTITY_VO, ATTACH_VO> extends BaseBindRepo<ENTITY, BIND, ATTACH, ENTITY_VO, ATTACH_VO> {

    @ApiIgnore
    @ApiOperation(value = "获取已绑定(列表)")
    @RequestMapping(value = "/bind/list", method = RequestMethod.GET)
    public List<ATTACH_VO> getBindList(String entityId) {
        List<ATTACH_VO> res = bindRepo().getBindList(entityId);
        return Resp.convert(res, getAttachVoClass());
    }

    protected BaseBindRepo<ENTITY, BIND, ATTACH, ENTITY_VO, ATTACH_VO> bindRepo() {
        return this;
    }

    protected Class<ATTACH_VO> getAttachVoClass() {
        return (Class<ATTACH_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }

    @ApiIgnore
    @ApiOperation(value = "获取已绑定(分页)")
    @RequestMapping(value = "/bind/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getBind(@RequestBody @Validated QueryBindReq req) {
        IPage<ATTACH_VO> res = bindRepo().queryAttachList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiIgnore
    @ApiOperation(value = "获取已绑定(分页)")
    @RequestMapping(value = "/bind/advanced/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getBind(@RequestBody @Validated AdvancedQueryBindReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        IPage<ATTACH_VO> res = bindRepo().advancedQueryAttachList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    protected void defaultQuery(Query req) {
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            getAttachPortalService().defaultQuery(req);
        }
    }

    protected boolean isAdmin() {
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            return getAttachPortalService().isAdmin();
        } else {
            return false;
        }
    }

    protected void beforeQuery(AdvancedQueryBindReq req) {
        if (FuncUtil.isNotEmpty(getAttachPortalService())) {
            getAttachPortalService().beforeQuery(req);
        }
    }

    @ApiIgnore
    @ApiOperation(value = "查询绑定实体(分页)")
    @RequestMapping(value = "/bind/attach/query", method = RequestMethod.POST)
    public Page<?> getAttach(@RequestBody @Validated QueryBindReq req) {
        Validator.assertNotNull(attachAdminController(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "实体列表");
        return attachAdminController().generalQuery(req);
    }

    protected BaseAdminController<ATTACH, ?> attachAdminController() {
        return null;
    }

    @ApiIgnore
    @ApiOperation(value = "查询绑定实体(分页)")
    @RequestMapping(value = "/attach/advanced/query", method = RequestMethod.POST)
    public Page<?> getAttach(@RequestBody @Validated AdvancedQueryBindReq req) {
        Validator.assertNotNull(attachAdminController(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "实体列表");
        return attachAdminController().advancedQuery(req);
    }

    @ApiIgnore
    @ApiOperation(value = "修改绑定信息")
    @RequestMapping(value = "/bind/info", method = RequestMethod.POST)
    public void bindInfo(@RequestBody @Validated BindInfoReq req, @RequestParam Object entityId,
                         @RequestParam(required = false) boolean strict) {
        bindRepo().bindInfo(entityId, req.getAttachId(), req.getData(), strict);
        Resp.notice("修改信息成功");
    }

    @ApiIgnore
    @ApiOperation(value = "修改绑定信息(列表)")
    @RequestMapping(value = "/bind/info/list", method = RequestMethod.POST)
    public void bindInfoList(@RequestBody @Validated List<BindInfoReq> req, @RequestParam Object entityId,
                             @RequestParam(required = false) boolean strict) {
        bindRepo().bindInfoList(entityId, req, strict);
        Resp.notice("修改信息成功");
    }

    @ApiIgnore
    @ApiOperation(value = "绑定")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(@RequestBody @Validated BindReq req) {
        bindRepo().bind(req.getAttachId(), req.getEntityId());
        Resp.notice("绑定成功");
    }

    @ApiIgnore
    @ApiOperation(value = "批量绑定")
    @RequestMapping(value = "/bind/batch", method = RequestMethod.POST)
    public void bind(@RequestBody @Validated BindListReq req) {
        bindRepo().bindList(req.getAttachIdList(), req.getEntityId());
        Resp.notice("绑定成功");
    }

    @ApiIgnore
    @ApiOperation(value = "全量绑定")
    @RequestMapping(value = "/bind/all", method = RequestMethod.POST)
    public void bindAllByCondition(@RequestBody @Validated AdvancedQueryBindReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        req.setCurrentPage(1L);
        req.setPageSize(60000L);
        bindRepo().bindAll(req);
        Resp.notice("绑定成功");
    }

    @ApiIgnore
    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getUnBind(@RequestBody @Validated QueryBindReq req) {
        IPage<ATTACH_VO> res = bindRepo().getUnbindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiIgnore
    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/advanced/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getUnBind(@RequestBody @Validated AdvancedQueryBindReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        IPage<ATTACH_VO> res = bindRepo().advancedQueryUnbindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiIgnore
    @ApiOperation(value = "解绑")
    @RequestMapping(value = "/unbind", method = RequestMethod.POST)
    public void unbind(@RequestBody @Validated BindReq req) {
        bindRepo().unbind(req.getAttachId(), req.getEntityId());
        Resp.notice("解绑成功");
    }

    @ApiIgnore
    @ApiOperation(value = "批量解绑")
    @RequestMapping(value = "/unbind/batch", method = RequestMethod.POST)
    public void unbind(@RequestBody @Validated BindListReq req) {
        bindRepo().unbindList(req.getAttachIdList(), req.getEntityId());
        Resp.notice("解绑成功");
    }

    @ApiIgnore
    @ApiOperation(value = "全量解绑")
    @RequestMapping(value = "/unbind/all", method = RequestMethod.POST)
    public void unbindAll(@RequestBody @Validated BindBaseReq req) {
        bindRepo().unbindAll(req.getEntityId());
        Resp.notice("解绑成功");
    }

    @ApiIgnore
    @ApiOperation(value = "替换绑定")
    @RequestMapping(value = "/replace", method = RequestMethod.POST)
    public void replace(@RequestBody @Validated BindListReq req) {
        bindRepo().replace(req.getAttachIdList(), req.getEntityId());
        Resp.notice("替换成功");
    }

    @ApiIgnore
    @ApiOperation(value = "替换绑定")
    @RequestMapping(value = "/advanced/replace", method = RequestMethod.POST)
    public void replace(@RequestBody @Validated AdvancedQueryBindReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        req.setCurrentPage(1L);
        req.setPageSize(60000L);
        bindRepo().advancedReplace(req);
        Resp.notice("替换成功");
    }
}
