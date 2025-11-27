package com.bidr.forge.controller.dynamic;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.forge.engine.driver.DatasetDriver;
import com.bidr.forge.engine.driver.MatrixDriver;
import com.bidr.forge.engine.driver.PortalDriver;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 动态Portal查询Controller（第1层 - 只读层）
 * <p>只提供查询和统计功能，不提供增删改功能</p>
 * 
 * <h3>Controller层次：</h3>
 * <pre>
 * DynamicQueryController（第1层 - 只读）⬅ 当前类
 *   └── DynamicCrudController（第2层 - 增删改）
 *         └── DynamicTreeController（第3层 - 树形结构）
 * </pre>
 * 
 * <h3>提供能力：</h3>
 * <ul>
 *   <li>查询：queryById、generalQuery、generalSelect、advancedQuery、advancedSelect</li>
 *   <li>统计：generalCount、advancedCount、generalSummary、advancedSummary、generalStatistic、advancedStatistic</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>Dataset数据源（只读）</li>
 *   <li>只读数据展示</li>
 *   <li>报表查询</li>
 * </ul>
 *
 * @author Sharp
 * @since 2025-11-27
 */
@Slf4j
@Api(tags = "动态Portal查询接口")
public class DynamicQueryController extends DynamicBaseController {

    @ApiOperation("根据id获取详情")
    @GetMapping("/{portalName}/id")
    public Map<String, Object> queryById(@PathVariable String portalName, IdReqVO req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.selectById(req.getId(), portalName, getRoleId());
    }

    @ApiOperation("通用查询数据")
    @PostMapping("/{portalName}/general/query")
    public Page<Map<String, Object>> generalQuery(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.queryPage(advReq, portalName, getRoleId());
    }

    @ApiOperation("通用查询数据(不分页)")
    @PostMapping("/{portalName}/general/select")
    public List<Map<String, Object>> generalSelect(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.queryList(advReq, portalName, getRoleId());
    }

    @ApiOperation("高级查询数据")
    @PostMapping("/{portalName}/advanced/query")
    public Page<Map<String, Object>> advancedQuery(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.queryPage(req, portalName, getRoleId());
    }

    @ApiOperation("高级查询数据(不分页)")
    @PostMapping("/{portalName}/advanced/select")
    public List<Map<String, Object>> advancedSelect(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.queryList(req, portalName, getRoleId());
    }

    // ==================== 统计接口 ====================

    @ApiOperation("个数统计")
    @PostMapping("/{portalName}/general/count")
    public Long generalCount(@PathVariable String portalName, @RequestBody QueryConditionReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedQueryReq advReq = convertToAdvancedReq(req);
        return driver.count(advReq, portalName, getRoleId());
    }

    @ApiOperation("统计个数")
    @PostMapping("/{portalName}/advanced/count")
    public Long advancedCount(@PathVariable String portalName, @RequestBody AdvancedQueryReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.count(req, portalName, getRoleId());
    }

    @ApiOperation("汇总")
    @PostMapping("/{portalName}/general/summary")
    public Map<String, Object> generalSummary(@PathVariable String portalName, @RequestBody GeneralSummaryReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedSummaryReq advReq = convertToAdvancedReq(req);
        return driver.summary(advReq, portalName, getRoleId());
    }

    @ApiOperation("汇总")
    @PostMapping("/{portalName}/advanced/summary")
    public Map<String, Object> advancedSummary(@PathVariable String portalName, @RequestBody AdvancedSummaryReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.summary(req, portalName, getRoleId());
    }

    @ApiOperation("指标统计")
    @PostMapping("/{portalName}/general/statistic")
    public List<StatisticRes> generalStatistic(@PathVariable String portalName, @RequestBody GeneralStatisticReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        AdvancedStatisticReq advReq = convertToAdvancedReq(req);
        return driver.statistic(advReq, portalName, getRoleId());
    }

    @ApiOperation("指标统计")
    @PostMapping("/{portalName}/advanced/statistic")
    public List<StatisticRes> advancedStatistic(@PathVariable String portalName, @RequestBody AdvancedStatisticReq req) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        return driver.statistic(req, portalName, getRoleId());
    }
}
