package com.bidr.neo4j.vo;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryNode4jNodeReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 10:29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryNode4jRelationshipReq extends QueryReqVO {
    @ApiModelProperty("起始节点")
    private QueryNode4jNodeReq startNode;
    @ApiModelProperty("结束节点")
    private QueryNode4jNodeReq endNode;
    @ApiModelProperty("关系")
    private QueryNode4jRelationReq relation;
    @ApiModelProperty("层级")
    private Integer level;
}
