package com.bidr.email.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Title: SendEmailReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/08/15 08:54
 */
@Data
public class SendEmailReq {
    @NotNull(message = "收件人不能为空")
    @ApiModelProperty("收件人")
    private String to;
    @ApiModelProperty("抄送人列表")
    private List<String> cc;
    @NotNull(message = "主题不能为空")
    @ApiModelProperty("主题")
    private String subject;
    @ApiModelProperty("内容")
    private String content;
    @ApiModelProperty("附件名称")
    private String attachmentName;
    @ApiModelProperty("附件路径(本地路径或网络地址)")
    private String filePath;
}
