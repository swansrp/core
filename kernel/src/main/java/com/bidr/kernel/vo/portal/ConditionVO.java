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

    public <T, R> ConditionVO(GetFunc<T, R> field, Object obj) {
        this.property = LambdaUtil.getFieldNameByGetFunc(field);
        this.relation = PortalConditionDict.EQUAL.getValue();
        this.value = Collections.singletonList(obj);
    }

    public ConditionVO(String field, Object obj) {
        this.property = field;
        this.relation = PortalConditionDict.EQUAL.getValue();
        this.value = Collections.singletonList(obj);
    }

    public ConditionVO(String property, Integer relation, List<?> value) {
        this.property = property;
        this.relation = relation;
        this.value = value;
    }
}
