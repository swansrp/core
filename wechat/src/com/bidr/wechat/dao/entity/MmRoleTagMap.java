package com.bidr.wechat.dao.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.persistence.*;

/**
 * Title: MmRoleTagMap
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/27 10:02
 */

/**
 * 用户标签角色关系
 */
@ApiModel(value = "com-sharp-wechat-dao-entity-MmRoleTagMap")
@Data
@Table(name = "mm_role_tag_map")
public class MmRoleTagMap {
    /**
     * 角色id
     */
    @Column(name = "role_id")
    @ApiModelProperty(value = "角色id")
    private Long roleId;

    /**
     * 标签id
     */
    @Column(name = "tag_id")
    @ApiModelProperty(value = "标签id")
    private Integer tagId;

    /**
     * 标签名
     */
    @Column(name = "tag_name")
    @ApiModelProperty(value = "标签名")
    private String tagName;

    /**
     * 个性菜单id
     */
    @Column(name = "menu_id")
    @ApiModelProperty(value = "个性菜单id")
    private String menuId;


    
}