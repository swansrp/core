package com.bidr.authorization.vo.user;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.config.response.Accept;
import com.bidr.kernel.config.response.BindRepo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: UserRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 09:36
 */
@Data
public class UserRes {
    @JsonProperty("value")
    private Long userId;
    private String customerNumber;
    private String userName;
    @JsonProperty("label")
    private String name;
    @Accept(name = "deptId")
    private String department;
    @BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "department")
    private String deptName;
    @Accept(name = "avatar")
    private String pictureLink;
}
