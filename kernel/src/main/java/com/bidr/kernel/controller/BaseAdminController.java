package com.bidr.kernel.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Title: BaseAdminController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public abstract class BaseAdminController<ENTITY, VO> {

    @Resource
    private ApplicationContext applicationContext;


    @ApiOperation("添加数据")
    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@RequestBody ENTITY entity) {
        if (!isAdmin()) {
            beforeAdd(entity);
        }
        Boolean result = getRepo().insert(entity);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "新增失败");
        afterAdd(entity);
        Resp.notice("新增成功");
    }

    /**
     * 管理员查看全局数据
     */
    protected boolean isAdmin() {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            return getPortalService().isAdmin();
        } else {
            return false;
        }

    }

    protected void beforeAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeAdd(entity);
        }
    }

    protected BaseSqlRepo getRepo() {
        return (BaseSqlRepo) applicationContext.getBean(
                StrUtil.lowerFirst(getEntityClass().getSimpleName()) + "Service");
    }

    protected void afterAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterAdd(entity);
        }
    }

    protected PortalCommonService<ENTITY, VO> getPortalService() {
        return null;
    }

    protected Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    @ApiOperation("更新数据")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@RequestBody ENTITY entity) {
        if (!isAdmin()) {
            beforeUpdate(entity);
        }
        Boolean result = getRepo().updateById(entity);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "更新失败");
        afterUpdate(entity);
        Resp.notice("更新成功");
    }

    protected void beforeUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeUpdate(entity);
        }
    }

    protected void afterUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterUpdate(entity);
        }
    }

    @ApiOperation("更新数据")
    @RequestMapping(value = "/update/list", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@RequestBody List<ENTITY> entityList) {
        if (FuncUtil.isNotEmpty(entityList)) {
            for (ENTITY entity : entityList) {
                beforeUpdate(entity);
                Boolean result = getRepo().updateById(entity);
                Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "更新失败");
                afterUpdate(entity);
            }
        }
        Resp.notice("更新成功");
    }

    protected <T> boolean update(IdReqVO vo, SFunction<ENTITY, ?> bizFunc, T bizValue) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = (ENTITY) getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        LambdaUtil.setValue(entity, bizFunc, bizValue);
        return getRepo().updateById(entity, false);
    }

    protected boolean update(IdReqVO vo, Map<SFunction<ENTITY, ?>, ?> valueMap) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = (ENTITY) getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        if (FuncUtil.isNotEmpty(valueMap)) {
            for (Map.Entry<SFunction<ENTITY, ?>, ?> entry : valueMap.entrySet()) {
                LambdaUtil.setValue(entity, entry.getKey(), entry.getValue());
            }
        }
        return getRepo().updateById(entity, false);
    }

    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void delete(@RequestBody IdReqVO vo) {
        if (!isAdmin()) {
            beforeDelete(vo);
        }
        getRepo().deleteById(vo.getId());
        afterDelete(vo);
        Resp.notice("删除成功");
    }

    protected void beforeDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeDelete(vo);
        }
    }

    protected void afterDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterDelete(vo);
        }
    }

    @ApiOperation("根据id获取详情")
    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public VO queryById(IdReqVO req) {
        return Resp.convert(getRepo().selectById(req.getId()), getVoClass());
    }

    protected Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    @ApiOperation("通用查询数据")
    @RequestMapping(value = "/general/query", method = RequestMethod.POST)
    public Page<VO> generalQuery(@RequestBody QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return Resp.convert(getRepo().select(req), getVoClass());
    }

    /**
     * 配置全局查询参数
     *
     * @param req 查询条件
     */
    protected void beforeQuery(QueryConditionReq req) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeQuery(req);
        }
    }

    @ApiOperation("数据导出")
    @RequestMapping(value = "/advanced/query/export", method = RequestMethod.POST)
    public void advancedQueryExport(@RequestBody AdvancedQueryReq req, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        req.setPageSize(60000L);
        Page<VO> result = advancedQuery(req);

        String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String fileName = StrUtil.format("数据导出-{}", DateUtil.date().toString("yyyyMMddHHmmss"));
        byte[] exportBytes;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            exportBytes = getPortalService().export(result.getRecords());
            fileName = fileName + ".xlsx";
        } else {
            exportBytes = CsvUtil.exportCSV(result.getRecords(), getVoClass());
            fileName = fileName + ".csv";
        }
        HttpUtil.export(request, response, contentType, "UTF-8", fileName, exportBytes);

    }

    @ApiOperation("高级查询数据")
    @RequestMapping(value = "/advanced/query", method = RequestMethod.POST)
    public Page<VO> advancedQuery(@RequestBody AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = null;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
        }
        return Resp.convert(getRepo().select(req, aliasMap, wrapper, getVoClass()), getVoClass());
    }

    /**
     * 配置全局查询参数
     *
     * @param req 查询条件
     */
    protected void beforeQuery(AdvancedQueryReq req) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeQuery(req);
        }
    }

}
