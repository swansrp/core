package com.bidr.forge.controller.dynamic;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.forge.engine.driver.PortalDriver;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.SortVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 动态Portal树形Controller（第3层 - 树形层）
 * <p>继承CRUD Controller，增加树形结构和排序功能</p>
 * 
 * <h3>Controller层次：</h3>
 * <pre>
 * DynamicQueryController（第1层 - 只读）
 *   └── DynamicCrudController（第2层 - 增删改）
 *         └── DynamicTreeController（第3层 - 树形结构）⬅ 当前类
 * </pre>
 * 
 * <h3>提供能力：</h3>
 * <ul>
 *   <li>继承自DynamicPortalCrudController：所有查询、统计、增删改功能</li>
 *   <li>新增 - 树形数据：getTreeData、getAdvancedTreeData</li>
 *   <li>新增 - 节点关系：getParent、getChildren、getBrothers</li>
 *   <li>新增 - 节点操作：updatePid（变更父节点）</li>
 *   <li>新增 - 排序功能：updateOrder（变更顺序）</li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>树形菜单管理</li>
 *   <li>组织架构管理</li>
 *   <li>分类目录管理</li>
 * </ul>
 *
 * @author Sharp
 * @since 2025-11-27
 */
@Slf4j
public class DynamicTreeController extends DynamicCrudController {

    // ==================== 树形结构接口 ====================

    @ApiOperation("获取树形数据")
    @GetMapping("/{portalName}/tree/data")
    public List<TreeDataResVO> getTreeData(@PathVariable String portalName) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
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

        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
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
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        SysPortal portal = sysPortalService.getByName(portalName, getRoleId());
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();

        // 查询自身记录
        Map<String, Object> self = driver.selectById(req.getId(), portalName, getRoleId());
        if (FuncUtil.isEmpty(self)) {
            return null;
        }

        // 获取父ID
        Object pid = self.get(pidColumn);
        if (FuncUtil.isEmpty(pid)) {
            return null;
        }

        // 查询父节点
        Map<String, Object> parent = driver.selectById(pid, portalName, getRoleId());
        if (FuncUtil.isNotEmpty(parent)) {
            return new TreeDataItemVO(parent.get(idColumn), parent.get(pidColumn),
                    String.valueOf(parent.get(nameColumn)));
        }

        return null;
    }

    @ApiOperation("获取子节点")
    @GetMapping("/{portalName}/tree/children")
    public List<TreeDataItemVO> getChildren(@PathVariable String portalName, IdReqVO req) {
        SysPortal portal = sysPortalService.getByName(portalName, getRoleId());
        String idColumn = portal.getIdColumn();
        String pidColumn = portal.getPidColumn();
        String nameColumn = portal.getNameColumn();
        String orderColumn = portal.getOrderColumn();

        // 构建查询条件：pid = req.getId()
        AdvancedQuery condition = new AdvancedQuery();
        condition.addCondition(pidColumn, PortalConditionDict.EQUAL, Collections.singletonList(req.getId()));

        // 构建排序：按 order 字段升序
        List<SortVO> sortList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(orderColumn)) {
            SortVO sort = new SortVO();
            sort.setProperty(orderColumn);
            sort.setType(PortalSortDict.ASC.getValue()); // 0=ASC, 1=DESC
            sortList.add(sort);
        }

        AdvancedQueryReq queryReq = new AdvancedQueryReq(condition, sortList);

        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
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

        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
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

        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        driver.batchUpdate(updateList, portalName, getRoleId());
        Resp.notice("变更顺序成功");
    }
}
