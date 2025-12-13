package com.bidr.oss.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Wiki页面实体
 *
 * @author sharp
 * @since 2025-12-12
 */
@ApiModel(description = "Wiki页面")
@Data
@AccountContextFill
@TableName(value = "sa_wiki_page")
public class SaWikiPage {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "页面ID")
    private Long id;

    @TableField(value = "title")
    @ApiModelProperty(value = "页面标题")
    private String title;

    @TableField(value = "content")
    @ApiModelProperty(value = "页面内容(JSON格式)")
    private String content;

    @TableField(value = "content_html")
    @ApiModelProperty(value = "页面内容(HTML格式)")
    private String contentHtml;

    @TableField(value = "parent_id")
    @ApiModelProperty(value = "父级页面ID")
    private Long parentId;

    @TableField(value = "sort_order")
    @ApiModelProperty(value = "排序号")
    private Integer sortOrder;

    @TableField(value = "author_id")
    @ApiModelProperty(value = "作者用户ID")
    private String authorId;

    @TableField(value = "modify_at")
    @ApiModelProperty(value = "内容更新时间")
    private Date modifyAt;

    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态: 1-草稿, 2-已发布")
    private String status;

    @TableField(value = "is_public")
    @ApiModelProperty(value = "是否公开: 0-私有, 1-公开")
    private String isPublic;

    @TableField(value = "view_count")
    @ApiModelProperty(value = "浏览次数")
    private Long viewCount;

    @TableField(value = "version")
    @ApiModelProperty(value = "版本号")
    private Integer version;

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

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
