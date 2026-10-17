package com.bidr.platform.vo.log;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;

/**
 * Title: LogReq
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/14 10:37
 */
@Data
public class LogReq {
    @NotBlank(message = "模块id不能为空")
    @ApiModelProperty("模块id")
    private List<String> moduleId;
    @NotBlank(message = "环境类型不能为空")
    @ApiModelProperty("环境类型")
    private String envType;
    @ApiModelProperty("日志等级")
    private List<String> logLevel;
    @ApiModelProperty("请求id")
    private String requestId;
    @ApiModelProperty("跟踪id")
    private String traceId;
    @ApiModelProperty("请求ip")
    private String requestIP;
    @ApiModelProperty("用户ip")
    private String userIP;
    @ApiModelProperty("服务ip")
    private String serverIP;
    @ApiModelProperty("现成名称")
    private String threadName;
    @ApiModelProperty("内容")
    private String content;

    @ApiModelProperty("时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date startAt;

    @ApiModelProperty("时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date endAt;

    @ApiModelProperty("排除日志")
    private List<String> blockMessage;
}
