package com.bidr.platform.bo.tree;

import lombok.Data;

/**
 * Title: TreeDictItem
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/04 11:42
 */
@Data
public class TreeDictItem {
    private Object id;
    private Object pid;
    private String treeType;
    private String treeTitle;
    private Object value;
    private String label;
    private Integer order;
}
