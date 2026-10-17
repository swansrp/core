package com.bidr.forge.engine.driver.inf;

import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.List;

/**
 * Driver树形结构基础接口
 * <p>定义树形结构的基础操作方法</p>
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverTreeBaseInf {
    
    /**
     * 获取完整树形数据
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 树形结构数据
     */
    List<TreeDataResVO> getTreeData(String portalName, Long roleId);

    /**
     * 根据高级查询条件获取树形数据
     *
     * @param req        高级查询请求
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 树形结构数据
     */
    List<TreeDataResVO> getAdvancedTreeData(AdvancedQueryReq req, String portalName, Long roleId);

    /**
     * 获取父节点
     *
     * @param req        包含当前节点ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 父节点信息
     */
    TreeDataItemVO getParent(IdReqVO req, String portalName, Long roleId);

    /**
     * 获取子节点列表
     *
     * @param req        包含父节点ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 子节点列表
     */
    List<TreeDataItemVO> getChildren(IdReqVO req, String portalName, Long roleId);

    /**
     * 获取兄弟节点列表
     *
     * @param req        包含当前节点ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 兄弟节点列表（包含自己）
     */
    List<TreeDataItemVO> getBrothers(IdReqVO req, String portalName, Long roleId);

    /**
     * 变更父节点
     *
     * @param req        包含节点ID和新的父节点ID
     * @param portalName Portal名称
     * @param roleId     角色ID
     */
    void updatePid(IdPidReqVO req, String portalName, Long roleId);
}
