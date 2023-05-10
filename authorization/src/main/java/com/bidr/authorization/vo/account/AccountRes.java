package com.bidr.authorization.vo.account;

import com.bidr.authorization.dao.entity.AcDept;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.data.copy.Accept;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: AccountRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:11
 */
@Data
public class AccountRes {
    @JsonProperty("value")
    @Accept(name = "userId")
    private String id;
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
