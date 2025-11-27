package com.bidr.forge.engine.driver.inf;

/**
 * Driver树形结构接口（第3层 - 树形层）
 * <p>继承CRUD接口，增加树形结构能力</p>
 * 
 * <h3>接口层次：</h3>
 * <pre>
 * DriverQueryOnlyInf（第1层 - 只读）
 *   └── DriverCrudInf（第2层 - 增删改）
 *         └── DriverTreeInf（第3层 - 树形结构）⬅ 当前接口
 * </pre>
 * 
 * <h3>能力说明：</h3>
 * <ul>
 *   <li>继承自DriverCrudInf：所有查询、统计、增删改功能</li>
 *   <li>新增能力：
 *     <ul>
 *       <li>树形数据：getTreeData、getAdvancedTreeData</li>
 *       <li>节点关系：getParent、getChildren、getBrothers</li>
 *       <li>节点操作：updatePid（变更父节点）</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>树形菜单管理</li>
 *   <li>组织架构管理</li>
 *   <li>分类目录管理</li>
 *   <li>部门层级管理</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 获取完整树形数据
 * List<TreeDataResVO> tree = driver.getTreeData(portalName, roleId);
 * 
 * // 获取某节点的子节点
 * IdReqVO req = new IdReqVO("parentId");
 * List<TreeDataItemVO> children = driver.getChildren(req, portalName, roleId);
 * 
 * // 变更节点的父节点
 * IdPidReqVO updateReq = new IdPidReqVO("nodeId", "newParentId");
 * driver.updatePid(updateReq, portalName, roleId);
 * }</pre>
 *
 * @param <VO> 返回的VO类型
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverTreeInf<VO> extends 
        DriverCrudInf<VO>,
        DriverTreeBaseInf {
    // 第3层：在CRUD基础上增加树形结构能力
    // Matrix驱动（树形结构）实现此接口
}
