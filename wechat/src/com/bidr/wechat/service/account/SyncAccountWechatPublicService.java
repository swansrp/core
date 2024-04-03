package com.bidr.wechat.service.account;

import com.bidr.wechat.dao.entity.MmOpenidMap;
import com.bidr.wechat.dao.repository.MmOpenidMapService;
import com.bidr.wechat.po.platform.user.PublicPlatformUserRes;
import com.bidr.wechat.po.platform.user.QueryWechatPlatformUserListRes;
import com.bidr.wechat.service.user.WechatPublicUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: SyncAccountWechatPublicService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/17 11:49
 */
@Service
public class SyncAccountWechatPublicService {
    @Resource
    private MmOpenidMapService mmOpenidMapService;
    @Resource
    private WechatPublicUserService wechatPublicUserService;

    public void syncUserInfo() {
        QueryWechatPlatformUserListRes res = new QueryWechatPlatformUserListRes();
        do {
            res = wechatPublicUserService.getPlatformUserList(res.getNextOpenId());
            for (String openId : res.getData().getOpenId()) {
                MmOpenidMap map = mmOpenidMapService.getOpenidMapByOpenId(openId);
                if (map == null) {
                    PublicPlatformUserRes userInfo = wechatPublicUserService.getWechatUserInfo(openId);
                    if (StringUtils.isNotBlank(userInfo.getUnionId())) {
                        map = new MmOpenidMap();
                        map.setOpenId(openId);
                        map.setUnionId(userInfo.getUnionId());
                        mmOpenidMapService.insert(map);
                    }
                }
            }
        } while (StringUtils.isNotBlank(res.getNextOpenId()));

    }

}
