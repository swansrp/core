package com.bidr.wechat.po.ocr;

import com.bidr.wechat.po.WechatBaseRes;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: IdCardOcrRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/15 15:02
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IdCardOcrRes extends WechatBaseRes {
    private String type;
    private String name;
    private String id;
    private String addr;
    private String gender;
    private String nationality;
}
