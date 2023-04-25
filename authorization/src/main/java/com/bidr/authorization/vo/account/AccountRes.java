package com.bidr.authorization.vo.account;

import com.bidr.authorization.dao.entity.AcDept;
import com.diboot.core.binding.annotation.BindField;
import lombok.Data;

/**
 * Title: AccountRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/23 15:11
 */
@Data
public class AccountRes {
    private String id;
    private String userName;
    private String name;
    private String department;
    @BindField(entity = AcDept.class, field = "name", condition = "this.department = dept_id")
    private String deptName;
    private String pictureLink;
}
