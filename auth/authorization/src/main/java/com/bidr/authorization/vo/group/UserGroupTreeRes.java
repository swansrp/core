package com.bidr.authorization.vo.group;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: UserGroupTreeRes
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 18:05
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserGroupTreeRes extends UserGroupTreeItem {
    private List<UserGroupTreeRes> children;

    public UserGroupTreeRes() {
        children = new ArrayList<>();
    }
}
