package com.bidr.wechat.po.platform.msg;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Title: ReceiveUserMsg
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/25 21:02
 */
@Data
@JacksonXmlRootElement(localName = "xml")
public class ReceiveUserMsg {
    @JacksonXmlProperty(localName = "ToUserName")
    private String toUserName;
    @JacksonXmlProperty(localName = "FromUserName")
    private String fromUserName;
    @JacksonXmlProperty(localName = "MsgType")
    private String msgType;
    @JacksonXmlProperty(localName = "Event")
    private String event;
    @JacksonXmlProperty(localName = "EventKey")
    private String eventKey;
    @JacksonXmlProperty(localName = "MsgId")
    private String msgId;
    @JacksonXmlProperty(localName = "Content")
    private String content;
    @JacksonXmlProperty(localName = "MediaId")
    private String mediaId;
    @JacksonXmlProperty(localName = "Format")
    private String format;
    @JacksonXmlProperty(localName = "Recognition")
    private String recognition;
    @JacksonXmlProperty(localName = "SendLocationInfo")
    private SendLocationInfo sendLocationInfo;
    @JacksonXmlProperty(localName = "ScanCodeInfo")
    private ScanCodeInfo scanCodeInfo;
    @JacksonXmlProperty(localName = "SendPicsInfo")
    private SendPicsInfo sendPicsInfo;
    @JacksonXmlProperty(localName = "PicUrl")
    private String picUrl;
    @JacksonXmlProperty(localName = "Url")
    private String url;
    @JacksonXmlProperty(localName = "Title")
    private String title;
    @JacksonXmlProperty(localName = "Description")
    private String description;
}
