package com.bidr.wechat.dao.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.persistence.*;

/**
 * Title: MmOpenidMap
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/27 10:02
 */

/**
 * 微信公众号账号对应关系
 */
@ApiModel(value = "com-sharp-wechat-dao-entity-MmOpenidMap")
@Data
@Table(name = "mm_openid_map")
public class MmOpenidMap {
    /**
     * openId
     */
    @Column(name = "open_id")
    @ApiModelProperty(value = "openId")
    private String openId;

    /**
     * 开放平台id
     */
    @Column(name = "union_id")
    @ApiModelProperty(value = "开放平台id")
    private String unionId;

    /**
     * 手机号
     */
    @Column(name = "phone")
    @ApiModelProperty(value = "手机号")
    private String phone;

    /**
     * 昵称
     */
    @Column(name = "nick_name")
    @ApiModelProperty(value = "昵称")
    private String nickName;

    /**
     * 头像地址
     */
    @Column(name = "avatar")
    @ApiModelProperty(value = "头像地址")
    private String avatar;
}