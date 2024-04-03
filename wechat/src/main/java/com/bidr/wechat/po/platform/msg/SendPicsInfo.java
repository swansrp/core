package com.bidr.wechat.po.platform.msg;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

/**
 * Title: SendPicsInfo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 21:19
 */
@Data
@JacksonXmlRootElement(localName = "SendPicsInfo")
public class SendPicsInfo {
    @JacksonXmlProperty(localName = "Count")
    private Integer count;
    @JacksonXmlProperty(localName = "PicList")
    private List<PicItem> picList;
}
