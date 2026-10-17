package com.bidr.socket.io.vo.chat.msg;

import lombok.Data;

/**
 * Title: RetractChatReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/6/7 14:01
 */
@Data
public class RetractChatReq {
    private String msgId;
    private String targetId;
    private String roomId;
}
