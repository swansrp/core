package com.bidr.admin.vo;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.authorization.dao.entity.AcRole;
import com.diboot.core.binding.annotation.BindField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: PortalAssociateRes
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/7/2 13:38
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalAssociateRes extends SysPortalAssociate {

    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "角色id")
    private Long roleId;

    @ApiModelProperty(value = "角色名称")
    @BindField(entity = AcRole.class, field = "roleName", condition = "this.roleId = role_id")
    private String roleName;

    @ApiModelProperty(value = "实体id")
    private Long portalId;

    @ApiModelProperty(value = "实体关系")
    private String bindType;

    @ApiModelProperty(value = "目标实体id")
    private Long bindPortalId;

    @BindField(entity = SysPortal.class, field = "name", condition = "this.roleId = role_id and this.bindPortalId = id")
    @ApiModelProperty(value = "实体英文名称")
    private String bindPortalName;


    @ApiModelProperty(value = "关联字段")
    private String bindProperty;

    @BindField(entity = SysPortalColumn.class, field = "displayName",
               condition = "this.portalId = portal_id and this.bindProperty = property")
    @ApiModelProperty(value = "关联字段名")
    private String bindPropertyName;

    @ApiModelProperty(value = "默认排序字段")
    private String bindSortProperty;

    @BindField(entity = SysPortalColumn.class, field = "displayName",
               condition = "this.portalId = portal_id and this.bindProperty = id")
    @ApiModelProperty(value = "默认排序字段名")
    private String bindSortPropertyName;

    @ApiModelProperty(value = "默认排序方式")
    private String bindSortType;

    @ApiModelProperty(value = "树形展示")
    private String treeMode;

    @ApiModelProperty(value = "树形结构显示是否严格节点显示")
    private String treeCheckStrict;

    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;
}
