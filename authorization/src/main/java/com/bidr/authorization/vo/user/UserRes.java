package com.bidr.authorization.vo.user;

import com.bidr.authorization.dao.entity.AcDept;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.data.copy.Accept;
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
    @BindField(entity = AcDept.class, field = "name", condition = "this.department = dept_id")
    private String deptName;
    @Accept(name = "avatar")
    private String pictureLink;
}
