package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.mapper.AcPartnerHistoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AcPartnerHistoryService#purgeBefore(Date)} 的 SQL 语义守卫（T3.3a 保留期口）。
 *
 * <p>钉两件事：①{@code expired == null} 直接返回 0 且一次库都不碰——无边界条件会把
 * {@code lt} 丢掉退化成整表删除（等保留痕直接清空）；②非空时按 {@code request_at < ?} 真删并回传行数。</p>
 *
 * @author sharp
 * @since 2026-11-03
 */
public class AcPartnerHistoryServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AcPartnerHistory.class);
    }

    private AcPartnerHistoryMapper mapper;
    private AcPartnerHistoryService service;

    @Before
    public void setUp() {
        mapper = mock(AcPartnerHistoryMapper.class);
        service = spy(new AcPartnerHistoryService());
        // super.getBaseMapper() 绕过 spy，必须把 mapper 直接塞进 ServiceImpl 字段
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    public void purgeBeforeNullDoesNothing() {
        Assert.assertEquals(0, service.purgeBefore(null));
        verifyNoInteractions(mapper);
    }

    @Test
    public void purgeBeforeDeletesByRequestAtAndReturnsRows() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(5);

        Assert.assertEquals(5, service.purgeBefore(new Date()));

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        Assert.assertTrue("必须按 request_at 裁：" + sql, sql.contains("request_at"));
        Assert.assertTrue("必须是严格小于边界（删旧的）：" + sql,
                sql.matches("(?s).*request_at\\s*<(?!=)\\s*#\\{.*"));
        verify(mapper, never()).selectList(any());
    }
}