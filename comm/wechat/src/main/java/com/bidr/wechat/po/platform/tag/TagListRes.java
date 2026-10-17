package com.bidr.wechat.po.platform.tag;

import com.bidr.wechat.po.WechatBaseRes;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: TagRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 10:48
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TagListRes extends WechatBaseRes {
    private List<UserTag> tags;
}
