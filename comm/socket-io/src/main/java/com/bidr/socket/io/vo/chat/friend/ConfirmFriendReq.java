package com.bidr.socket.io.vo.chat.friend;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ConfirmFriendReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/7 17:04
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.chat.friend
 */
@Data
public class ConfirmFriendReq {
    @ApiModelProperty("好友id")
    private String friendId;
    @ApiModelProperty("审核状态")
    private String confirmed;
}
