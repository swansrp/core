package com.bidr.oss.vo;

import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Wiki页面VO
 *
 * @author sharp
 * @since 2025-12-12
 */
@ApiModel(description = "Wiki页面")
@Data
public class OssWikiPageVO {

    /**
     * 页面ID
     */
    @ApiModelProperty(value = "页面ID")
    private Long id;

    /**
     * 页面标题
     */
    @ApiModelProperty(value = "页面标题")
    private String title;

    /**
     * 模式
     */
    @ApiModelProperty(value = "模式:0,富文本;1,markdown")
    private String mode;

    /**
     * 页面内容(JSON格式)
     */
    @ApiModelProperty(value = "页面内容(JSON格式)")
    private String content;

    /**
     * 页面内容(HTML格式)
     */
    @ApiModelProperty(value = "页面内容(HTML格式)")
    private String contentHtml;

    /**
     * 父级页面ID
     */
    @ApiModelProperty(value = "父级页面ID")
    private Long parentId;

    /**
     * 排序号
     */
    @ApiModelProperty(value = "排序号")
    private Integer sortOrder;

    @ApiModelProperty(value = "作者用户ID")
    private String authorId;

    @ApiModelProperty(value = "作者名称")
    @BindField(entity = AcUser.class, field = "name", condition = "this.authorId=customer_number")
    private String authorName;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "内容更新时间")
    private Date modifyAt;

    @ApiModelProperty(value = "状态: 1-草稿, 2-已发布")
    private String status;

    @ApiModelProperty(value = "是否公开: 0-私有, 1-公开")
    private String isPublic;

    @ApiModelProperty(value = "浏览次数")
    private Long viewCount;

    @ApiModelProperty(value = "版本号")
    private Integer version;

    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @ApiModelProperty(value = "创建者")
    private String createBy;

    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @ApiModelProperty(value = "是否可编辑(当前用户)")
    private Boolean canEdit;

    @ApiModelProperty(value = "是否是作者(当前用户)")
    private Boolean isAuthor;
}
