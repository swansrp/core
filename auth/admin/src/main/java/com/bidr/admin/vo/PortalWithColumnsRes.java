package com.bidr.admin.vo;

import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.dao.entity.SysPortalColumn;
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
    private List<SysPortalColumn> columns;

    @ApiModelProperty("关联")
    private List<PortalAssociateRes> associates;
}
