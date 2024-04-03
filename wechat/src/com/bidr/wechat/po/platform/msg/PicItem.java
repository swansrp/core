package com.bidr.wechat.po.platform.msg;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Title: PicItem
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 21:22
 */
@Data
@JacksonXmlRootElement(localName = "PicList")
public class PicItem {
    @JacksonXmlProperty(localName = "PicMd5Sum")
    private String picMd5Sum;
}
