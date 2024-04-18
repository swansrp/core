package com.bidr.qcc.contants;

/**
 * Title: QiChaChaUrl
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 8:51
 */
public class QiChaChaUrl {
    /**
     * 1027
     * 企业搜索
     * 通过企业名称模糊搜索匹配企业，展示前5条记录，返回企业名称、匹配原因等信息。
     */
    public static final String NAME_SEARCH_URL = "https://api.qichacha.com/NameSearch/GetList";
    /**
     * 886
     * 企业高级搜索
     * 通过搜索关键字（如企业名、人名、产品名、地址、电话、经营范围等）获取匹配搜索条件的企业列表信息，返回包括但不限于企业名称、法定代表人名称、企业状态、成立日期、统一社会信用代码、注册号等信息。
     */
    public static final String ENTERPRISE_INFO_URL = "https://api.qichacha.com/FuzzySearch/GetList";
    /**
     * 271
     * 税号开票信息
     * 企业税务登记号查询、纳税人识别号、企业名称、企业类型、地址、联系电话、开户行、开户行账号
     */
    public static final String CREDIT_INFO_URL = "https://api.qichacha.com/ECICreditCode/GetCreditCodeNew";
    /**
     * 410
     * 企业工商照面
     * 实时查询企业工商照面信息，返回企业名称、企业类型、注册资本、统一社会信用代码、经营范围、营业期限、上市状态等信息。
     */
    public static final String ENTERPRISE_ADVANCED_INFO_URL = "https://api.qichacha.com/ECIV4/GetBasicDetailsByName";
}
