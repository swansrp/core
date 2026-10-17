package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@ApiModel(description = "sys_log")
@Data
@TableName(value = "sys_log")
public class SysLog {
    /**
     * 日志id
     */
    @TableId(value = "log_id", type = IdType.AUTO)
    @ApiModelProperty(value = "日志id")
    @NotNull(message = "日志id不能为null")
    private Long logId;

    /**
     * 项目标识id
     */
    @TableField(value = "project_id")
    @ApiModelProperty(value = "项目标识id")
    @Size(max = 100, message = "项目标识id最大长度要小于 100")
    private String projectId;

    /**
     * 模块id
     */
    @TableField(value = "module_id")
    @ApiModelProperty(value = "模块id")
    @Size(max = 100, message = "模块id最大长度要小于 100")
    private String moduleId;

    /**
     * 环境类型
     */
    @TableField(value = "env_type")
    @ApiModelProperty(value = "环境类型")
    @Size(max = 100, message = "环境类型最大长度要小于 100")
    private String envType;

    /**
     * 日志创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "日志创建时间")
    private Date createTime;

    /**
     * 日志序列号
     */
    @TableField(value = "log_seq")
    @ApiModelProperty(value = "日志序列号")
    private Long logSeq;

    /**
     * 日志级别
     */
    @TableField(value = "log_level")
    @ApiModelProperty(value = "日志级别")
    @Size(max = 20, message = "日志级别最大长度要小于 20")
    private String logLevel;

    /**
     * 请求id
     */
    @TableField(value = "request_id")
    @ApiModelProperty(value = "请求id")
    @Size(max = 100, message = "请求id最大长度要小于 100")
    private String requestId;

    /**
     * trace id
     */
    @TableField(value = "trace_id")
    @ApiModelProperty(value = "trace id")
    @Size(max = 100, message = "trace id最大长度要小于 100")
    private String traceId;

    /**
     * 请求ip
     */
    @TableField(value = "request_ip")
    @ApiModelProperty(value = "请求ip")
    @Size(max = 100, message = "请求ip最大长度要小于 100")
    private String requestIp;

    /**
     * 用户ip
     */
    @TableField(value = "user_ip")
    @ApiModelProperty(value = "用户ip")
    @Size(max = 100, message = "用户ip最大长度要小于 100")
    private String userIp;

    /**
     * 服务器ip
     */
    @TableField(value = "server_ip")
    @ApiModelProperty(value = "服务器ip")
    @Size(max = 100, message = "服务器ip最大长度要小于 100")
    private String serverIp;

    /**
     * 线程名
     */
    @TableField(value = "thread_name")
    @ApiModelProperty(value = "线程名")
    @Size(max = 100, message = "线程名最大长度要小于 100")
    private String threadName;

    /**
     * 类名
     */
    @TableField(value = "class_name")
    @ApiModelProperty(value = "类名")
    @Size(max = 200, message = "类名最大长度要小于 200")
    private String className;

    /**
     * 方法名
     */
    @TableField(value = "method_name")
    @ApiModelProperty(value = "方法名")
    @Size(max = 200, message = "方法名最大长度要小于 200")
    private String methodName;

    /**
     * 内容
     */
    @TableField(value = "content")
    @ApiModelProperty(value = "内容")
    private String content;
}