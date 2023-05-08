package com.bidr.platform.bo.tree;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: TreeDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/04 11:42
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TreeDict extends TreeDictItem {
    private List<TreeDict> children;
}
