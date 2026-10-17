package com.bidr.neo4j.vo.advanced;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Title: QueryNode4jNodeAdvancedReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/16 09:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryNode4jAdvancedReq extends QueryReqVO {
    @NotEmpty(message = "关系查询条件不能为空")
    @ApiModelProperty("条件列表")
    private List<QueryNode4jAdvanced> conditions;
}
