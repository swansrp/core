package com.bidr.kernel.constant.dict.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: ApprovalDict
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/1/9 15:57
 */

@Getter
@RequiredArgsConstructor
@MetaDict(value = "APPROVAL_DICT", remark = "申请状态字典")
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
public enum ApprovalDict implements Dict {

    UNKNOWN("0", "未提交"),
    APPLY("1", "待审核"),
    REJECT("2", "未通过"),
    APPROVAL("3", "已通过");

    private final String value;
    private final String label;
}
