package com.bidr.kernel.vo.portal;

import lombok.Data;

import java.util.List;

/**
 * Title: SortVO
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:42
 */
@Data
public class SortVO {
    private List<String> property;
    private Integer type;
}
