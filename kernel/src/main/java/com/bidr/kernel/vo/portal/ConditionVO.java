package com.bidr.kernel.vo.portal;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.LambdaUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Title: ConditionVO
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:40
 */
@Data
public class ConditionVO {
    @ApiModelProperty("字段名")
    private String property;
    @ApiModelProperty("查询值")
    private List<?> value;
    @ApiModelProperty("查询关系")
    private Integer relation;
    @ApiModelProperty("日期格式")
    private String dateFormat;

    public ConditionVO() {
        value = new ArrayList<>();
    }

    public ConditionVO(GetFunc field, Object obj) {
        property = LambdaUtil.getFieldNameByGetFunc(field);
        relation = PortalConditionDict.EQUAL.getValue();
        value = Collections.singletonList(obj);
    }

    public ConditionVO(String field, Object obj) {
        property = field;
        relation = PortalConditionDict.EQUAL.getValue();
        value = Collections.singletonList(obj);
    }
}
