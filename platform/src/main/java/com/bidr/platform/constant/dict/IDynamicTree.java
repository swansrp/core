package com.bidr.platform.constant.dict;

import com.bidr.platform.bo.tree.TreeDict;

import java.util.List;

/**
 * Title: IDynamicTree
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/04 11:40
 */
public interface IDynamicTree {
    List<TreeDict> generate(String treeType, String treeTitle);
}
