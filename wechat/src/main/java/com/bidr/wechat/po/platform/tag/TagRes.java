package com.bidr.wechat.po.platform.tag;

import com.bidr.wechat.po.WechatBaseRes;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: TagRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 10:50
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TagRes extends WechatBaseRes {
    private UserTag tag;
}
