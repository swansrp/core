package com.bidr.authorization.vo.permit;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: PermitTreeRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermitTreeRes extends PermitTreeItem {
    private List<PermitTreeItem> children;

    public void PermitTreeItem() {
        children = new ArrayList<>();
    }
}
