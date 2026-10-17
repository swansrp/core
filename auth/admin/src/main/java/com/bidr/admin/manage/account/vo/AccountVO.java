package com.bidr.admin.manage.account.vo;

import com.bidr.admin.config.PortalDictField;
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalImageField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.authorization.constants.dict.GenderDict;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: AccountVO
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/8/14 16:28
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountVO extends AcAccount {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private String id;

    @PortalNameField
    @ApiModelProperty(value = "姓名")
    private String name;

    @PortalDictField(GenderDict.class)
    @ApiModelProperty(value = "人员性别")
    private String gender;

    @ApiModelProperty(value = "民族")
    private String nationality;

    @ApiModelProperty(value = "籍贯")
    private String nativePlace;

    @ApiModelProperty(value = "政治面貌")
    private String politicalOutlook;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "参加工作日期")
    private Date workDate;

    @ApiModelProperty(value = "身份证号")
    private String idNumber;

    @ApiModelProperty(value = "专业技术职务")
    private String profession;

    @ApiModelProperty(value = "公司人才工程名称")
    private String talent;

    @ApiModelProperty(value = "人员电子邮件")
    private String email;

    @ApiModelProperty(value = "人员手机")
    private String mobile;

    @ApiModelProperty(value = "人员类别")
    private String category;

    @ApiModelProperty(value = "人员所属部门")
    private String department;

    @ApiModelProperty(value = "人员所属组织")
    private String org;

    @ApiModelProperty(value = "用户登录名")
    private String userName;

    @PortalImageField
    @ApiModelProperty(value = "人员照片链接")
    private String pictureLink;

    @PortalImageField
    @ApiModelProperty(value = "人员电子签名链接")
    private String signatureLink;

    @PortalDictField(BoolDict.class)
    @ApiModelProperty(value = "在职状态")
    private String employStatus;

    @PortalDictField(BoolDict.class)
    @ApiModelProperty(value = "人员启用状态")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;
}
