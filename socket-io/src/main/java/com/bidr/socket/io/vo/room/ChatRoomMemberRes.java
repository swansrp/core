package com.bidr.socket.io.vo.room;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ChatRoomMemberRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/5 11:30
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.room
 */
@Data
public class ChatRoomMemberRes {
    @ApiModelProperty("房间成员列表")
    private List<ChatMemberVO> memberList;
}
