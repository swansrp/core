package com.bidr.socket.io.vo.chat.friend;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: MakeFriendReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/7 16:47
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.chat.friend
 */
@Data
public class MakeFriendReq {
    @ApiModelProperty("好友id")
    private String friendId;
}
