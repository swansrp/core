package com.bidr.admin.manage.account.vo;

import com.bidr.authorization.dao.entity.AcAccount;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "姓名")
    private String name;

    @ApiModelProperty(value = "人员性别")
    private String gender;

    @ApiModelProperty(value = "民族")
    private String nationality;

    @ApiModelProperty(value = "籍贯")
    private String nativePlace;

    @ApiModelProperty(value = "政治面貌")
    private String politicalOutlook;

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

    private String userName;

    @ApiModelProperty(value = "人员照片链接")
    private String pictureLink;

    @ApiModelProperty(value = "人员电子签名链接")
    private String signatureLink;

    @ApiModelProperty(value = "在职状态")
    private String employStatus;

    @ApiModelProperty(value = "人员启用状态")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    private Date createAt;
}
