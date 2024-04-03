package com.bidr.wechat.po.platform.msg;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Title: SendLocationInfo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 21:04
 */
@Data
@JacksonXmlRootElement(localName = "SendLocationInfo")
public class SendLocationInfo {
    @JacksonXmlProperty(localName = "Location_X")
    private String locationX;
    @JacksonXmlProperty(localName = "Location_Y")
    private String locationY;
    @JacksonXmlProperty(localName = "Scale")
    private String scale;
    @JacksonXmlProperty(localName = "Label")
    private String label;
    @JacksonXmlProperty(localName = "Poiname")
    private String poiName;
}
