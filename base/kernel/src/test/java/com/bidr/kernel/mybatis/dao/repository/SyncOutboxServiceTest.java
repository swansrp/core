package com.bidr.kernel.mybatis.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.mapper.SyncOutboxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SyncOutboxService} 的 SQL 语义守卫（T3.3a）。
 *
 * <p>钉四件事：①{@code listAfter} 无游标时不拼 id 谓词、有游标时按 {@code id > ?} 且升序、
 * 尾段始终是 {@code LIMIT n}（消费方按"本次最后一条 id"续拉，顺序反了就漏事件）；
 * ②{@code purgeBefore(null)} 直接返回 0 且一次库都不碰（无边界不得退化成清空全表）；
 * ③{@code purgeBefore(date)} 按 {@code occurred_at < ?} 真删并回传行数。</p>
 *
 * <p>离线单测须先 {@code TableInfoHelper.initTableInfo} 装缓存，lambda 列解析才能取到列名
 * （同 {@code AcSsoRedirectServiceTest} 的做法）。</p>
 *
 * @author sharp
 * @since 2026-11-03
 */
public class SyncOutboxServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SyncOutbox.class);
    }

    private SyncOutboxMapper mapper;
    private SyncOutboxService service;

    @Before
    public void setUp() {
        mapper = mock(SyncOutboxMapper.class);
        service = spy(new SyncOutboxService());
        // 🔴 必须把 mapper 塞进 ServiceImpl.baseMapper：purgeBefore 走 super.getBaseMapper()，
        // super 调用绕过 spy，doReturn(...).when(spy) 拦不住（2026-11-03 实测 NPE）
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    public void listAfterWithoutCursorReadsFromHeadInAscendingOrder() {
        service.listAfter(null, 10);

        String sql = capturedSelectSql();
        Assert.assertFalse("无游标时不得拼 id 谓词：" + sql, sql.contains("id >"));
        Assert.assertTrue("必须按 id 升序：" + sql, sql.toUpperCase().contains("ORDER BY") && sql.toUpperCase().contains("ASC"));
        Assert.assertTrue("必须带 LIMIT 且为传入值：" + sql, sql.contains("LIMIT 10"));
    }

    @Test
    public void listAfterZeroOrNegativeCursorReadsFromHead() {
        service.listAfter(0L, 5);
        service.listAfter(-1L, 5);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(2)).selectList(captor.capture());
        for (Wrapper<?> wrapper : captor.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            Assert.assertFalse("cursor<=0 必须从头拉（不得拼 id >）：" + sql, sql.contains("id >"));
        }
    }

    @Test
    public void listAfterWithCursorFiltersByIdGreaterThan() {
        service.listAfter(7L, 5);

        Wrapper<?> wrapper = capturedWrapper();
        String sql = wrapper.getSqlSegment();
        // 🔴 必须是严格大于：写成 >= 会重复投递游标那一行（消费方按最后一条 id 续拉）
        Assert.assertTrue("必须按 id > ? 严格过滤：" + sql, sql.matches("(?s).*\\bid\\s*>\\s*#\\{.*"));
        Assert.assertTrue("游标值 7 必须进参数：" + paramValues(wrapper), paramValues(wrapper).containsValue(7L));
        Assert.assertTrue("必须带 LIMIT 5：" + sql, sql.contains("LIMIT 5"));
    }

    @Test
    public void purgeBeforeNullDoesNothing() {
        Assert.assertEquals(0, service.purgeBefore(null));
        verifyNoInteractions(mapper);
    }

    @Test
    public void purgeBeforeDeletesByOccurredAtAndReturnsRows() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(3);

        Assert.assertEquals(3, service.purgeBefore(new Date()));

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        Assert.assertTrue("必须按 occurred_at 裁：" + sql, sql.contains("occurred_at"));
        // 🔴 必须是严格小于：写成 <= 会把边界那一刻的行也删掉
        Assert.assertTrue("必须是严格小于边界（删旧的）：" + sql,
                sql.matches("(?s).*occurred_at\\s*<(?!=)\\s*#\\{.*"));
        verify(mapper, never()).selectList(any());
    }

    @SuppressWarnings("unchecked")
    private String capturedSelectSql() {
        return capturedWrapper().getSqlSegment();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<?> capturedWrapper() {
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> paramValues(Wrapper<?> wrapper) {
        return ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }
}