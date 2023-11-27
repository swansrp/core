package com.bidr.neo4j.vo;

import com.bidr.kernel.vo.portal.QueryConditionReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: QueryNode4jNodeReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 10:29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryNode4jNodeReq extends QueryConditionReq {
    @ApiModelProperty("node配置id")
    private Long nodeId;
    @ApiModelProperty("id")
    private Long id;
    @ApiModelProperty("类型(单体)")
    private String label;
    @ApiModelProperty("类型(列表)")
    private List<String> labelList = new ArrayList<>();
}
