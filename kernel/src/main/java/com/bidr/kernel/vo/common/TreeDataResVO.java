package com.bidr.kernel.vo.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: TreeDataResVO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/10 09:35
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TreeDataResVO extends TreeDataItemVO {
    private List<TreeDataResVO> children;
}
