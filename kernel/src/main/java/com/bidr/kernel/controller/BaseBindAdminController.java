package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.bind.BindReq;
import com.bidr.kernel.vo.bind.QueryBindReq;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Title: BaseBindAdminController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:44
 */
@SuppressWarnings("unchecked,rawtypes")
public abstract class BaseBindAdminController<ENTITY, BIND, ATTACH, ENTITY_VO, ATTACH_VO> extends AdminController<ENTITY,
        ENTITY_VO> {


    protected Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    protected Class<ENTITY_VO> getVoClass() {
        return (Class<ENTITY_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }

    protected Class<BIND> getBindClass() {
        return (Class<BIND>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected Class<ATTACH> getAttachClass() {
        return (Class<ATTACH>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
    }

    @ApiOperation(value = "获取已绑定")
    @RequestMapping(value = "/bind/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getBind(@RequestBody QueryBindReq req) {
        IPage<ATTACH> res = bindRepo().getBindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    protected abstract BaseBindRepo<ENTITY, BIND, ATTACH> bindRepo();

    protected Class<ATTACH_VO> getAttachVoClass() {
        return (Class<ATTACH_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }

    @ApiOperation(value = "绑定")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(@RequestBody BindReq req) {
        bindRepo().bind(req.getAttachId(), req.getEntityId());
        Resp.notice("绑定成功");
    }

    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/query", method = RequestMethod.POST)
    public Page<ATTACH_VO> getUnBind(@RequestBody QueryBindReq req) {
        IPage<ATTACH> res = bindRepo().getUnbindList(req);
        return Resp.convert(res, getAttachVoClass());
    }

    @ApiOperation(value = "解绑")
    @RequestMapping(value = "/unbind", method = RequestMethod.POST)
    public void unbind(@RequestBody BindReq req) {
        bindRepo().unbind(req.getAttachId(), req.getEntityId());
        Resp.notice("解绑成功");
    }
}
