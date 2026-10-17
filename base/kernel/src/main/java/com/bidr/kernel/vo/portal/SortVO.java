package com.bidr.kernel.vo.portal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: SortVO
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:42
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SortVO {
    private String property;
    private Integer type;
}
