package com.bidr.socket.io.vo.room;


import com.bidr.socket.io.constant.dict.RoomRoleDict;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatMemberVO
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/5 14:11
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.room
 */
@Data
public class ChatMemberVO {
    @ApiModelProperty("用户id")
    private String userId;
    @ApiModelProperty("用户名")
    private String userName;
    @ApiModelProperty("头像")
    private String avatar;
    //@PortalDict(dict = RoomRoleDict.class)
    @ApiModelProperty("角色")
    private String role;
}
