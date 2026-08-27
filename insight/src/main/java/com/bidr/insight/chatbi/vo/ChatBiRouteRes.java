package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatBiRouteRes
 * Description: 看板路由结果——选中的 tableId，前端据此进入既定单看板问答流程
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Data
public class ChatBiRouteRes {

    @ApiModelProperty(value = "选中的表格code")
    private String tableId;

    @ApiModelProperty(value = "表格配置名称（portalName）")
    private String portalName;
}
