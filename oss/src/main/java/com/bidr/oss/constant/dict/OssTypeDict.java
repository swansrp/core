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
    DOC("1", "文档", new String[]{".sh", ".sql", ".vue", ".java", ".ts", ".js"}),
    IMG("2", "图片", new String[]{".jpg",".png",".bmp",".gif",".jpeg",".webp",".svg",".tiff"}),
    VIDEO("3", "视频", new String[]{".mp4",".mkv",".wmv",".avi"}),
    APK("4", "应用安装文件",  new String[]{".apk"}),
    AUDIO("5", "音频", new String[]{".m4a", ".mp3", ".wav", ".wma", ".m3u", ".flac", ".aac", ".ape", ".ogg", ".amr"}),
    TEXT("6", "文字", new String[]{".txt"}),
    FILE("7", "文件", new String[]{".zip", ".rar", ".7z"}),
    WORD("8", "word", new String[]{".doc", ".docx"}),
    EXCEL("9", "excel", new String[]{".xls", ".xlsx"}),
    PPT("10", "ppt", new String[]{".ppt", ".pptx"}),
    PDF("11", "pdf", new String[]{".pdf"}),
    LOG("12", "日志", new String[]{".log"}),
    OTHER("13", "其他", new String[]{""}),;

    private final String value;
    private final String label;
    private final String[] suffix;

    public static OssTypeDict getByFileName(String fileName) {
        for (OssTypeDict dict : OssTypeDict.values()) {
            for (String suffix : dict.suffix) {
                if (fileName.contains(suffix)) {
                    return dict;
                }
            }
        }
        return OTHER;
    }
}
