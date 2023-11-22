package com.bidr.platform.vo.portal;

import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.diboot.core.binding.annotation.BindEntityList;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: PortalRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalRes extends SysPortal {
    @ApiModelProperty("字段")
    @BindEntityList(entity = SysPortalColumn.class, condition = "this.id = portal_id", deepBind = true)
    private List<SysPortalColumn> columns;
}
