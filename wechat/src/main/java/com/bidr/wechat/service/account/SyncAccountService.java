package com.bidr.wechat.service.account;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.user.RealNameReq;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.wechat.constant.WechatParamConst;
import com.bidr.wechat.constant.WechatTokenItem;
import com.bidr.wechat.dao.entity.MmOpenidMap;
import com.bidr.wechat.dao.repository.MmOpenidMapService;
import com.bidr.wechat.po.ocr.IdCardOcrReq;
import com.bidr.wechat.po.ocr.IdCardOcrRes;
import com.bidr.wechat.service.ocr.WechatOcrService;
import com.bidr.wechat.vo.auth.AuthRealNameReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * Title: AccountService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/19 15:43
 */
@Slf4j
@Service
public class SyncAccountService {
    @Resource
    private LoginService loginService;
    @Resource
    private MmOpenidMapService mmOpenidMapService;
    @Resource
    private WechatOcrService wechatOcrService;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private TokenService tokenService;
    @Resource
    private SysConfigCacheService frameCacheService;

    @Transactional(rollbackFor = Exception.class)
    public LoginRes loginOrReg(String openId, String unionId, String nickName, String avatar) {
        MmOpenidMap map = mmOpenidMapService.getOpenidMapByOpenId(openId);
        if (map == null) {
            map = buildMmOpenidMap(openId, unionId, nickName, avatar);
            mmOpenidMapService.insert(map);
        }
        LoginRes res;
        if (StringUtils.isNotBlank(map.getPhone())) {
            res = loginService.loginOrReg(map.getPhone());
            userInfoService.setAvatar(res.getCustomerNumber(), avatar);
        } else if (StringUtils.isNotBlank(map.getUnionId())) {
            res = loginService.loginOrRegByWechatUnionId(map.getUnionId(), map.getNickName(), avatar);
        } else {
            res = buildGuestLoginRes(map.getOpenId(), map.getNickName());
        }
        return res;
    }

    private MmOpenidMap buildMmOpenidMap(String openId, String unionId, String nickName, String avatar) {
        MmOpenidMap map = new MmOpenidMap();
        map.setOpenId(openId);
        map.setAvatar(avatar);
        boolean openIdAsAccount = true;
        try {
            String openIdAccount = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_OPENID_ACCOUNT);
            openIdAsAccount = StringUtils.equals(openIdAccount, CommonConst.YES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (StringUtils.isNotBlank(unionId)) {
            map.setUnionId(unionId);
        } else if (openIdAsAccount) {
            map.setUnionId(openId);
        }
        map.setNickName(nickName);
        return map;
    }

    private LoginRes buildGuestLoginRes(String openId, String nickName) {
        TokenInfo accessToken = tokenService.buildWechatToken(openId);
        TokenHolder.set(accessToken);
        tokenService.putItem(accessToken, TokenItem.OPERATOR.name(), openId);
        tokenService.putItem(accessToken, TokenItem.NICK_NAME.name(), nickName);
        LoginRes res = new LoginRes();
        res.setCustomerNumber(openId);
        res.setName(nickName);
        res.setAccessToken(accessToken.getToken());
        return res;
    }

    @Transactional(rollbackFor = Exception.class)
    public void realName(AuthRealNameReq req) {
        IdCardOcrReq idCardOcrReq = ReflectionUtil.copy(req, IdCardOcrReq.class);
        IdCardOcrRes idCardOcrRes = wechatOcrService.idCardOcr(idCardOcrReq);
        RealNameReq realNameReq = ReflectionUtil.copy(idCardOcrRes, RealNameReq.class);
        userInfoService.realName(realNameReq);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginRes loginOrRegByPhoneNumber(String phoneNumber) {
        String openId = tokenService.getItem(WechatTokenItem.OPEN_ID.name(), String.class);
        String nickName = tokenService.getItem(TokenItem.NICK_NAME.name(), String.class);
        Validator.assertNotBlank(openId, ErrCodeSys.SYS_ERR_MSG, "未检测到微信登录");
        MmOpenidMap map = mmOpenidMapService.getOpenidMapByOpenId(openId);
        Validator.assertNotNull(map, ErrCodeSys.PA_DATA_NOT_EXIST, "微信用户");
        map.setPhone(phoneNumber);
        mmOpenidMapService.updateById(map);
        return loginService.loginOrRegByWechatPhoneNumber(map.getUnionId(), nickName, phoneNumber, map.getAvatar());
    }
}
