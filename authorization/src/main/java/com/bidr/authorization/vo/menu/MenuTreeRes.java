package com.bidr.authorization.vo.menu;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: MenuTreeRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuTreeRes extends MenuTreeItem {
    private List<MenuTreeRes> children;

    public void MenuTreeRes() {
        children = new ArrayList<>();
    }
}
