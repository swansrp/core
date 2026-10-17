package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Title: ChatBiTableDescReq
 * Description: 看板业务描述保存请求（描述空白视为清除）
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Data
public class ChatBiTableDescReq {

    @ApiModelProperty(value = "表格code（sys_portal_table.table_code）")
    @NotBlank(message = "tableId不能为空")
    private String tableId;

    @ApiModelProperty(value = "业务描述（供大模型路由选择看板）")
    private String description;
}
