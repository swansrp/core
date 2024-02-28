package com.bidr.oss.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
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
    DOC("1", "文档", PortalFieldDict.FILE),
    IMG("2", "图片", PortalFieldDict.IMAGE),
    VIDEO("3", "视频", PortalFieldDict.VIDEO),
    APK("4", "应用安装文件", PortalFieldDict.FILE),
    AUDIO("5", "音频", PortalFieldDict.AUDIO),
    TEXT("6", "文字", PortalFieldDict.FILE),
    FILE("7", "文件", PortalFieldDict.FILE),
    WORD("8", "word", PortalFieldDict.FILE),
    EXCEL("9", "excel", PortalFieldDict.FILE),
    PPT("10", "ppt", PortalFieldDict.FILE),
    PDF("11", "pdf", PortalFieldDict.FILE),
    LOG("12", "日志", PortalFieldDict.FILE),
    OTHER("13", "其他", PortalFieldDict.FILE);

    private final String value;
    private final String label;
    private final PortalFieldDict portalField;
}
