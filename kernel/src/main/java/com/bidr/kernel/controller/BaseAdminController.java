package com.bidr.kernel.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.CsvUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
@Slf4j
@SuppressWarnings("rawtypes, unchecked")
public class BaseAdminController<ENTITY, VO> implements AdminControllerInf<ENTITY, VO> {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    @ApiIgnore
    @ApiOperation("添加数据")
    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@RequestBody VO vo) {
        insertEntity(vo);
        Resp.notice("新增成功");
    }

    public void insertEntity(VO vo) {
        ENTITY entity = ReflectionUtil.copy(vo, getEntityClass());
        if (isAdmin()) {
            adminBeforeAdd(entity);
        } else {
            beforeAdd(entity);
        }
        Boolean result = getRepo().insert(entity);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "新增失败");
        afterAdd(entity);
    }

    @Override
    @ApiIgnore
    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void delete(@RequestBody IdReqVO vo) {
        if (isAdmin()) {
            adminBeforeDelete(vo);
        } else {
            beforeDelete(vo);
        }
        getRepo().deleteById(vo.getId());
        afterDelete(vo);
        Resp.notice("删除成功");
    }

    @Override
    @ApiIgnore
    @ApiOperation("更新数据")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@RequestBody VO vo, @RequestParam(required = false) boolean strict) {
        updateEntity(vo, strict);
        Resp.notice("更新成功");
    }

    public void updateEntity(VO vo, Boolean strict) {
        ENTITY entity = ReflectionUtil.copy(vo, getEntityClass());
        if (!strict) {
            ENTITY originalEntity = getRepo().selectById(entity);
            entity = ReflectionUtil.merge(entity, originalEntity, true);
        }
        if (isAdmin()) {
            adminBeforeUpdate(entity);
        } else {
            beforeUpdate(entity);
        }
        Boolean result = getRepo().updateById(entity, !strict);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "更新失败");
        afterUpdate(entity);
    }

    @Override
    @ApiIgnore
    @ApiOperation("更新数据")
    @RequestMapping(value = "/update/list", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@RequestBody List<VO> entityList, @RequestParam(required = false) boolean strict) {
        if (FuncUtil.isNotEmpty(entityList)) {
            for (VO vo : entityList) {
                updateEntity(vo, strict);
            }
        }
        Resp.notice("更新成功");
    }

    @Override
    @ApiIgnore
    @ApiOperation("根据id获取详情")
    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public VO queryById(IdReqVO req) {
        return Resp.convert(getRepo().selectById(req.getId()), getVoClass());
    }

    @Override
    @ApiIgnore
    @ApiOperation("通用查询数据")
    @RequestMapping(value = "/general/query", method = RequestMethod.POST)
    public Page<VO> generalQuery(@RequestBody QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return Resp.convert(getRepo().select(req), getVoClass());
    }

    @Override
    @ApiIgnore
    @ApiOperation("高级查询数据")
    @RequestMapping(value = "/advanced/query", method = RequestMethod.POST)
    public Page<VO> advancedQuery(@RequestBody AdvancedQueryReq req) {
        Page<ENTITY> result = queryByAdvancedReq(req);
        return Resp.convert(result, getVoClass());
    }

    @Override
    @ApiIgnore
    @SneakyThrows
    @ApiOperation("数据导出")
    @RequestMapping(value = "/advanced/query/export", method = RequestMethod.POST)
    public void advancedQueryExport(@RequestBody AdvancedQueryReq req, @RequestParam(required = false) String name,
                                    HttpServletRequest request, HttpServletResponse response) {
        req.setPageSize(60000L);
        Page<VO> result = advancedQuery(req);

        String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String fileName = StrUtil.format("数据导出-{}", DateUtil.date().toString("yyyyMMddHHmmss"));
        byte[] exportBytes;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            exportBytes = getPortalService().export(result.getRecords(), name);
            fileName = fileName + ".xlsx";
        } else {
            exportBytes = CsvUtil.exportCSV(result.getRecords(), getVoClass());
            fileName = fileName + ".csv";
        }
        HttpUtil.export(request, response, contentType, "UTF-8", fileName, exportBytes);
    }

    protected Page<ENTITY> queryByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = null;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            wrapper.setEntityClass(getEntityClass());
        }
        Page<ENTITY> result = getRepo().select(req, aliasMap, wrapper, getVoClass());
        return result;
    }

    @Override
    @ApiIgnore
    @SneakyThrows
    @ApiOperation("模版导出")
    @RequestMapping(value = "/template/export", method = RequestMethod.GET)
    public void templateExport(@RequestParam(required = false) String name, HttpServletRequest request,
                               HttpServletResponse response) {
        String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String fileName = StrUtil.format("数据导出-{}", DateUtil.date().toString("yyyyMMddHHmmss"));
        byte[] exportBytes;
        if (FuncUtil.isNotEmpty(getPortalService())) {
            exportBytes = getPortalService().templateExport(name);
            fileName = fileName + ".xlsx";
        } else {
            exportBytes = CsvUtil.exportCSV(null, getVoClass());
            fileName = fileName + ".csv";
        }
        HttpUtil.export(request, response, contentType, "UTF-8", fileName, exportBytes);
    }

    @Override
    @ApiIgnore
    @ApiOperation("导入新增")
    @RequestMapping(value = "/import/add", method = RequestMethod.POST)
    public void importAdd(@RequestParam(required = false) String name, MultipartFile file) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "导入新增操作");
        try {
            getPortalService().readExcelForInsert(file.getInputStream(), name);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    @Override
    @ApiIgnore
    @ApiOperation("获取导入新增进度")
    @RequestMapping(value = "/import/add/progress", method = RequestMethod.GET)
    public Object importAddProgress(@RequestParam(required = false) String name) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "获取导入新增进度操作");
        return getPortalService().getUploadProgressRes(name);
    }

    @Override
    @ApiIgnore
    @SneakyThrows
    @ApiOperation("导入修改")
    @RequestMapping(value = "/import/update", method = RequestMethod.POST)
    public void importUpdate(@RequestParam(required = false) String name, MultipartFile file) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "导入修改操作");
        try {
            getPortalService().readExcelForUpdate(file.getInputStream(), name);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    @Override
    @ApiIgnore
    @SneakyThrows
    @ApiOperation("导入修改进度")
    @RequestMapping(value = "/import/update/progress", method = RequestMethod.GET)
    public Object importUpdateProgress(@RequestParam(required = false) String name) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "获取导入修改进度操作");
        return getPortalService().getUploadProgressRes(name);
    }

    @Override
    public BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo() {
        return (BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY>) applicationContext.getBean(
                StrUtil.lowerFirst(getEntityClass().getSimpleName()) + "Service");
    }

}
