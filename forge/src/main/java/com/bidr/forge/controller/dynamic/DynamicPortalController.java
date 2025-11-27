package com.bidr.forge.controller.dynamic;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: DynamicPortalController
 * Description: 动态Portal控制器（统合接口）
 * <p>继承所有层级Controller，提供完整的数据操作能力</p>
 * <p>这是唯一允许使用@PathVariable的Controller，用于通过路径区分不同Portal</p>
 * 
 * <h3>Controller层次：</h3>
 * <pre>
 * DynamicQueryController（第1层 - 只读）
 *   └── DynamicCrudController（第2层 - 增删改）
 *         └── DynamicTreeController（第3层 - 树形结构）
 *               └── DynamicPortalController（统合接口）⬅ 当前类
 * </pre>
 * 
 * <h3>完整能力清单：</h3>
 * <ul>
 *   <li><b>查询能力</b>（来自DynamicPortalQueryController）：
 *     <ul>
 *       <li>queryById - 根据ID查询</li>
 *       <li>generalQuery / generalSelect - 通用查询</li>
 *       <li>advancedQuery / advancedSelect - 高级查询</li>
 *       <li>generalCount / advancedCount - 统计计数</li>
 *       <li>generalSummary / advancedSummary - 汇总统计</li>
 *       <li>generalStatistic / advancedStatistic - 指标统计</li>
 *     </ul>
 *   </li>
 *   <li><b>CRUD能力</b>（来自DynamicPortalCrudController）：
 *     <ul>
 *       <li>add - 新增数据</li>
 *       <li>update / updateList - 更新数据</li>
 *       <li>delete / deleteList - 删除数据</li>
 *     </ul>
 *   </li>
 *   <li><b>树形能力</b>（来自DynamicPortalTreeController）：
 *     <ul>
 *       <li>getTreeData / getTreeDataAdvanced - 获取树形数据</li>
 *       <li>getParent / getChildren / getBrothers - 节点关系</li>
 *       <li>updatePid - 变更父节点</li>
 *       <li>updateOrder - 变更顺序</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>实现说明：</h3>
 * <ul>
 *   <li><b>Matrix驱动</b>：实现所有方法（完整功能）</li>
 *   <li><b>Dataset驱动</b>：只实现查询和统计方法，其他方法抛UnsupportedOperationException</li>
 * </ul>
 * 
 * <h3>选择合适的Controller：</h3>
 * <ul>
 *   <li>只需查询 → 使用 {@link DynamicQueryController}</li>
 *   <li>需要增删改 → 使用 {@link DynamicCrudController}</li>
 *   <li>需要树形结构 → 使用 {@link DynamicTreeController}</li>
 *   <li>需要所有功能 → 使用 {@link DynamicPortalController}</li>
 * </ul>
 * 
 * Copyright: Copyright (c) 2025
 *
 * @author Sharp
 * @since 2025/11/24
 */
@Slf4j
@RestController
@Api(tags = "动态Portal接口")
@RequestMapping("/web/dynamic/portal")
public class DynamicPortalController extends DynamicTreeController {
    // 统合所有Portal能力（查询 + 统计 + CRUD + 树形 + 排序）
    // 继承自 DynamicTreeController，自动获得所有层级的能力
}
