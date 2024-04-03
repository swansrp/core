package com.bidr.wechat.po.platform.user;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: PublicPlatformUserRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/23 22:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PublicPlatformUserRes extends WechatBaseRes {
    @ApiModelProperty("用户是否订阅该公众号标识，值为0时，代表此用户没有关注该公众号，拉取不到其余信息。")
    private String subscribe;
    @ApiModelProperty("用户的标识，对当前公众号唯一")
    @JsonProperty("openid")
    private String openId;
    @ApiModelProperty("用户的昵称")
    private String nickname;
    @ApiModelProperty("用户的性别，值为1时是男性，值为2时是女性，值为0时是未知")
    private String sex;
    @ApiModelProperty("语言")
    private String language;
    @ApiModelProperty("用户所在城市")
    private String city;
    @ApiModelProperty("用户所在省份")
    private String province;
    @ApiModelProperty("用户所在国家")
    private String country;
    @ApiModelProperty("用户头像")
    @JsonProperty("headimgurl")
    private String headImgUrl;
    @ApiModelProperty("用户关注时间，为时间戳。如果用户曾多次关注，则取最后关注时间")
    @JsonProperty("subscribe_time")
    private Long subscribeTime;
    @ApiModelProperty("只有在用户将公众号绑定到微信开放平台帐号后，才会出现该字段。")
    @JsonProperty("unionid")
    private String unionId;
    @ApiModelProperty("公众号运营者对粉丝的备注，公众号运营者可在微信公众平台用户管理界面对粉丝添加备注")
    private String remark;
    @ApiModelProperty("用户所在的分组ID")
    @JsonProperty("groupid")
    private String groupId;
    @ApiModelProperty("用户被打上的标签ID列表")
    @JsonProperty("tagid_list")
    private List<Integer> tagIdList;
    @ApiModelProperty("返回用户关注的渠道来源")
    @JsonProperty("subscribe_scene")
    private String subscribeScene;
    @ApiModelProperty("二维码扫码场景（开发者自定义）")
    @JsonProperty("qr_scene")
    private String qrScene;
    @ApiModelProperty("二维码扫码场景描述（开发者自定义）")
    @JsonProperty("qr_scene_str")
    private String qrSceneStr;
}
