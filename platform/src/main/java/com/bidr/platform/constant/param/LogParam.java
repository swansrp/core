package com.bidr.platform.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: LogParam
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/27 9:32
 */

@Getter
@MetaParam
@AllArgsConstructor
public enum LogParam implements Param {
    /**
     *
     */
    DB_LOG_EXPIRED("日志保存周期(天)", "30", "每天定时清除多少天前的日志");

    private final String title;
    private final String defaultValue;
    private final String remark;
}