package com.bidr.wechat.po.platform.msg;

import com.bidr.wechat.constant.ReplyMsgTypeConst;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: ReplyTextMsg
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/25 20:48
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReplyTextMsg extends ReplyMsgBase {
    public static final String MSG_TYPE = ReplyMsgTypeConst.TEXT.name();
    private String content;
}
