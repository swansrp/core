package com.bidr.kernel.vo.portal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ConditionVO
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/05 15:40
 */
@Data
public class ConditionVO {
    private String property;
    private String value;
    private Integer relation;
    private Boolean status;
    private List<String> valueList = new ArrayList<>();
}
