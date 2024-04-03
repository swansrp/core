package com.bidr.wechat.service.user;

import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.WechatBaseRes;
import com.bidr.wechat.po.platform.tag.UserTagReq;
import com.bidr.wechat.po.platform.user.PublicPlatformUserReq;
import com.bidr.wechat.po.platform.user.PublicPlatformUserRes;
import com.bidr.wechat.po.platform.user.QueryWechatPlatFormUserListReq;
import com.bidr.wechat.po.platform.user.QueryWechatPlatformUserListRes;
import com.bidr.wechat.service.WechatPublicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: WechatPublicUserServiceImpl
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 16:02
 */
@Service
public class WechatPublicUserService {
    @Resource
    private WechatPublicService wechatPublicService;

    public QueryWechatPlatformUserListRes getPlatformUserList() {
        return getPlatformUserList(null);
    }

    public QueryWechatPlatformUserListRes getPlatformUserList(String nextOpenId) {
        QueryWechatPlatFormUserListReq req = new QueryWechatPlatFormUserListReq();
        if (StringUtils.isNotBlank(nextOpenId)) {
            req.setNextOpenId(nextOpenId);
        }
        return wechatPublicService.get(WechatUrlConst.WECHAT_PUBLIC_USER_INFO_LIST_GET_URL,
                req, QueryWechatPlatformUserListRes.class);
    }

    public PublicPlatformUserRes getWechatUserInfo(String openId) {
        PublicPlatformUserReq req = new PublicPlatformUserReq();
        req.setOpenId(openId);
        return wechatPublicService.get(WechatUrlConst.WECHAT_PUBLIC_USER_INFO_GET_URL, req, PublicPlatformUserRes.class);
    }

    public WechatBaseRes setUserTag(String openId, Integer tagId) {
        UserTagReq req = buildUserTagReq(openId, tagId);
        return wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_SET_USER_TAG_POST_URL, WechatBaseRes.class, req);
    }

    private UserTagReq buildUserTagReq(String openId, Integer tagId) {
        UserTagReq req = new UserTagReq();
        List<String> openIdList = new ArrayList<>();
        openIdList.add(openId);
        req.setOpenIdList(openIdList);
        req.setTagId(tagId);
        return req;
    }
}
