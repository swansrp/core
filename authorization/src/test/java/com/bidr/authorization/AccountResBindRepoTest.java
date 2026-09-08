package com.bidr.authorization;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.config.response.BindRepoHandler;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

/**
 * Title: AccountResBindRepoTest
 * Description: {@code @BindRepo} 运行时链路验证（2026-09-08，diboot 删除后的自研绑定回归）。
 * <p>
 * 验证 AccountRes.deptName 上
 * {@code @BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "department")}
 * 在 Resp.convert 中的完整接力：
 * <pre>
 * copy(同名) → acceptConvert(@Accept 填充 department 关联键) → customBindConvert(@BindRepo 批量关联) → 回填
 * </pre>
 * 即：先由 @Accept(name="deptId") 把 AcUser.deptId 填入 VO.department，
 * 再以 department 关联 ac_dept.dept_id 批量取出 name 回填 deptName。
 * <p>
 * 纯单测：mock WebApplicationContext 注入 BeanUtil，mock AcDeptService 数据源，不连数据库。
 *
 * @author Sharp
 * @since 2026/09/08
 */
public class AccountResBindRepoTest {

    private static AcDeptService acDeptService;

    @BeforeClass
    public static void setUp() {
        acDeptService = Mockito.mock(AcDeptService.class);
        Mockito.when(acDeptService.getEntityClass()).thenReturn(AcDept.class);
        Mockito.when(acDeptService.select(Mockito.any(Wrapper.class))).thenAnswer(invocation ->
                Arrays.asList(dept("D100", "研发部"), dept("D200", "交付部")));

        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForType(BindRepoHandler.class))
                .thenReturn(new String[]{"bindRepoHandler"});
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class))
                .thenReturn(new String[]{"acDeptService"});
        Mockito.when(ctx.getBean("bindRepoHandler")).thenReturn(new BindRepoHandler());
        Mockito.when(ctx.getBean("acDeptService")).thenReturn(acDeptService);
        BeanUtil.setContext(ctx);
    }

    private static AcDept dept(String deptId, String name) {
        AcDept dept = new AcDept();
        dept.setDeptId(deptId);
        dept.setName(name);
        dept.setValid("1");
        return dept;
    }

    private static AcUser user(Long userId, String deptId) {
        AcUser user = new AcUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setUserName("zhangsan");
        user.setName("张三");
        user.setAvatar("http://a.b/c.png");
        return user;
    }

    /**
     * 单实体：@Accept 跨型复制（Long userId → String id）、@BindRepo 以 department 关联出 deptName
     */
    @Test
    public void singleConvertFillsAcceptAndBindRepo() {
        AccountRes res = Resp.convert(user(9001L, "D100"), AccountRes.class);

        Assert.assertEquals("9001", res.getId());
        Assert.assertEquals("D100", res.getDepartment());
        Assert.assertEquals("研发部", res.getDeptName());
        Assert.assertEquals("http://a.b/c.png", res.getPictureLink());
        Assert.assertEquals("张三", res.getName());
        Assert.assertEquals("zhangsan", res.getUserName());
    }

    /**
     * 列表：两个不同部门的账号只触发一次批量 IN 查询（防 N+1）
     */
    @Test
    public void listConvertBatchesRepoQuery() {
        // 清除单实体测试产生的调用记录（保留 stub），使次数断言从零开始
        Mockito.clearInvocations(acDeptService);

        List<AccountRes> resList = Resp.convert(
                Arrays.asList(user(9001L, "D100"), user(9002L, "D200")), AccountRes.class);

        Assert.assertEquals(2, resList.size());
        Assert.assertEquals("研发部", resList.get(0).getDeptName());
        Assert.assertEquals("交付部", resList.get(1).getDeptName());
        Mockito.verify(acDeptService, Mockito.times(1)).select(Mockito.any(Wrapper.class));
    }

    /**
     * 无匹配：department 在 ac_dept 中不存在时 deptName 保持 null，不报错
     */
    @Test
    public void convertWithUnknownDeptKeepsNull() {
        AccountRes res = Resp.convert(user(9003L, "D404"), AccountRes.class);

        Assert.assertEquals("D404", res.getDepartment());
        Assert.assertNull(res.getDeptName());
    }
}
