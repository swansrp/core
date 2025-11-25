package com.bidr.forge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.service.driver.DatasetDriver;
import com.bidr.forge.service.driver.MatrixDriver;
import com.bidr.forge.service.driver.PortalDataDriver;
import com.bidr.forge.service.driver.PortalDataMode;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.*;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.GeneralStatisticReq;
import com.bidr.kernel.vo.portal.statistic.GeneralSummaryReq;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: DynamicPortalController
 * Description: 动态Portal控制器，支持Matrix和Dataset模式的统一访问
 * 这是唯一允许使用@PathVariable的Controller，用于通过路径区分不同Portal
 * Copyright: Copyright (c) 2025
 *
 * @author Sharp
 * @since 2025/11/24
 */
@Slf4j
@RestController
@Api(tags = "动态Portal接口")
@RequestMapping("/web/dynamic/portal")
public class DynamicPortalController {

    @Resource
    private MatrixDriver matrixDriver;

    @Resource
    private DatasetDriver datasetDriver;

    @Resource
    private SysPortalService sysPortalService;

    /**
     * 根据portalName获取对应的驱动
     */
    private PortalDataDriver<Map<String, Object>> getDriver(String portalName) {


        SysPortal portal = sysPortalService.getByName(portalName, getRoleId());
        Validator.assertNotNull(portal, ErrCodeSys.SYS_ERR_MSG, "Portal配置不存在: " + portalName);

        String dataMode = portal.getDataMode();
        Validator.assertNotBlank(dataMode, ErrCodeSys.PA_DATA_NOT_SUPPORT, "Portal未配置数据模式");

        if (PortalDataMode.MATRIX.name().equals(dataMode)) {
            return matrixDriver;
        } else if (PortalDataMode.DATASET.name().equals(dataMode)) {
            return datasetDriver;
        }

        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "不支持的数据模式: " + dataMode);
    }

    /**
     * 获取roleId（从ThreadLocal或Session中获取）
     */
    private Long getRoleId() {
        return PortalConfigContext.getPortalConfigRoleId();
    }

    // ==================== 查询接口 ====================

    @ApiOperation("根据id获取详情")
    @GetMapping("/{portalName}/id")
    public Map<String, Object> queryById(@PathVariable String portalName, IdReqVO req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq queryReq = new AdvancedQueryReq();
        queryReq.setCurrentPage(1L);
        queryReq.setPageSize(1L);
        // TODO: 添加id条件
        return driver.queryOne(queryReq, portalName, getRoleId());
    }

    @ApiOperation("通用查询数据")
    @PostMapping("/{portalName}/general/query")
    public Page<Map<String, Object>> generalQuery(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.queryPage(advReq, portalName, getRoleId());
    }

    @ApiOperation("通用查询数据(不分页)")
    @PostMapping("/{portalName}/general/select")
    public List<Map<String, Object>> generalSelect(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.queryList(advReq, portalName, getRoleId());
    }

    @ApiOperation("高级查询数据")
    @PostMapping("/{portalName}/advanced/query")
    public Page<Map<String, Object>> advancedQuery(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.queryPage(req, portalName, getRoleId());
    }

    @ApiOperation("高级查询数据(不分页)")
    @PostMapping("/{portalName}/advanced/select")
    public List<Map<String, Object>> advancedSelect(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.queryList(req, portalName, getRoleId());
    }

    @ApiOperation("个数统计")
    @PostMapping("/{portalName}/general/count")
    public Long generalCount(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.count(advReq, portalName, getRoleId());
    }

    @ApiOperation("统计个数")
    @PostMapping("/{portalName}/advanced/count")
    public Long advancedCount(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.count(req, portalName, getRoleId());
    }

    // ==================== DML接口 (仅Matrix支持) ====================

    @ApiOperation("添加数据")
    @PostMapping("/{portalName}/insert")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@PathVariable String portalName, @RequestBody Map<String, Object> data) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.insert(data, portalName, getRoleId());
        Resp.notice("新增成功");
    }

    @ApiOperation("更新数据")
    @PostMapping("/{portalName}/update")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@PathVariable String portalName, @RequestBody Map<String, Object> data) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.update(data, portalName, getRoleId());
        Resp.notice("更新成功");
    }

    @ApiOperation("更新数据列表")
    @PostMapping("/{portalName}/update/list")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void updateList(@PathVariable String portalName, @RequestBody List<Map<String, Object>> dataList) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.batchUpdate(dataList, portalName, getRoleId());
        Resp.notice("更新成功");
    }

    @ApiOperation("删除数据")
    @PostMapping("/{portalName}/delete")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void delete(@PathVariable String portalName, @RequestBody IdReqVO vo) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.delete(vo.getId(), portalName, getRoleId());
        Resp.notice("删除成功");
    }

    @ApiOperation("删除数据列表")
    @PostMapping("/{portalName}/delete/list")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void deleteList(@PathVariable String portalName, @RequestBody List<String> idList) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        if (FuncUtil.isNotEmpty(idList)) {
            List<Object> objectIds = new ArrayList<>(idList);
            driver.batchDelete(objectIds, portalName, getRoleId());
        }
        Resp.notice("删除列表成功");
    }

    // ==================== 树形结构接口 ====================

    @ApiOperation("获取树形数据")
    @GetMapping("/{portalName}/tree/data")
    public List<TreeDataResVO> getTreeData(@PathVariable String portalName) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        List<Map<String, Object>> allData = driver.getAllData(portalName, getRoleId());

        SysPortal portal = sysPortalService.getByName(portalName, null);
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();

        List<TreeDataItemVO> list = new ArrayList<>();
        if (FuncUtil.isNotEmpty(allData)) {
            for (Map<String, Object> data : allData) {
                Object id = data.get(idColumn);
                Object pid = data.get(pidColumn);
                String name = String.valueOf(data.get(nameColumn));
                list.add(new TreeDataItemVO(id, pid, name));
            }
        }

        return ReflectionUtil.buildTree(TreeDataResVO::setChildren, list,
                TreeDataItemVO::getId, TreeDataItemVO::getPid, null);
    }

    @ApiOperation("高级查询树形数据")
    @PostMapping("/{portalName}/advanced/tree/data")
    public List<TreeDataResVO> getTreeDataAdvanced(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        req.setCurrentPage(1L);
        req.setPageSize(60000L);

        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        Page<Map<String, Object>> page = driver.queryPage(req, portalName, getRoleId());

        SysPortal portal = sysPortalService.getByName(portalName, null);
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();

        List<TreeDataItemVO> list = new ArrayList<>();
        if (FuncUtil.isNotEmpty(page.getRecords())) {
            for (Map<String, Object> data : page.getRecords()) {
                Object id = data.get(idColumn);
                Object pid = data.get(pidColumn);
                String name = String.valueOf(data.get(nameColumn));
                list.add(new TreeDataItemVO(id, pid, name));
            }
        }

        return ReflectionUtil.buildTree(TreeDataResVO::setChildren, list,
                TreeDataItemVO::getId, TreeDataItemVO::getPid, null);
    }

    @ApiOperation("获取父节点")
    @GetMapping("/{portalName}/tree/parent")
    public TreeDataItemVO getParent(@PathVariable String portalName, IdReqVO req) {
        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq queryReq = new AdvancedQueryReq();
        // TODO: 添加id查询条件
        Map<String, Object> self = driver.queryOne(queryReq, portalName, getRoleId());

        if (FuncUtil.isEmpty(self)) {
            return null;
        }

        SysPortal portal = sysPortalService.getByName(portalName, null);
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();

        Object pid = self.get(pidColumn);
        if (FuncUtil.isEmpty(pid)) {
            return null;
        }

        // TODO: 添加pid查询条件
        Map<String, Object> parent = driver.queryOne(queryReq, portalName, getRoleId());
        if (FuncUtil.isNotEmpty(parent)) {
            return new TreeDataItemVO(parent.get(idColumn), parent.get(pidColumn),
                    String.valueOf(parent.get(nameColumn)));
        }

        return null;
    }

    @ApiOperation("获取子节点")
    @GetMapping("/{portalName}/tree/children")
    public List<TreeDataItemVO> getChildren(@PathVariable String portalName, IdReqVO req) {
        SysPortal portal = sysPortalService.getByName(portalName, null);
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();

        AdvancedQueryReq queryReq = new AdvancedQueryReq();
        // TODO: 设置pid条件和排序

        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        List<Map<String, Object>> children = driver.queryList(queryReq, portalName, getRoleId());

        List<TreeDataItemVO> res = new ArrayList<>();
        if (FuncUtil.isNotEmpty(children)) {
            for (Map<String, Object> child : children) {
                res.add(new TreeDataItemVO(child.get(idColumn), child.get(pidColumn),
                        String.valueOf(child.get(nameColumn))));
            }
        }
        return res;
    }

    @ApiOperation("获取兄弟节点")
    @GetMapping("/{portalName}/tree/brothers")
    public List<TreeDataItemVO> getBrothers(@PathVariable String portalName, IdReqVO req) {
        TreeDataItemVO parent = getParent(portalName, req);
        if (FuncUtil.isNotEmpty(parent)) {
            return getChildren(portalName, new IdReqVO(parent.getId().toString()));
        }
        return new ArrayList<>();
    }

    @ApiOperation("变更父节点")
    @PostMapping("/{portalName}/pid")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void updatePid(@PathVariable String portalName, @RequestBody IdPidReqVO req) {
        SysPortal portal = sysPortalService.getByName(portalName, null);
        String pidColumn = portal.getPidColumn();

        Map<String, Object> updateData = new HashMap<>();
        updateData.put(portal.getIdColumn(), req.getId());
        updateData.put(pidColumn, req.getPid());

        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.update(updateData, portalName, getRoleId());
        Resp.notice("变更父节点成功");
    }

    // ==================== 排序接口 ====================

    @ApiOperation("变更顺序")
    @PostMapping("/{portalName}/order/update")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void updateOrder(@PathVariable String portalName, @RequestBody List<IdOrderReqVO> idOrderReqVOList) {
        if (FuncUtil.isEmpty(idOrderReqVOList)) {
            return;
        }

        SysPortal portal = sysPortalService.getByName(portalName, null);
        String idColumn = portal.getIdColumn();
        String orderColumn = portal.getOrderColumn();

        List<Map<String, Object>> updateList = new ArrayList<>();
        for (IdOrderReqVO req : idOrderReqVOList) {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put(idColumn, req.getId());
            updateData.put(orderColumn, req.getShowOrder());
            updateList.add(updateData);
        }

        PortalDataDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.batchUpdate(updateList, portalName, getRoleId());
        Resp.notice("变更顺序成功");
    }

    // ==================== 汇总统计接口 ====================

    @ApiOperation("汇总")
    @PostMapping("/{portalName}/general/summary")
    public Map<String, Object> generalSummary(@PathVariable String portalName, @RequestBody GeneralSummaryReq req) {
        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "暂不支持汇总功能");
    }

    @ApiOperation("汇总")
    @PostMapping("/{portalName}/advanced/summary")
    public Map<String, Object> advancedSummary(@PathVariable String portalName, @RequestBody GeneralSummaryReq req) {
        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "暂不支持汇总功能");
    }

    @ApiOperation("指标统计")
    @PostMapping("/{portalName}/general/statistic")
    public List<StatisticRes> generalStatistic(@PathVariable String portalName, @RequestBody GeneralStatisticReq req) {
        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "暂不支持统计功能");
    }

    @ApiOperation("指标统计")
    @PostMapping("/{portalName}/advanced/statistic")
    public List<StatisticRes> advancedStatistic(@PathVariable String portalName, @RequestBody GeneralStatisticReq req) {
        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "暂不支持统计功能");
    }

    // ==================== 辅助方法 ====================

    private AdvancedQueryReq convertToAdvancedReq(QueryConditionReq req) {
        AdvancedQueryReq advReq = new AdvancedQueryReq();
        advReq.setCurrentPage(req.getCurrentPage());
        advReq.setPageSize(req.getPageSize());
        // TODO: 转换其他查询条件
        return advReq;
    }
}
