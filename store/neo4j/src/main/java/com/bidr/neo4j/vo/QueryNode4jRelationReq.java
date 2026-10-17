package com.bidr.neo4j.vo;

import com.bidr.kernel.vo.portal.QueryConditionReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryNode4jRelationReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 08:52
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryNode4jRelationReq extends QueryConditionReq {
    @ApiModelProperty("关系类型")
    private String relationship;
    @ApiModelProperty("关系类型配置id")
    private Long relationshipId;
}
