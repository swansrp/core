package com.bidr.platform.constant.upload;

/**
 * Title: UploadProgressStep
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/21 12:29
 */
public enum UploadProgressStep {
    /**
     * 上传状态类型
     */
    INIT,
    UPLOAD,
    VALIDATE,
    SAVE,
    SUCCESS,
    FAILED,
    /** 被用户主动停止（或执行实例失联）的终态：已完成部分成果保留，可重新发起继续 */
    STOPPED
}
