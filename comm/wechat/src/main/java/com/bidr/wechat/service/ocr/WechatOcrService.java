package com.bidr.wechat.service.ocr;

import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.ocr.IdCardOcrReq;
import com.bidr.wechat.po.ocr.IdCardOcrRes;
import com.bidr.wechat.service.WechatPublicService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatOcrService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/15 12:03
 */
@Service
public class WechatOcrService {

    @Resource
    private WechatPublicService wechatPublicService;

    public IdCardOcrRes idCardOcr(IdCardOcrReq req) {
        return wechatPublicService.post(WechatUrlConst.WECHAT_OCR_ID_CARD, IdCardOcrRes.class, req, null);
    }
}
