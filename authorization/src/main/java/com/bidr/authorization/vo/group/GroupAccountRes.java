package com.bidr.authorization.vo.group;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.vo.account.AccountRes;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.data.copy.Accept;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: GroupAccountRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 16:25
 */
@Data
public class GroupAccountRes {

    @JsonProperty("value")
    private String userId;
    private String userName;
    @JsonProperty("label")
    private String name;
    @JsonProperty("department")
    @Accept(name = "deptId")
    private String deptId;
    @BindField(entity = AcDept.class, field = "name", condition = "this.deptId = dept_id")
    private String deptName;
    @JsonProperty("pictureLink")
    @Accept(name = "avatar")
    private String avatar;

    private Long groupId;

    @BindField(entity = AcUserGroup.class, field = "dataScope", condition = "this.groupId = group_id and this.userId = user_id")
    private String dataScope;

    @BindDict(type = "DATA_PERMIT_SCOPE_DICT", field = "dataScope")
    @BindField(entity = AcUserGroup.class, field = "dataScope", condition = "this.groupId = group_id and this.userId = user_id")
    private String dataScopeDisplay;

}
