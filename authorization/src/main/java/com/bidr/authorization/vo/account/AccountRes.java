package com.bidr.authorization.vo.account;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.config.response.Accept;
import com.bidr.kernel.config.response.BindRepo;
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
