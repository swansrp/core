package com.bidr.neo4j.vo.advanced;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: QueryNode4jAdvanced
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/16 10:17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryNode4jAdvanced extends AdvancedQueryReq {
    @ApiModelProperty("类型(0 节点 1 关系)")
    private String type;
    @ApiModelProperty("配置id")
    private Long configId;
    @ApiModelProperty("id")
    private Long id;
    @ApiModelProperty("节点/关系名(单体)")
    private String label;
    @ApiModelProperty("节点/关系名(列表)")
    private List<String> labelList = new ArrayList<>();
    @ApiModelProperty("关系方向 0 正向 1 逆向 默认正向")
    private String direction;

}
