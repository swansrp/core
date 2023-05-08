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
 * Title: BaseBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:44
 */
@SuppressWarnings("unchecked,rawtypes")
public abstract class BaseBindController<MASTER, BIND, SLAVE, MASTER_VO, SLAVE_VO> extends AdminController<SLAVE,
        SLAVE_VO> {

    protected Class<MASTER> getMasterClass() {
        return (Class<MASTER>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    protected Class<BIND> getBindClass() {
        return (Class<BIND>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected Class<SLAVE> getSlaveClass() {
        return (Class<SLAVE>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
    }

    protected Class<MASTER_VO> getMasterVoClass() {
        return (Class<MASTER_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 3);
    }

    protected Class<SLAVE_VO> getSlaveVoClass() {
        return (Class<SLAVE_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }


    protected Class<SLAVE> getEntityClass() {
        return (Class<SLAVE>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 2);
    }

    protected Class<SLAVE_VO> getVoClass() {
        return (Class<SLAVE_VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 4);
    }

    @ApiOperation(value = "获取已绑定")
    @RequestMapping(value = "/bind/query", method = RequestMethod.POST)
    public Page<MASTER_VO> getBind(@RequestBody QueryBindReq req) {
        IPage<MASTER> res = bindRepo().getBindList(req);
        return Resp.convert(res, getMasterVoClass());
    }

    protected abstract BaseBindRepo<MASTER, BIND, SLAVE> bindRepo();

    @ApiOperation(value = "绑定")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(@RequestBody BindReq req) {
        bindRepo().bind(req.getMasterIds(), req.getSlaveId());
        Resp.notice("绑定成功");
    }

    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/query", method = RequestMethod.POST)
    public Page<MASTER_VO> getUnBind(@RequestBody QueryBindReq req) {
        IPage<MASTER> res = bindRepo().getUnbindList(req);
        return Resp.convert(res, getMasterVoClass());
    }

    @ApiOperation(value = "解绑")
    @RequestMapping(value = "/unbind", method = RequestMethod.POST)
    public void unbind(@RequestBody BindReq req) {
        bindRepo().unbind(req.getMasterIds(), req.getSlaveId());
        Resp.notice("解绑成功");
    }
}
