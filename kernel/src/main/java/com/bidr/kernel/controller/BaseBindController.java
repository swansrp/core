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
public abstract class BaseBindController<ENTITY, VO, BIND> {

    protected Class<ENTITY> entityClass = (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    protected Class<VO> voClass = (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);

    @ApiOperation(value = "获取已绑定")
    @RequestMapping(value = "/bind/query", method = RequestMethod.POST)
    public Page<VO> getBind(@RequestBody QueryBindReq req) {
        IPage<ENTITY> res = bindRepo().getBindList(req);
        return Resp.convert(res, voClass);
    }

    protected abstract BaseBindRepo<ENTITY, BIND> bindRepo();

    @ApiOperation(value = "绑定")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(@RequestBody BindReq req) {
        bindRepo().bind(req.getMasterIds(), req.getSlaveId());
        Resp.notice("绑定成功");
    }

    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/query", method = RequestMethod.POST)
    public Page<VO> getUnBind(@RequestBody QueryBindReq req) {
        IPage<ENTITY> res = bindRepo().getUnbindUserList(req);
        return Resp.convert(res, voClass);
    }

    @ApiOperation(value = "解绑")
    @RequestMapping(value = "/unbind", method = RequestMethod.POST)
    public void unbind(@RequestBody BindReq req) {
        bindRepo().unbind(req.getMasterIds(), req.getSlaveId());
        Resp.notice("解绑成功");
    }
}
