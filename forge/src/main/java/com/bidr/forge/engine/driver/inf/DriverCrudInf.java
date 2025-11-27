package com.bidr.forge.engine.driver.inf;

import com.bidr.forge.engine.driver.base.DriverDeleteInf;
import com.bidr.forge.engine.driver.base.DriverInsertInf;
import com.bidr.forge.engine.driver.base.DriverUpdateInf;

/**
 * Driver增删改查接口（第2层 - CRUD层）
 * <p>继承查询接口，增加增删改能力</p>
 * 
 * <h3>接口层次：</h3>
 * <pre>
 * DriverQueryOnlyInf（第1层 - 只读）
 *   └── DriverCrudInf（第2层 - 增删改）⬅ 当前接口
 *         └── DriverTreeInf（第3层 - 树形结构）
 * </pre>
 * 
 * <h3>能力说明：</h3>
 * <ul>
 *   <li>继承自DriverQueryOnlyInf：所有查询和统计功能</li>
 *   <li>新增能力：
 *     <ul>
 *       <li>插入：insert、batchInsert</li>
 *       <li>更新：update、batchUpdate</li>
 *       <li>删除：delete、batchDelete</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>Matrix数据源（单表CRUD）</li>
 *   <li>标准的数据管理（无树形结构）</li>
 *   <li>列表型数据维护</li>
 * </ul>
 *
 * @param <VO> 返回的VO类型
 * @author Sharp
 * @since 2025-11-27
 */
public interface DriverCrudInf<VO> extends 
        DriverQueryOnlyInf<VO>,
        DriverInsertInf<VO>,
        DriverUpdateInf<VO>,
        DriverDeleteInf<VO> {
    // 第2层：在查询基础上增加增删改能力
    // Matrix驱动（无树形结构）实现此接口
}
