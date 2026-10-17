package com.bidr.authorization;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.vo.group.GroupAccountRes;
import com.bidr.kernel.config.response.BindRepoHandler;
import com.bidr.kernel.config.response.DictBinder;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.config.response.RespConvert;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: GroupAccountResDataScopeBindTest
 * Description: 关联用户 dataScope 绑定回归（2026-10-15 修复「已绑定用户显示 -0」）。
 * <p>
 * 守卫 {@link GroupAccountRes} 上 dataScope 的
 * {@code @BindRepo(..., extractField = "dataScope")}：Resp.convert 管线中
 * customBindConvert(@BindRepo) 先回填 dataScope 原始码，dictBindConvert(@BindDict)
 * 再据 dataScope 翻译回填 dataScopeDisplay。extractField 缺失时 dataScope 为 null
 * （默认取 AcUserGroup.name），字典翻译空转，dataScopeDisplay 只剩裸码。
 * <p>
 * 纯单测：mock AcUserGroupService 与 DictBinder SPI，不连数据库。
 *
 * @author Sharp
 * @since 2026-10-15
 */
public class GroupAccountResDataScopeBindTest {

    private static final Map<String, String> DICT = new HashMap<>();

    static {
        DICT.put("0", "本部门");
        DICT.put("2", "本部门及子部门");
    }

    private static AcUserGroupService acUserGroupService;
    private static DictBinder dictBinder;

    @BeforeClass
    public static void setUp() throws Exception {
        resetRespConvertCaches();

        acUserGroupService = Mockito.mock(AcUserGroupService.class);
        Mockito.when(acUserGroupService.getEntityClass()).thenReturn(AcUserGroup.class);
        Mockito.when(acUserGroupService.select(Mockito.any(Wrapper.class))).thenAnswer(invocation ->
                Arrays.asList(bind(81L, 16L, 0), bind(81L, 18L, 2)));

        dictBinder = Mockito.mock(DictBinder.class);
        Mockito.doAnswer(invocation -> {
            List<?> voList = invocation.getArgument(0);
            String setFieldName = invocation.getArgument(1);
            String getFieldName = invocation.getArgument(2);
            for (Object vo : voList) {
                Object value = ReflectionUtil.getValue(vo, getFieldName, Object.class);
                String label = value == null ? null : DICT.get(String.valueOf(value));
                if (label != null) {
                    ReflectionUtil.setFieldValue(vo, setFieldName, label);
                }
            }
            return null;
        }).when(dictBinder).bindItemLabel(Mockito.anyList(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());

        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForType(BindRepoHandler.class))
                .thenReturn(new String[]{"bindRepoHandler"});
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class))
                .thenReturn(new String[]{"acUserGroupService"});
        Mockito.when(ctx.getBeanNamesForType(DictBinder.class))
                .thenReturn(new String[]{"dictBinder"});
        Mockito.when(ctx.getBean("bindRepoHandler")).thenReturn(new BindRepoHandler());
        Mockito.when(ctx.getBean("acUserGroupService")).thenReturn(acUserGroupService);
        Mockito.when(ctx.getBean("dictBinder")).thenReturn(dictBinder);
        BeanUtil.setContext(ctx);
    }

    /**
     * RespConvert 缓存 handler/binder 与 BindRepoHandler 缓存 repoIndex 均为静态/实例级，
     * 与同模块其他绑定测试必须各自重建上下文，先清空缓存避免跨类污染
     */
    static void resetRespConvertCaches() throws Exception {
        for (String name : new String[]{"cachedHandler", "cachedDictBinder"}) {
            Field field = RespConvert.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, null);
        }
        for (String name : new String[]{"handlerInitialized", "dictBinderInitialized"}) {
            Field field = RespConvert.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, Boolean.FALSE);
        }
    }

    private static AcUserGroup bind(Long groupId, Long userId, Integer dataScope) {
        AcUserGroup relation = new AcUserGroup();
        relation.setGroupId(groupId);
        relation.setUserId(userId);
        relation.setDataScope(dataScope);
        return relation;
    }

    /**
     * 源即 join 出的 GroupAccountRes（dataScope 未填），复刻 searchBindList → Resp.convert 链路
     */
    private static GroupAccountRes source(Long userId, String name) {
        GroupAccountRes res = new GroupAccountRes();
        res.setUserId(userId);
        res.setGroupId(81L);
        res.setName(name);
        res.setCustomerNumber("19790" + userId);
        return res;
    }

    @Test
    public void dataScopeFilledAndDisplayTranslated() {
        List<GroupAccountRes> res = Resp.convert(
                Arrays.asList(source(16L, "李东强"), source(18L, "赵建玲")), GroupAccountRes.class);

        Assert.assertEquals("0", res.get(0).getDataScope());
        Assert.assertEquals("本部门", res.get(0).getDataScopeDisplay());
        Assert.assertEquals("2", res.get(1).getDataScope());
        Assert.assertEquals("本部门及子部门", res.get(1).getDataScopeDisplay());
    }

    /**
     * @BindDict 契约腿：翻译源字段必须是 dataScope（而非字段同名回退），字典类型不变
     */
    @Test
    public void dictBinderReadsDataScopeField() {
        Resp.convert(Arrays.asList(source(16L, "李东强")), GroupAccountRes.class);

        ArgumentCaptor<String> setField = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> getField = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        Mockito.verify(dictBinder, Mockito.atLeastOnce())
                .bindItemLabel(Mockito.anyList(), setField.capture(), getField.capture(), type.capture());
        Assert.assertEquals("dataScopeDisplay", setField.getValue());
        Assert.assertEquals("dataScope", getField.getValue());
        Assert.assertEquals("DATA_PERMIT_SCOPE_DICT", type.getValue());
    }

    /**
     * 无绑定关系：dataScope/display 保持 null，不抛错（前端据此显示空权限）
     */
    @Test
    public void unboundUserKeepsNull() {
        GroupAccountRes res = Resp.convert(Arrays.asList(source(9999L, "查无此人")), GroupAccountRes.class).get(0);

        Assert.assertNull(res.getDataScope());
        Assert.assertNull(res.getDataScopeDisplay());
    }
}
