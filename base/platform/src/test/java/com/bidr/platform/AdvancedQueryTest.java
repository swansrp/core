package com.bidr.platform;

import com.bidr.kernel.test.BaseTest;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.platform.config.log.DbLogbackCron;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.repository.SysDictService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: AdvancedQueryTest
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 15:25
 */
@Test
@SpringBootTest(classes = MiniApplication.class)
public class AdvancedQueryTest extends BaseTest {
    @Resource
    private SysDictService sysDictService;
    @Resource
    private DbLogbackCron dbLogbackCron;

    @NotNull
    private static AdvancedQuery getAdvancedQueryReq(Integer relation, String property, String value) {
        AdvancedQuery condition = new AdvancedQuery();
        condition.setProperty(property);
        condition.setRelation(relation);
        condition.setValue(Collections.singletonList(value));
        return condition;
    }

    @NotNull
    private static AdvancedQuery getAdvancedQueryReq(String relation, AdvancedQuery... query) {
        AdvancedQuery req = new AdvancedQuery();
        req.setAndOr(relation);
        req.setConditionList(Arrays.asList(query));
        return req;
    }

    public void advancedQueryTest() {
        AdvancedQuery aa = getAdvancedQueryReq(1, "dictValue", "AA");
        AdvancedQuery bb = getAdvancedQueryReq(2, "dictItem", "BB");
        AdvancedQuery cc = getAdvancedQueryReq(3, "dictName", "CC");
        AdvancedQuery dd = getAdvancedQueryReq(4, "dictLabel", "DD");
        AdvancedQuery a = getAdvancedQueryReq("1", aa, bb);
        AdvancedQuery b = getAdvancedQueryReq("1", cc, dd);
        AdvancedQuery c = getAdvancedQueryReq(5, "status", "CC");
        AdvancedQuery d = getAdvancedQueryReq("0", a, b);
        AdvancedQuery req = getAdvancedQueryReq("1", d, c);
        log(req);
        MPJLambdaWrapper<SysDict> wrapper = new MPJLambdaWrapper<>(new SysDict());
        sysDictService.parseAdvancedQuery(req, null, wrapper);
        log(wrapper.getTargetSql());

    }

    public void logServiceTest() {
        dbLogbackCron.logInDbExpired();
    }
}
