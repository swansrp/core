package com.bidr.authorization.vo.department;

import com.diboot.core.data.copy.Accept;
import lombok.Data;

/**
 * Title: DepartmentItem
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/21 18:28
 */
@Data
public class DepartmentItem {
    private String id;
    @Accept(name = "deptId")
    private String value;
    @Accept(name = "deptId")
    private String key;
    private String pid;
    @Accept(name = "name")
    private String label;
    @Accept(name = "name")
    private String title;
}
