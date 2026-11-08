package com.bidr.feishu.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: FeishuParam
 * Description: 飞书相关系统参数（自动注册至 sys_config）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@AllArgsConstructor
@Getter
@MetaParam
public enum FeishuParam implements Param {
    /**
     * 异常通知飞书接收人
     */
    EXCEPTION_NOTIFY_FEISHU("异常通知飞书接收人", "", "接收异常通知的飞书 open_id，多个接收人用,分隔"),

    /**
     * 异常通知飞书标题
     */
    EXCEPTION_NOTIFY_FEISHU_SUBJECT("异常通知飞书标题", "【系统异常通知】", "异常通知飞书的标题"),

    /**
     * 异常通知飞书开关
     */
    EXCEPTION_NOTIFY_FEISHU_ENABLED("异常通知飞书开关", "0", "是否开启异常通知飞书功能"),

    /**
     * 异常通知飞书堆栈深度
     */
    EXCEPTION_NOTIFY_FEISHU_STACK_DEPTH("异常通知飞书堆栈深度", "50", "异常通知飞书中堆栈信息的最大行数，-1表示不限制"),

    /**
     * 异常通知飞书最低级别
     */
    EXCEPTION_NOTIFY_FEISHU_MIN_LEVEL("异常通知飞书最低级别", "ERROR", "异常通知飞书的最低错误级别，只有达到或超过此级别的异常才会发送飞书通知。可选值：FATAL、ERROR、WARN、INFO、DEBUG、TRACE");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
