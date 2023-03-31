package com.bidr.kernel.controller;

import cn.hutool.core.util.StrUtil;
import com.bidr.kernel.common.func.SetFunc;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.diboot.core.binding.Binder;
import io.swagger.annotations.ApiOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;

/**
 * Title: AdminController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public abstract class AdminController<ENTITY, VO> {
    protected Class<ENTITY> entityClass = (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    protected Class<VO> voClass = (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    @Resource
    private ApplicationContext applicationContext;

    @ApiOperation("更新数据")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public Boolean update(@RequestBody ENTITY entity) {
        Boolean result = getRepo().updateById(entity);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "更新失败");
        return null;
    }

    protected BaseSqlRepo getRepo() {
        return (BaseSqlRepo) applicationContext.getBean(StrUtil.lowerFirst(entityClass.getSimpleName()) + "Service");
    }

    protected <T> boolean update(IdReqVO vo, SetFunc<ENTITY, T> bizFunc, T bizValue) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = (ENTITY) getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        bizFunc.apply(entity, bizValue);
        return getRepo().updateById(entity, false);
    }

    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public Boolean delete(@RequestBody IdReqVO vo) {
        return getRepo().deleteById(vo.getId());
    }

    @ApiOperation("根据id获取详情")
    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public VO queryById(IdReqVO req) {
        return Binder.convertAndBindRelations(getRepo().selectById(req.getId()), voClass);
    }

}
