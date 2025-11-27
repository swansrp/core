package com.bidr.forge.controller.dynamic;

import com.bidr.forge.engine.driver.PortalDriver;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 动态Portal增删改Controller（第2层 - CRUD层）
 * <p>继承查询Controller，增加增删改功能</p>
 * 
 * <h3>Controller层次：</h3>
 * <pre>
 * DynamicQueryController（第1层 - 只读）
 *   └── DynamicCrudController（第2层 - 增删改）⬅ 当前类
 *         └── DynamicTreeController（第3层 - 树形结构）
 * </pre>
 * 
 * <h3>提供能力：</h3>
 * <ul>
 *   <li>继承自DynamicPortalQueryController：所有查询和统计功能</li>
 *   <li>新增：insert、update、batchUpdate</li>
 *   <li>新增：delete、batchDelete</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>Matrix数据源（标准CRUD）</li>
 *   <li>标准的数据管理（无树形结构）</li>
 * </ul>
 *
 * @author Sharp
 * @since 2025-11-27
 */
@Slf4j
@Api(tags = "动态Portal增删改接口")
public class DynamicCrudController extends DynamicQueryController {

    // ==================== DML接口 (仅Matrix支持) ====================

    @ApiOperation("添加数据")
    @PostMapping("/{portalName}/insert")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void add(@PathVariable String portalName, @RequestBody Map<String, Object> data) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.insert(data, portalName, getRoleId());
        Resp.notice("新增成功");
    }

    @ApiOperation("更新数据")
    @PostMapping("/{portalName}/update")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void update(@PathVariable String portalName, @RequestBody Map<String, Object> data) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.update(data, portalName, getRoleId());
        Resp.notice("更新成功");
    }

    @ApiOperation("更新数据列表")
    @PostMapping("/{portalName}/update/list")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void updateList(@PathVariable String portalName, @RequestBody List<Map<String, Object>> dataList) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.batchUpdate(dataList, portalName, getRoleId());
        Resp.notice("更新成功");
    }

    @ApiOperation("删除数据")
    @PostMapping("/{portalName}/delete")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void delete(@PathVariable String portalName, @RequestBody IdReqVO vo) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.delete(vo.getId(), portalName, getRoleId());
        Resp.notice("删除成功");
    }

    @ApiOperation("删除数据列表")
    @PostMapping("/{portalName}/delete/list")
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void deleteList(@PathVariable String portalName, @RequestBody List<String> idList) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        if (FuncUtil.isNotEmpty(idList)) {
            List<Object> objectIds = new ArrayList<>(idList);
            driver.batchDelete(objectIds, portalName, getRoleId());
        }
        Resp.notice("删除列表成功");
    }
}
