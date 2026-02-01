package com.bidr.authorization.vo.department;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.data.copy.Accept;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: DepartmentAccountRes
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/1 10:45
 */
@Data
public class DepartmentAccountRes {
    private Long userId;
    @JsonProperty("value")
    private String customerNumber;
    private String userName;
    @JsonProperty("label")
    private String name;
    @JsonProperty("pictureLink")
    @Accept(name = "avatar")
    private String avatar;

    private Long deptId;

    @BindField(entity = AcUserDept.class, field = "dataScope", condition = "this.deptId = dept_id and this.userId " +
            "= user_id")
    private String dataScope;

    @BindDict(type = "DATA_PERMIT_SCOPE_DICT", field = "dataScope")
    @BindField(entity = AcUserDept.class, field = "dataScope", condition = "this.deptId = dept_id and this.userId " +
            "= user_id")
    private String dataScopeDisplay;
}