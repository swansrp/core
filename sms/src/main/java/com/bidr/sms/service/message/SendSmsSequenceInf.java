package com.bidr.sms.service.message;

/**
 * Title: SendSmsSequenceInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/15 12:30
 */
public interface SendSmsSequenceInf {
    /**
     * 生成发送历史序列号
     *
     * @return 发送历史序列号
     */
    String getSendSequence();
}
