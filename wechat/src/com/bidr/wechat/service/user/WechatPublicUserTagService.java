package com.bidr.wechat.service.user;

import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.platform.tag.TagListRes;
import com.bidr.wechat.po.platform.tag.TagReq;
import com.bidr.wechat.po.platform.tag.TagRes;
import com.bidr.wechat.po.platform.tag.UserTag;
import com.bidr.wechat.service.WechatPublicService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatPublicUserTagService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 10:43
 */
@Service
public class WechatPublicUserTagService {
    @Resource
    private WechatPublicService wechatPublicService;

    public UserTag createUserTag(UserTag userTag) {
        TagReq req = buildTagReq(userTag);
        TagRes res = wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_CREATE_USER_TAG_POST_URL, TagRes.class, req);
        UserTag userTagRes = res.getTag();
        return userTagRes;
    }

    private TagReq buildTagReq(UserTag userTag) {
        TagReq req = new TagReq();
        req.setTag(userTag);
        return req;
    }

    public TagListRes getUserTag() {
        return wechatPublicService.get(WechatUrlConst.WECHAT_PUBLIC_GET_USER_TAG_GET_URL, TagListRes.class);
    }

    public void deleteUserTag(UserTag userTag) {
        TagReq req = buildTagReq(userTag);
        wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_DELETE_USER_TAG_POST_URL, TagRes.class, req);
    }


}
