package com.bidr.authorization.vo.department;

import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.kernel.config.response.Accept;
import com.bidr.kernel.config.response.BindDict;
import com.bidr.kernel.config.response.BindRepo;
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

    @BindRepo(entity = AcUserDept.class, matchField = "deptId", matchField2 = "userId",
            sourceField = "deptId", sourceField2 = "userId")
    private String dataScope;

    @BindDict(type = "DATA_PERMIT_SCOPE_DICT", field = "dataScope")
    @BindRepo(entity = AcUserDept.class, matchField = "deptId", matchField2 = "userId",
            sourceField = "deptId", sourceField2 = "userId", extractField = "dataScope")
    private String dataScopeDisplay;
}