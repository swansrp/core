package com.bidr.qcc.service;

import com.bidr.qcc.dto.credit.CreditCodeReq;
import com.bidr.qcc.dto.credit.CreditCodeRes;
import com.bidr.qcc.dto.enterprise.EnterpriseAdvancedReq;
import com.bidr.qcc.dto.enterprise.EnterpriseAdvancedRes;
import com.bidr.qcc.dto.enterprise.EnterpriseReq;
import com.bidr.qcc.dto.enterprise.EnterpriseRes;
import com.bidr.qcc.dto.name.NameSearchReq;
import com.bidr.qcc.dto.name.NameSearchRes;

/**
 * Title: QccService
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 13:46
 */

public interface QccService {
    /**
     * 0.01元/次
     * 1027
     * 企业搜索
     * 通过企业名称模糊搜索匹配企业，展示前5条记录，返回企业名称、匹配原因等信息。
     *
     * @param req 企业名称（模糊匹配）
     * @return 数据信息
     */
    NameSearchRes enterpriseSearch(NameSearchReq req);

    /**
     * 886 0.10元/次
     * 企业高级搜索
     * 通过搜索关键字（如企业名、人名、产品名、地址、电话、经营范围等）获取匹配搜索条件的企业列表信息，返回包括但不限于企业名称、法定代表人名称、企业状态、成立日期、统一社会信用代码、注册号等信息。
     *
     * @param req 搜索关键字（如企业名、人名、产品名、地址、电话、经营范围等）
     * @return 企业基础信息
     */
    EnterpriseRes getEnterpriseInfo(EnterpriseReq req);

    /**
     * 410 0.20元/次
     * 企业工商照面
     * 实时查询企业工商照面信息，返回企业名称、企业类型、注册资本、统一社会信用代码、经营范围、营业期限、上市状态等信息。
     *
     * @param req 关键字（企业名称、统一社会信用代码、注册号）注：社会组织、中国香港企业仅支持通过企业名称查询")
     * @return 企业工商照面
     */
    EnterpriseAdvancedRes getEnterpriseAdvancedInfo(EnterpriseAdvancedReq req);

    /**
     * 271 0.20元/次
     * 税号开票信息
     * 企业税务登记号查询、纳税人识别号、企业名称、企业类型、地址、联系电话、开户行、开户行账号
     *
     * @param req 查询关键字（公司名称、注册号）
     * @return 开票信息
     */
    CreditCodeRes getCreditCode(CreditCodeReq req);

}
