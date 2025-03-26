package com.bidr.platform.constant.dict.log;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: LogLevelDict
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/11 10:13
 */
@RequiredArgsConstructor
@Getter
@MetaDict(value = "LOG_LEVEL_DICT", remark = "日志等级字典")
public enum LogLevelDict implements Dict {
    /**
     * 日志等级字典
     */
    TRACE("TRACE", "TRACE"),
    DEBUG("DEBUG", "DEBUG"),
    INFO("INFO", "INFO"),
    WARNING("WARN", "WARN"),
    ERROR("ERROR", "ERROR"),
    FATAL("FATAL", "FATAL");

    private final String value;
    private final String label;
}
