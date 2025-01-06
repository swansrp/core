package com.bidr.admin.vo;

import com.bidr.admin.config.PortalDisplayNoneField;
import com.bidr.admin.config.PortalDisplayOnlyField;
import com.bidr.admin.config.PortalNoFilterField;
import com.bidr.admin.config.PortalSortField;
import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: BaseVO
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/12/18 15:17
 */
@Data
public class BaseVO {
    @PortalDisplayOnlyField
    @PortalSortField
    @PortalNoFilterField
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "创建人工号")
    private String createBy;

    @PortalNoFilterField
    @BindField(entity = AcUser.class, field = "name", condition = "this.createBy = customer_number")
    @ApiModelProperty(value = "创建人")
    private String createName;

    @PortalDisplayOnlyField
    @PortalSortField
    @PortalNoFilterField
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "更新人工号")
    private String updateBy;

    @PortalNoFilterField
    @BindField(entity = AcUser.class, field = "name", condition = "this.updateBy = customer_number")
    @ApiModelProperty(value = "更新人")
    private String updateName;

    @PortalDisplayNoneField
    @ApiModelProperty(value = "有效性")
    private String valid;
}
