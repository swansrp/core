package com.bidr.neo4j.vo.configuration;

import com.bidr.kernel.vo.portal.ConditionVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: NodeReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 09:10
 */
@Data
public class NodeReq {
    @ApiModelProperty("条件列表")
    private List<ConditionVO> conditionList;
}
