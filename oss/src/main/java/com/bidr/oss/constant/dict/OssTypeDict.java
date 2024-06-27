package com.bidr.oss.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: OssTypeDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 23:12
 */

@MetaDict(value = "OSS_TYPE_DICT", remark = "对象存储文件类型字典")
@Getter
@RequiredArgsConstructor
public enum OssTypeDict implements Dict {
    /**
     * 对象存储类型
     */
    DOC("1", "文档"),
    IMG("2", "图片"),
    VIDEO("3", "视频"),
    APK("4", "应用安装文件"),
    AUDIO("5", "音频"),
    TEXT("6", "文字"),
    FILE("7", "文件"),
    WORD("8", "word"),
    EXCEL("9", "excel"),
    PPT("10", "ppt"),
    PDF("11", "pdf"),
    LOG("12", "日志"),
    OTHER("13", "其他");

    private final String value;
    private final String label;
}
