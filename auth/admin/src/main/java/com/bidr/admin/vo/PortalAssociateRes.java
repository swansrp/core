package com.bidr.admin.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.bidr.admin.config.*;
import com.bidr.admin.constant.dict.AllPortalDict;
import com.bidr.admin.constant.dict.BindTypeDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.config.response.BindRepo;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
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
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "角色id")
    private Long roleId;

    @ApiModelProperty(value = "角色名称")
    @BindRepo(entity = AcRole.class, matchField = "roleId", extractField = "roleName", sourceField = "roleId")
    private String roleName;

    @PortalNameField
    @TableField(value = "title")
    @ApiModelProperty(value = "显示名称")
    private String title;

    @PortalDisplayOnlyField
    @PortalDictField(AllPortalDict.class)
    @ApiModelProperty(value = "实体id")
    private Long portalId;

    @BindRepo(entity = SysPortal.class, matchField = "roleId", matchField2 = "id",
            sourceField = "roleId", sourceField2 = "portalId", extractField = "name")
    @ApiModelProperty(value = "本实体英文名称")
    private String portalName;

    @PortalDictField(BindTypeDict.class)
    @ApiModelProperty(value = "实体关系")
    private String bindType;

    @PortalDictField(AllPortalDict.class)
    @ApiModelProperty(value = "目标实体")
    private Long bindPortalId;

    @BindRepo(entity = SysPortal.class, matchField = "roleId", matchField2 = "id",
            sourceField = "roleId", sourceField2 = "bindPortalId", extractField = "name")
    @ApiModelProperty(value = "目标实体英文名称")
    private String bindPortalName;


    @ApiModelProperty(value = "关联字段")
    private String bindProperty;

    @BindRepo(entity = SysPortalColumn.class, matchField = "portalId", matchField2 = "property",
            sourceField = "portalId", sourceField2 = "bindProperty", extractField = "displayName")
    @ApiModelProperty(value = "关联字段名")
    private String bindPropertyName;

    @ApiModelProperty(value = "默认排序字段")
    private String bindSortProperty;

    @BindRepo(entity = SysPortalColumn.class, matchField = "portalId", matchField2 = "id",
            sourceField = "portalId", sourceField2 = "bindProperty", extractField = "displayName")
    @ApiModelProperty(value = "默认排序字段名")
    private String bindSortPropertyName;

    @PortalDictField(PortalSortDict.class)
    @ApiModelProperty(value = "默认排序方式")
    private String bindSortType;

    @PortalDictField(BoolDict.class)
    @ApiModelProperty(value = "树形展示")
    private String treeMode;

    @PortalDictField(BoolDict.class)
    @ApiModelProperty(value = "树形结构显示是否严格节点显示")
    private String treeCheckStrict;

    @PortalOrderField
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;
}
