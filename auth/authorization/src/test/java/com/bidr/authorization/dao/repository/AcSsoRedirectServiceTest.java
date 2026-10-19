package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Title: AcSsoRedirectServiceTest
 * Description: 白名单查询口的 {@code valid='1'} 过滤真针（M15 棒 0b-7，review F3）。
 * <p>🔴 它守的是 M-22 红线的另一半：撤销登记＝把行置 {@code valid='0'}，出票与查重都必须过滤掉这些行。
 * 在这一针之前，全系统<b>没有任何自动化守护</b>盯着 {@code validWrapper} 里那条
 * {@code eq(getValid, CommonConst.YES)}——把它删掉，authorization 与 admin 的其余腿全绿
 * （出票侧的用例把 {@code existsValid} 整体 mock 掉了），真实回归无人接住。</p>
 * <p>离线形态＝直接断言 {@code validWrapper} 产出的 wrapper 形态（三条件齐、全为等值、无 LIKE、
 * {@code valid} 绑的是 '1'）；🔴 不打库、不起容器，因此<b>只跑入参短路腿与 wrapper 构造腿</b>——
 * {@code count()} 需要 baseMapper，真调会 NPE，这不是"测试坏了"而是本类的离线边界，写在这里免得后人误加。</p>
 * <p>lambda 列名解析需要 MyBatis-Plus 的 TableInfo 缓存（离线态没有 SqlSessionFactory），
 * 故静态块先用 {@code TableInfoHelper.initTableInfo} 把 {@code AcSsoRedirect} 装进缓存。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Test
public class AcSsoRedirectServiceTest {

    private static final long PARTNER_ID = 9L;
    private static final String REDIRECT_URI = "https://sub.example.com/sso/cb";
    private static final long ROW_ID = 7L;

    static {
        // 离线建 TableInfo 缓存：getSqlSegment() 解析 lambda 列名要用它
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcSsoRedirect.class);
    }

    private long countOccurrences(String text, String token) {
        long count = 0;
        int idx = text.indexOf(token);
        while (idx != -1) {
            count++;
            idx = text.indexOf(token, idx + token.length());
        }
        return count;
    }

    /**
     * 针本体：三条件必须齐全、全部是等值、且 {@code valid} 绑 '1'。
     * <p>删掉 {@code validWrapper} 里 {@code eq(getValid, CommonConst.YES)} 那一行 ⇒ 等值项从 3 变 2、
     * 参数表里不再有 '1' ⇒ 本腿翻红（这就是它区别于"假针"的地方，交付前已用变异自证跑过一次）。</p>
     */
    public void validWrapperCarriesThreeEqualityConditionsWithValidOne() {
        LambdaQueryWrapper<AcSsoRedirect> wrapper = new AcSsoRedirectService().validWrapper(PARTNER_ID, REDIRECT_URI);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("partner_id"), "查询条件必须含 partner_id: " + sql);
        assertTrue(sql.contains("redirect_uri"), "查询条件必须含 redirect_uri: " + sql);
        assertTrue(sql.contains("valid"), "查询条件必须含 valid 过滤: " + sql);
        assertEquals(countOccurrences(sql, "="), 3L, "三条件必须全是等值（出现 like/前缀匹配即命中判据被放宽）: " + sql);
        assertFalse(sql.toUpperCase().contains("LIKE"), "白名单命中判定禁止 like: " + sql);
        List<Object> values = new ArrayList<>(wrapper.getParamNameValuePairs().values());
        assertTrue(values.contains(PARTNER_ID), "参数表必须带 partner_id 值: " + values);
        assertTrue(values.contains(REDIRECT_URI), "参数表必须带 redirect_uri 原串: " + values);
        assertTrue(values.contains("1"), "valid 必须绑 '1'（CommonConst.YES）＝只看未撤销的登记: " + values);
    }

    /**
     * 排除自身只多一个 {@code <>} 条件，三条件本体不得被改写。
     * <p>🔴 本腿取的是<b>生产构造</b> {@code validWrapperExcludingId}（{@code existsValidExcludingId} 的唯一入口），
     * 不是在这里重搭一遍 {@code .ne(...)}——重搭的话把生产方法里那一行删掉本腿仍绿，等于编辑口查重无针（10-14 review R2）。
     * 变异自证＝删掉 {@code AcSsoRedirectService.validWrapperExcludingId} 的 {@code .ne(...)} ⇒ 本腿的
     * {@code <>} 与"参数表含 row id"两条断言同时翻红。</p>
     */
    public void existsValidExcludingIdAddsSelfExclusionOnly() {
        LambdaQueryWrapper<AcSsoRedirect> wrapper =
                new AcSsoRedirectService().validWrapperExcludingId(PARTNER_ID, REDIRECT_URI, ROW_ID);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("partner_id") && sql.contains("redirect_uri") && sql.contains("valid"),
                "排除自身后三条件仍在: " + sql);
        assertEquals(countOccurrences(sql, "="), 3L, "仍只有三个等值项: " + sql);
        assertTrue(sql.contains("<>"), "必须带 id 的排除条件: " + sql);
        List<Object> values = new ArrayList<>(wrapper.getParamNameValuePairs().values());
        assertTrue(values.contains("1"), "排除自身不得把 valid 过滤一起改掉: " + values);
        assertTrue(values.contains(ROW_ID), "参数表必须带被排除的自身 id: " + values);
    }

    /**
     * 入参缺席即拒方向：出票判据不许因空入参退化成"查全部"。
     */
    public void nullInputsShortCircuitToFalse() {
        AcSsoRedirectService repository = new AcSsoRedirectService();
        assertFalse(repository.existsValid(null, REDIRECT_URI), "partnerId 缺席必须判未命中");
        assertFalse(repository.existsValid(PARTNER_ID, null), "redirectUri 缺席必须判未命中");
        assertFalse(repository.existsValidExcludingId(null, REDIRECT_URI, ROW_ID), "partnerId 缺席必须判未命中");
        assertFalse(repository.existsValidExcludingId(PARTNER_ID, REDIRECT_URI, null), "excludeId 缺席必须判未命中");
    }
}
