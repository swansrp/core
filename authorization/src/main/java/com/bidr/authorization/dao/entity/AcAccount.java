package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import lombok.Data;

 /**
 * Title: AcAccount
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/23 13:34
 */

/**
 * 用户表
 */
@ApiModel(value = "用户表")
@Data
@TableName(value = "ac_account")
public class AcAccount {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    private String id;

    /**
     * 姓名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "姓名")
    private String name;

    /**
     * 人员性别
     */
    @TableField(value = "gender")
    @ApiModelProperty(value = "人员性别")
    private String gender;

    /**
     * 民族
     */
    @TableField(value = "nationality")
    @ApiModelProperty(value = "民族")
    private String nationality;

    /**
     * 籍贯
     */
    @TableField(value = "native_place")
    @ApiModelProperty(value = "籍贯")
    private String nativePlace;

    /**
     * 政治面貌
     */
    @TableField(value = "political_outlook")
    @ApiModelProperty(value = "政治面貌")
    private String politicalOutlook;

    /**
     * 参加工作日期
     */
    @TableField(value = "work_date")
    @ApiModelProperty(value = "参加工作日期")
    private Date workDate;

    /**
     * 身份证号
     */
    @TableField(value = "id_number")
    @ApiModelProperty(value = "身份证号")
    private String idNumber;

    /**
     * 专业技术职务
     */
    @TableField(value = "profession")
    @ApiModelProperty(value = "专业技术职务")
    private String profession;

    /**
     * 公司人才工程名称
     */
    @TableField(value = "talent")
    @ApiModelProperty(value = "公司人才工程名称")
    private String talent;

    /**
     * 人员电子邮件
     */
    @TableField(value = "email")
    @ApiModelProperty(value = "人员电子邮件")
    private String email;

    /**
     * 人员手机
     */
    @TableField(value = "mobile")
    @ApiModelProperty(value = "人员手机")
    private String mobile;

    /**
     * 人员类别
     */
    @TableField(value = "category")
    @ApiModelProperty(value = "人员类别")
    private String category;

    /**
     * 人员所属部门
     */
    @TableField(value = "department")
    @ApiModelProperty(value = "人员所属部门")
    private String department;

    /**
     * 人员所属组织
     */
    @TableField(value = "org")
    @ApiModelProperty(value = "人员所属组织")
    private String org;

    /**
     * 用户名
     */
    @TableField(value = "user_name")
    @ApiModelProperty(value = "用户名")
    private String userName;

    /**
     * 人员照片链接
     */
    @TableField(value = "picture_link")
    @ApiModelProperty(value = "人员照片链接")
    private String pictureLink;

    /**
     * 人员电子签名链接
     */
    @TableField(value = "signature_link")
    @ApiModelProperty(value = "人员电子签名链接")
    private String signatureLink;

    /**
     * 在职状态
     */
    @TableField(value = "employ_status")
    @ApiModelProperty(value = "在职状态")
    private String employStatus;

    /**
     * 人员启用状态
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "人员启用状态")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    public static final String COL_ID = "id";

    public static final String COL_NAME = "name";

    public static final String COL_GENDER = "gender";

    public static final String COL_NATIONALITY = "nationality";

    public static final String COL_NATIVE_PLACE = "native_place";

    public static final String COL_POLITICAL_OUTLOOK = "political_outlook";

    public static final String COL_WORK_DATE = "work_date";

    public static final String COL_ID_NUMBER = "id_number";

    public static final String COL_PROFESSION = "profession";

    public static final String COL_TALENT = "talent";

    public static final String COL_EMAIL = "email";

    public static final String COL_MOBILE = "mobile";

    public static final String COL_CATEGORY = "category";

    public static final String COL_DEPARTMENT = "department";

    public static final String COL_ORG = "org";

    public static final String COL_USER_NAME = "user_name";

    public static final String COL_PICTURE_LINK = "picture_link";

    public static final String COL_SIGNATURE_LINK = "signature_link";

    public static final String COL_EMPLOY_STATUS = "employ_status";

    public static final String COL_STATUS = "status";

    public static final String COL_CREATE_AT = "create_at";
}