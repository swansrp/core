package com.bidr.platform.constant.dict;

import com.bidr.kernel.constant.dict.MetaDictName;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.bo.tree.TreeDict;

import java.util.List;

/**
 * Title: IDynamicTree
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/04 11:40
 */
public interface IDynamicTree extends MetaDictName {
    List<TreeDict> generate(String treeType, String treeTitle);

    /**
     * 生成字典树条目
     *
     * @param id        id
     * @param pid       pid
     * @param name      名称
     * @param order     顺序
     * @param treeType  树类型
     * @param treeTitle 树标题
     * @return 树节点
     */
    default TreeDict buildTreeDict(Object id, Object pid, String name, Integer order, String treeType,
                                   String treeTitle) {
        TreeDict dict = new TreeDict();
        dict.setId(id);
        dict.setPid(pid);
        dict.setKey(id);
        dict.setValue(id);
        dict.setLabel(name);
        dict.setOrder(order);
        dict.setTreeType(treeType);
        dict.setTreeTitle(treeTitle);
        return dict;
    }

    /**
     * 生成树
     *
     * @param items 树节点列表
     * @return 树
     */
    default List<TreeDict> buildTree(List<TreeDict> items) {
        return ReflectionUtil.buildTree(TreeDict::setChildren, items, TreeDict::getId, TreeDict::getPid);
    }
}
