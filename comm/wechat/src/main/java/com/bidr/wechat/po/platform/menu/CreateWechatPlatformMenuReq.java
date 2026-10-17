package com.bidr.wechat.po.platform.menu;

import lombok.Data;

import java.util.List;

/**
 * Title: WechatPlatformMenuRefreshReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 16:58
 */
@Data
public class CreateWechatPlatformMenuReq {
    List<WechatPlatformMenu> button;
}
