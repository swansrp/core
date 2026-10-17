package com.bidr.oss.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Wiki协作者关系实体
 *
 * @author sharp
 * @since 2025-12-12
 */
@ApiModel(description = "Wiki协作者")
@Data
@AccountContextFill
@TableName(value = "sa_wiki_collaborator")
public class SaWikiCollaborator {

    /**
     * 页面ID
     */
    @MppMultiId
    @TableField(value = "page_id")
    @ApiModelProperty(value = "页面ID")
    private Long pageId;

    /**
     * 用户ID
     */
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户ID")
    private String userId;

    @TableField(value = "permission")
    @ApiModelProperty(value = "权限类型: 1-只读, 2-编辑")
    private String permission;

    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态: 0-待审批, 1-已通过, 2-已拒绝")
    private String status;

    @TableField(value = "request_msg")
    @ApiModelProperty(value = "申请说明")
    private String requestMsg;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
