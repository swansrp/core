package com.bidr.socket.io.vo.room;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatRoomMemberReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/5 11:30
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.room
 */
@Data
public class ChatRoomMemberReq {
    @ApiModelProperty("房间id")
    private String roomId;
}
