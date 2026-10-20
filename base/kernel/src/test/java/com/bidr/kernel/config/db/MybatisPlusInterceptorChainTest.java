package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.sql.SQLException;
import java.util.List;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Title: MybatisPlusInterceptorChainTest
 * Description: kernel 拦截器链扩展点离线单测：customizer 注入的拦截器必须位于链 [0]
 * （先于框架内置分页/动态表名）；无 customizer 时链与旧版同形（内置两件）。
 * <p>AppProperties 经反射注入（maxPageSize 只影响分页上限，链序断言无关）；</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
public class MybatisPlusInterceptorChainTest {

    private MybatisPlusConfig newConfig() throws Exception {
        MybatisPlusConfig config = new MybatisPlusConfig();
        java.lang.reflect.Field field = MybatisPlusConfig.class.getDeclaredField("appProperties");
        field.setAccessible(true);
        Class<?> propsClass = field.getType();
        Object props = propsClass.getDeclaredConstructor().newInstance();
        java.lang.reflect.Field size = propsClass.getDeclaredField("maxPageSize");
        size.setAccessible(true);
        size.set(props, 1000L);
        field.set(config, props);
        return config;
    }

    @SuppressWarnings("unchecked")
    private MybatisPlusInterceptor build(MybatisPlusConfig config, InnerInterceptor... custom) throws Exception {
        ObjectProvider<MybatisPlusInterceptorCustomizer> provider =
                new FixedCustomizerProvider(custom);
        return config.mybatisPlusInterceptor(provider);
    }

    /**
     * 固定清单 ObjectProvider 假件：stream/orderedStream/iterator/forEach 全部显式覆写，
     * 不依赖接口默认方法互相委托的实现细节（Spring 5.3 的 orderedStream 默认腿会走
     * 不支持的操作——匿名类只覆写 stream() 会炸 UnsupportedOperationException）。
     */
    private static final class FixedCustomizerProvider implements ObjectProvider<MybatisPlusInterceptorCustomizer> {

        private final MybatisPlusInterceptorCustomizer[] customizers;

        FixedCustomizerProvider(InnerInterceptor... custom) {
            MybatisPlusInterceptorCustomizer combined = interceptor -> {
                for (InnerInterceptor inner : custom) {
                    interceptor.addInnerInterceptor(inner);
                }
            };
            this.customizers = new MybatisPlusInterceptorCustomizer[]{combined};
        }

        @Override
        public MybatisPlusInterceptorCustomizer getObject(Object... args) {
            throw new UnsupportedOperationException("单测假件不走 getObject");
        }

        @Override
        public MybatisPlusInterceptorCustomizer getObject() {
            throw new UnsupportedOperationException("单测假件不走 getObject");
        }

        @Override
        public MybatisPlusInterceptorCustomizer getIfAvailable() {
            return customizers.length > 0 ? customizers[0] : null;
        }

        @Override
        public MybatisPlusInterceptorCustomizer getIfUnique() {
            return getIfAvailable();
        }

        @Override
        public java.util.Iterator<MybatisPlusInterceptorCustomizer> iterator() {
            return java.util.Arrays.asList(customizers).iterator();
        }

        @Override
        public java.util.stream.Stream<MybatisPlusInterceptorCustomizer> stream() {
            return java.util.Arrays.stream(customizers);
        }

        @Override
        public java.util.stream.Stream<MybatisPlusInterceptorCustomizer> orderedStream() {
            return stream();
        }
    }

    @Test
    public void customizerInterceptor_landsAtHead_beforePagination() throws Exception {
        InnerInterceptor probe = new NoopInnerInterceptor();
        MybatisPlusInterceptor interceptor = build(newConfig(), probe);
        List<InnerInterceptor> chain = interceptor.getInterceptors();
        assertEquals("链 [0] 必须是 customizer 注入的拦截器（多租户先于分页）", probe, chain.get(0));
        assertTrue("链 [1] 必须是框架内置分页", chain.get(1) instanceof PaginationInnerInterceptor);
        assertEquals("customizer 一件 + 内置两件", 3, chain.size());
    }

    @Test
    public void noCustomizer_chainUnchanged() throws Exception {
        MybatisPlusInterceptor interceptor = build(newConfig());
        List<InnerInterceptor> chain = interceptor.getInterceptors();
        assertEquals("无 customizer 时链＝内置两件（向后兼容）", 2, chain.size());
        assertTrue(chain.get(0) instanceof PaginationInnerInterceptor);
    }

    /** 探针拦截器（全空实现，只占链位） */
    private static class NoopInnerInterceptor implements InnerInterceptor {
        @Override
        public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
            // noop
        }
    }
}
