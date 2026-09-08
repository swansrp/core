package com.bidr.authorization.vo.group;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.kernel.config.response.Accept;
import com.bidr.kernel.config.response.BindDict;
import com.bidr.kernel.config.response.BindRepo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: GroupAccountRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 16:25
 */
@Data
public class GroupAccountRes {
    private Long userId;
    @JsonProperty("value")
    private String customerNumber;
    private String userName;
    @JsonProperty("label")
    private String name;
    @JsonProperty("department")
    @Accept(name = "deptId")
    private String deptId;
    @BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "deptId")
    private String deptName;
    @JsonProperty("pictureLink")
    @Accept(name = "avatar")
    private String avatar;

    private Long groupId;

    @BindRepo(entity = AcUserGroup.class, matchField = "groupId", matchField2 = "userId",
            sourceField = "groupId", sourceField2 = "userId")
    private String dataScope;

    @BindDict(type = "DATA_PERMIT_SCOPE_DICT", field = "dataScope")
    @BindRepo(entity = AcUserGroup.class, matchField = "groupId", matchField2 = "userId",
            sourceField = "groupId", sourceField2 = "userId", extractField = "dataScope")
    private String dataScopeDisplay;

}
