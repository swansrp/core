package com.bidr.authorization.vo.permit;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: PermitApplyMenuTreeRes
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/6 22:51
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermitApplyMenuTreeRes extends PermitApplyMenuTreeItem {
    private List<PermitApplyMenuTreeRes> children = new ArrayList<>();
}