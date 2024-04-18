package com.bidr.qcc.dto.enterprise;

import com.diboot.core.binding.annotation.BindDict;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * Title: EnterpriseAdvanceRes
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 10:49
 */
@Data
public class EnterpriseAdvancedRes {
    @ApiModelProperty("主键")
    @JsonProperty("KeyNo")
    private String keyNo;

    @ApiModelProperty("企业名称")
    @JsonProperty("Name")
    private String name;

    @ApiModelProperty(
            "根据企业性质的不同返回不同的值，具体如下：EntType = 0/1/4/6/7/9/10/11/-1 中国境内企业时：该字段返回工商注册号；EntType = 3 中国香港企业时：该字段返回企业编号；EntType = 5 中国台湾企业时：该字段返回企业编号")
    @JsonProperty("No")
    private String no;

    @ApiModelProperty("登记机关")
    @JsonProperty("BelongOrg")
    private String belongOrg;

    @ApiModelProperty("法定代表人ID")
    @JsonProperty("OperId")
    private String corporateId;

    @ApiModelProperty("法定代表人名称")
    @JsonProperty("OperName")
    private String corporateName;

    @ApiModelProperty("成立日期")
    @JsonProperty("StartDate")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startDate;

    @ApiModelProperty("吊销日期")
    @JsonProperty("EndDate")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endDate;

    @ApiModelProperty("登记状态")
    @JsonProperty("Status")
    private String status;

    @ApiModelProperty("省份")
    @JsonProperty("Province")
    private String province;

    @ApiModelProperty("更新日期")
    @JsonProperty("UpdatedDate")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedDate;

    @ApiModelProperty(
            "根据企业性质的不同返回不同的值，具体如下：EntType = 0/1/4/6/7/9/10/11/-1 中国境内企业时：该字段返回统一社会信用代码；EntType = 3 中国香港企业时：该字段返回商业登记号码")
    @JsonProperty("CreditCode")
    private String creditCode;

    @ApiModelProperty("注册资本")
    @JsonProperty("RegistCapi")
    private String registerCapital;

    @ApiModelProperty("企业类型")
    @JsonProperty("EconKind")
    private String econKind;

    @ApiModelProperty("注册地址")
    @JsonProperty("Address")
    private String address;

    @ApiModelProperty("经营范围")
    @JsonProperty("Scope")
    private String scope;

    @ApiModelProperty("营业期限始")
    @JsonProperty("TermStart")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date termStart;

    @ApiModelProperty("营业期限至")
    @JsonProperty("TermEnd")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date termEnd;

    @ApiModelProperty("核准日期")
    @JsonProperty("CheckDate")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date checkDate;

    @ApiModelProperty("组织机构代码")
    @JsonProperty("OrgNo")
    private String orgNo;

    @ApiModelProperty("是否上市（0-未上市，1-上市）")
    @JsonProperty("IsOnStock")
    private String isOnStock;

    @ApiModelProperty("股票代码（如A股和港股同时存在，优先显示A股代码）")
    @JsonProperty("StockNumber")
    private String stockNumber;

    @ApiModelProperty("上市类型（A股、港股、美股、新三板、新四板）")
    @JsonProperty("StockType")
    private String stockType;

    @ApiModelProperty("曾用名")
    @JsonProperty("OriginalName")
    private List<OriginalName> originalName;

    @ApiModelProperty("图片链接")
    @JsonProperty("ImageUrl")
    private String imageUrl;

    @ApiModelProperty(
            "企业性质，0-大陆企业，1-社会组织 ，3-中国香港公司，4-事业单位，5-中国台湾公司，6-基金会，7-医院，8-海外公司，9-律师事务所，10-学校 ，11-机关单位，-1-其他")
    @JsonProperty("EntType")
    @BindDict(type = "QCC_ENTERPRISE_TYPE_DICT")
    private String entType;

    @ApiModelProperty("实缴资本")
    @JsonProperty("RecCap")
    private String recCap;

    @ApiModelProperty("注销吊销信息")
    @JsonProperty("RevokeInfo")
    private RevokeInfo revokeInfo;

    @ApiModelProperty("行政区域")
    @JsonProperty("Area")
    private AreaInfo area;

    @ApiModelProperty("行政区划代码")
    @JsonProperty("AreaCode")
    private String areaCode;


}
