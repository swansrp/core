package com.bidr.authorization.vo.department;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: DepartmentTreeRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/21 18:28
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepartmentTreeRes extends DepartmentItem {
    private List<DepartmentTreeRes> children;

    public void DepartmentTreeRes() {
        children = new ArrayList<>();
    }
}
