package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatBiRouteItem
 * Description: 看板路由目录条目——业务描述非空即注册进全局路由候选（白名单），
 * 描述本身就是路由大模型的判断依据
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Data
public class ChatBiRouteItem {

    @ApiModelProperty(value = "看板标识（sys_portal.name，即图表指标挂载与表格渲染的 tableId）")
    private String tableId;

    @ApiModelProperty(value = "看板名（sys_portal.name，与 tableId 同值）")
    private String portalName;

    @ApiModelProperty(value = "看板中文名（sys_portal.display_name）")
    private String title;

    @ApiModelProperty(value = "业务描述（insight_chatbi_table_desc，供大模型判断看板与问题的相关性）")
    private String description;
}
