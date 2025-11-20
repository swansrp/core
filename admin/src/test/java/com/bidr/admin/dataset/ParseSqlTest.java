package com.bidr.admin.dataset;

import com.bidr.admin.service.PortalDatasetService;
import com.bidr.kernel.test.BaseTest;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.platform.AdminApplication;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: ParseSqlTest
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 13:59
 */

@Test
@SpringBootTest(classes = AdminApplication.class)
public class ParseSqlTest extends BaseTest {
    @Resource
    private PortalDatasetService portalDatasetService;

    public void replaceConfigTest() throws JSQLParserException {
        String sql = "SELECT\n" +
                "  dim_om_project_dyf.project_name AS projectName,\n" +
                "  dim_om_project_dyf.area1_code AS area,\n" +
                "  dim_om_project_dyf.bprov_code AS province,\n" +
                "  dim_om_project_dyf.manage_level AS manageLevel,\n" +
                "  dim_om_project_dyf.business_plate AS plate,\n" +
                "  dim_om_project_dyf.manage_mode AS manageMode,\n" +
                "  dim_om_project_dyf.owner_user_name AS ownerUser,\n" +
                "  dim_om_project_dyf.busi_entity2_id AS omEntity,\n" +
                "  dim_om_project_dyf.area_leader_name AS areaLeader,\n" +
                "  dim_om_project_dyf.manager_user_name AS managerUser,\n" +
                "  dim_om_project_dyf.tradi_ptlead_code AS traditionalLeadDept,\n" +
                "  dim_om_project_dyf.total_ptlead_code AS generalLeadDept,\n" +
                "  dim_om_customer_dyf.customer_id AS customerId,\n" +
                "  dim_om_customer_dyf.customer_name AS customer,\n" +
                "  dim_om_contract_dyf.inner_contract_code AS contractInnerCode,\n" +
                "  dim_om_contract_dyf.contract_confirm_dt AS contractCdt,\n" +
                "  dim_om_project_dyf.project_id AS omProjectId,\n" +
                "  dim_om_project_dyf.manage_stage AS manageStage,\n" +
                "  dim_om_project_dyf.is_important_bid AS isImportantBid,\n" +
                "  dim_om_project_dyf.success_rate AS successRate,\n" +
                "  dim_om_project_dyf.manage_status AS manageStatus,\n" +
                "  t.dy as 'dy',\n" +
                "  t.project_sign_status as 'projectSignStatus',\n" +
                "  t.project_code as 'projectCode'\n" +
                "FROM\n" +
                "  dw_ads.ads_om_project_detail_dyf t\n" +
                "  LEFT JOIN dw_dim.dim_om_project_dyf dim_om_project_dyf ON (\n" +
                "    dim_om_project_dyf.dy = 2025\n" +
                "    AND dim_om_project_dyf.project_code = t.project_code\n" +
                "  )\n" +
                "  LEFT JOIN dw_dim.dim_om_contract_dyf dim_om_contract_dyf ON (\n" +
                "    t.dy = dim_om_contract_dyf.dy\n" +
                "    AND dim_om_project_dyf.project_code = dim_om_contract_dyf.project_code\n" +
                "  )\n" +
                "  LEFT JOIN dw_dim.dim_om_customer_dyf dim_om_customer_dyf ON (\n" +
                "    dim_om_customer_dyf.dy = dim_om_project_dyf.dy\n" +
                "    AND dim_om_project_dyf.customer_no = dim_om_customer_dyf.customer_no\n" +
                "  )\n" +
                "WHERE\n" +
                "  ((1 = 1))";
        portalDatasetService.replaceConfig(sql, "abd");
        log(portalDatasetService.getSql("abd"));
    }

    private AdvancedQuery getAdvancedQueryReq(Integer relation, String property, String value) {
        AdvancedQuery condition = new AdvancedQuery();
        condition.setProperty(property);
        condition.setRelation(relation);
        condition.setValue(Collections.singletonList(value));
        return condition;
    }

    private AdvancedQuery getAdvancedQueryReq(String relation, AdvancedQuery... query) {
        AdvancedQuery req = new AdvancedQuery();
        req.setAndOr(relation);
        req.setConditionList(Arrays.asList(query));
        return req;
    }

    public void queryTest() {
        AdvancedQuery aa = getAdvancedQueryReq(1, "dictValue", "AA");
        AdvancedQuery bb = getAdvancedQueryReq(2, "dictItem", "BB");
        AdvancedQuery cc = getAdvancedQueryReq(3, "dictName", "CC");
        AdvancedQuery dd = getAdvancedQueryReq(4, "dictLabel", "DD");
        AdvancedQuery a = getAdvancedQueryReq("1", aa, bb);
        AdvancedQuery b = getAdvancedQueryReq("1", cc, dd);
        AdvancedQuery c = getAdvancedQueryReq(5, "status", "CC");
        AdvancedQuery d = getAdvancedQueryReq("0", a, b);
        AdvancedQuery query = getAdvancedQueryReq("1", d, c);
        AdvancedQueryReq req = new AdvancedQueryReq();
        req.setCondition(query);
        req.setSortList(Collections.singletonList(new SortVO("dictLabel", 0)));
        portalDatasetService.advancedSelect(req, "abd");
    }
}