package com.bidr.platform.vo.portal;

import com.bidr.platform.dao.entity.SysPortalColumn;
import com.diboot.core.binding.annotation.BindEntityList;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: PortalWithColumnsRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalWithColumnsRes extends PortalUpdateReq {
    @ApiModelProperty("字段")
    @BindEntityList(entity = SysPortalColumn.class, condition = "this.id = portal_id", orderBy = "display_order",
            deepBind = true)
    private List<SysPortalColumn> columns;
}
