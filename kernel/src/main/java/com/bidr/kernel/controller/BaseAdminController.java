package com.bidr.kernel.controller;

import cn.hutool.core.util.StrUtil;
import com.bidr.kernel.common.func.SetFunc;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;

/**
 * Title: BaseController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/28 11:03
 */

@SuppressWarnings("rawtypes, unchecked")
public abstract class BaseAdminController<ENTITY> {

    Class<ENTITY> entityClass = (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    @Resource
    private ApplicationContext applicationContext;

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

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public Boolean delete(@RequestBody IdReqVO vo) {
        return getRepo().deleteById(vo.getId());
    }

    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public ENTITY queryById(Object id) {
        return (ENTITY) getRepo().selectById(id);
    }


}
