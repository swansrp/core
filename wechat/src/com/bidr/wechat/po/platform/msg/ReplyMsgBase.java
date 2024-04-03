package com.bidr.wechat.po.platform.msg;

import lombok.Data;

/**
 * Title: ReplyMsg
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/25 20:45
 */
@Data
public class ReplyMsgBase {
    private String toUser;
    private String fromUser;
    private String timeStamp;
}
