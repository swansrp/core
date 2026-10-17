package com.bidr.qcc.contants.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: QccEnterTypeDict
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 11:09
 */
@RequiredArgsConstructor
@Getter
@MetaDict(value = "QCC_ENTERPRISE_TYPE_DICT", remark = "企查查企业类型字典")
public enum QccEnterpriseTypeDict implements Dict {
    /**
     *
     */
    MAINLAND_COMPANY("0", "大陆企业"),
    SOCIAL_ORGANIZATION("1", "社会组织"),
    HONGKONG_COMPANY("3", "中国香港公司"),
    PUBLIC_INSTITUTION("4", "事业单位"),
    TAIWAN_COMPANY("5", "中国台湾公司"),
    FOUNDATION("6", "基金会"),
    HOSPITAL("7", "医院"),
    OVERSEAS_COMPANY("8", "海外公司"),
    LAW_FIRM("9", "律师事务所"),
    SCHOOL("10", "学校"),
    GOVERNMENT_AGENCY("11", "机关单位"),
    OTHER("-1", "其他");

    private final String value;
    private final String label;

}
