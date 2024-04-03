package com.bidr.wechat.po.platform.msg;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * Title: ScanCodeInfo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 21:08
 */
@Data
@JacksonXmlRootElement(localName = "ScanCodeInfo")
public class ScanCodeInfo {
    @JacksonXmlProperty(localName = "ScanType")
    private String scanType;
    @JacksonXmlProperty(localName = "ScanResult")
    private String scanResult;
}
