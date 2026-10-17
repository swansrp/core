package com.bidr.platform.vo.log;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: LogRes
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/14 10:37
 */
@Data
public class LogRes {
    @ApiModelProperty(value = "id")
    private String logId;
    @ApiModelProperty(value = "项目标识id")
    private String projectId;
    @ApiModelProperty(value = "模块id")
    private String moduleId;
    @ApiModelProperty(value = "环境类型")
    private String envType;
    @ApiModelProperty(value = "日志创建时间")
    private String createTime;
    @ApiModelProperty(value = "日志序列号")
    private Long logSeq;
    @ApiModelProperty(value = "日志级别")
    private String logLevel;
    @ApiModelProperty(value = "请求id")
    private String requestId;
    @ApiModelProperty(value = "trace id")
    private String traceId;
    @ApiModelProperty(value = "请求ip")
    private String requestIp;
    @ApiModelProperty(value = "用户ip")
    private String userIp;
    @ApiModelProperty(value = "服务器ip")
    private String serverIp;
    @ApiModelProperty(value = "线程名")
    private String threadName;
    @ApiModelProperty(value = "类名")
    private String className;
    @ApiModelProperty(value = "方法名")
    private String methodName;
    @ApiModelProperty(value = "内容")
    private Object content;
}
