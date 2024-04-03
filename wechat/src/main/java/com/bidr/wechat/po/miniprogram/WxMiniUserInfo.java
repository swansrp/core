package com.bidr.wechat.po.miniprogram;

import lombok.Data;

/**
 * Title: WxMiniUserInfo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/10 23:42
 */
@Data
public class WxMiniUserInfo {
    private String openId;
    private String unionId;
    private String nickName;
    private String gender;
    private String language;
    private String city;
    private String province;
    private String country;
    private String avatarUrl;
    private WxMiniUserInfoWaterMark watermark;
}
