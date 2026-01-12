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
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.*;
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

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
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
@SuppressWarnings("unchecked")
public class BaseAdminController<ENTITY, VO> implements AdminControllerInf<ENTITY, VO> {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    @ApiOperation("添加数据")
    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@RequestBody VO vo) {
        insertEntity(vo);
        Resp.notice("新增成功");
    }

    @Override
    @ApiOperation("添加数据")
    @RequestMapping(value = "/insert/list", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@RequestBody List<VO> voList) {
        if (FuncUtil.isNotEmpty(voList)) {
            for (VO vo : voList) {
                insertEntity(vo);
            }
        }
        Resp.notice("新增成功");
    }

    @Override
    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void delete(@RequestBody IdReqVO vo) {
        deleteEntity(vo);
        Resp.notice("删除成功");
    }

    @Override
    @ApiOperation("删除数据列表")
    @RequestMapping(value = "/delete/list", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void deleteList(@RequestBody List<String> idList) {
        if (FuncUtil.isNotEmpty(idList)) {
            for (String id : idList) {
                deleteEntity(new IdReqVO(id));
            }
        }
        Resp.notice("删除列表成功");
    }

    @Override
    @ApiOperation("更新数据")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@RequestBody VO vo, @RequestParam(required = false) boolean strict) {
        updateEntity(vo, strict);
        Resp.notice("更新成功");
    }

    @Override
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
    @ApiOperation("根据id获取详情")
    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public VO queryById(IdReqVO req) {
        if (FuncUtil.isEmpty(getPortalService())) {
            return Resp.convert(getRepo().selectById(req.getId()), getVoClass());
        } else {
            return Resp.convert(getPortalService().selectById(req.getId()), getVoClass());
        }
    }

    @Override
    @ApiOperation("通用查询数据")
    @RequestMapping(value = "/general/query", method = RequestMethod.POST)
    public Page<VO> generalQuery(@RequestBody QueryConditionReq req) {
        Page<VO> result = queryByGeneralReq(req);
        return Resp.convert(result, getVoClass());
    }

    @Override
    @ApiOperation("通用查询数据(不分页)")
    @RequestMapping(value = "/general/select", method = RequestMethod.POST)
    public List<VO> generalSelect(@RequestBody QueryConditionReq req) {
        List<VO> result = selectByGeneralReq(req);
        return Resp.convert(result, getVoClass());
    }

    @Override
    @ApiOperation("高级查询数据")
    @RequestMapping(value = "/advanced/query", method = RequestMethod.POST)
    public Page<VO> advancedQuery(@RequestBody AdvancedQueryReq req) {
        Page<VO> result = queryByAdvancedReq(req);
        return Resp.convert(result, getVoClass());
    }

    @Override
    @ApiOperation("高级查询数据(不分页)")
    @RequestMapping(value = "/advanced/select", method = RequestMethod.POST)
    public List<VO> advancedSelect(@RequestBody AdvancedQueryReq req) {
        List<VO> result = selectByAdvancedReq(req);
        return Resp.convert(result, getVoClass());
    }

    @Override
    public BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo() {
        return (BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY>) applicationContext.getBean(
                Introspector.decapitalize(getEntityClass().getSimpleName()) + "Service");
    }

    @Override
    @ApiOperation("个数统计")
    @RequestMapping(value = "/general/count", method = RequestMethod.POST)
    public Long generalCount(@RequestBody QueryConditionReq req) {
        return countByGeneralReq(req);
    }

    @Override
    @ApiOperation("统计个数")
    @RequestMapping(value = "/advanced/count", method = RequestMethod.POST)
    public Long advancedCount(@RequestBody AdvancedQueryReq req) {
        return countByAdvancedReq(req);
    }

    @Override
    @ApiOperation("汇总")
    @RequestMapping(value = "/general/summary", method = RequestMethod.POST)
    public Map<String, Object> generalSummary(@RequestBody GeneralSummaryReq req) {
        return summaryByGeneralReq(req);
    }

    @Override
    @ApiOperation("汇总")
    @RequestMapping(value = "/advanced/summary", method = RequestMethod.POST)
    public Map<String, Object> advancedSummary(@RequestBody AdvancedSummaryReq req) {
        return summaryByAdvancedReq(req);
    }

    @Override
    @ApiOperation("指标统计")
    @RequestMapping(value = "/general/statistic", method = RequestMethod.POST)
    public List<StatisticRes> generalStatistic(@RequestBody GeneralStatisticReq req) {
        return statisticByGeneralReq(req);
    }

    @Override
    @ApiOperation("指标统计")
    @RequestMapping(value = "/advanced/statistic", method = RequestMethod.POST)
    public List<StatisticRes> advancedStatistic(@RequestBody AdvancedStatisticReq req) {
        return statisticByAdvancedReq(req);
    }

    @Override

    @SneakyThrows
    @ApiOperation("数据导出")
    @RequestMapping(value = "/advanced/query/export", method = RequestMethod.POST)
    public void advancedQueryExport(@RequestBody AdvancedQueryReq req, @RequestParam(required = false) String name,
                                    HttpServletRequest request, HttpServletResponse response) {
        req.setCurrentPage(1L);
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

    @Override

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
    @ApiOperation("导入新增")
    @RequestMapping(value = "/import/add", method = RequestMethod.POST)
    public void importAdd(@RequestParam(required = false) String name, MultipartFile file) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "导入新增操作");
        try {
            getPortalService().validateReadExcel();
            getPortalService().readExcelForInsert(file.getInputStream(), name);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    @Override
    @ApiOperation("获取导入新增进度")
    @RequestMapping(value = "/import/add/progress", method = RequestMethod.GET)
    public Object importAddProgress(@RequestParam(required = false) String name) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "获取导入新增进度操作");
        return getPortalService().getUploadProgressRes(name);
    }

    @Override

    @SneakyThrows
    @ApiOperation("导入修改")
    @RequestMapping(value = "/import/update", method = RequestMethod.POST)
    public void importUpdate(@RequestParam(required = false) String name, MultipartFile file) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "导入修改操作");
        try {
            getPortalService().validateReadExcel();
            getPortalService().readExcelForUpdate(file.getInputStream(), name);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    @Override

    @SneakyThrows
    @ApiOperation("导入修改进度")
    @RequestMapping(value = "/import/update/progress", method = RequestMethod.GET)
    public Object importUpdateProgress(@RequestParam(required = false) String name) {
        Validator.assertNotNull(getPortalService(), ErrCodeSys.PA_DATA_NOT_SUPPORT, "获取导入修改进度操作");
        return getPortalService().getUploadProgressRes(name);
    }
}
