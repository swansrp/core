package com.bidr.authorization.vo.menu;

import com.bidr.authorization.dao.entity.AcMenu;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Title: MenuTreeItem
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuTreeItem extends AcMenu {

    @JsonIgnore
    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;


    @JsonIgnore
    private Integer menuType;

    @JsonIgnore
    private String visible;

    @JsonIgnore
    private String status;

    @JsonIgnore
    private String perms;

    @JsonIgnore
    private String createBy;

    @JsonIgnore
    private Date createAt;

    @JsonIgnore
    private String updateBy;

    @JsonIgnore
    private Date updateAt;

    @JsonIgnore
    private String remark;
}
