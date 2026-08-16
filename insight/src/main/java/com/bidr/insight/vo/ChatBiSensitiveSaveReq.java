package com.bidr.insight.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Title: ChatBiSensitiveSaveReq
 * Description: 敏感列整板覆盖保存请求——columns 为空即清空该板敏感配置
 *
 * @author Sharp
 * @since 2026/8/16
 */
@ApiModel(description = "敏感列保存请求")
@Data
public class ChatBiSensitiveSaveReq {

    @ApiModelProperty(value = "看板名（sys_portal.name，即语义目录 tableId）")
    @NotBlank(message = "看板名不能为空")
    private String tableId;

    @ApiModelProperty(value = "敏感列清单（property 必填，replaceProperty 可空）")
    @Valid
    @Size(max = 200, message = "敏感列数量超限")
    private List<Column> columns;

    /**
     * 敏感列条目
     */
    @ApiModel(description = "敏感列条目")
    @Data
    public static class Column {

        @ApiModelProperty(value = "敏感列属性名")
        @NotBlank(message = "敏感列属性名不能为空")
        private String property;

        @ApiModelProperty(value = "配对替换列属性名（可空，如 项目名称→项目编号）")
        private String replaceProperty;
    }
}
