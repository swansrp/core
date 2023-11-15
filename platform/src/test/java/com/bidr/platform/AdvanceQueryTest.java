package com.bidr.platform;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.test.BaseTest;
import com.bidr.kernel.vo.portal.AdvanceQuery;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.repository.SysDictService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * Title: AdvanceQueryTest
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 15:25
 */
@Test
@SpringBootTest(classes = MiniApplication.class)
public class AdvanceQueryTest extends BaseTest {
    @Resource
    private SysDictService sysDictService;

    public void advanceQueryTest() {
        AdvanceQuery aa = getAdvanceQueryReq(1, "dictValue", "AA");
        AdvanceQuery bb = getAdvanceQueryReq(2, "dictItem", "BB");
        AdvanceQuery cc = getAdvanceQueryReq(3, "dictName", "CC");
        AdvanceQuery dd = getAdvanceQueryReq(4, "dictLabel", "DD");
        AdvanceQuery a = getAdvanceQueryReq("1", aa, bb);
        AdvanceQuery b = getAdvanceQueryReq("1", cc, dd);
        AdvanceQuery c = getAdvanceQueryReq(5, "status", "CC");
        AdvanceQuery d = getAdvanceQueryReq("0", a, b);
        AdvanceQuery req = getAdvanceQueryReq("1", d, c);
        QueryWrapper<SysDict> wrapper = new QueryWrapper<>(new SysDict());
        sysDictService.parseAdvanceQuery(req, wrapper);
        log(wrapper.getTargetSql());

    }

    @NotNull
    private static AdvanceQuery getAdvanceQueryReq(Integer relation, String property, String value) {
        AdvanceQuery condition = new AdvanceQuery();
        condition.setProperty(property);
        condition.setRelation(relation);
        condition.setValue(Collections.singletonList(value));
        return condition;
    }

    @NotNull
    private static AdvanceQuery getAdvanceQueryReq(String relation, AdvanceQuery aa, AdvanceQuery bb) {
        AdvanceQuery req = new AdvanceQuery();
        req.setAndOr(relation);
        req.setConditionA(aa);
        req.setConditionB(bb);
        return req;
    }

}
