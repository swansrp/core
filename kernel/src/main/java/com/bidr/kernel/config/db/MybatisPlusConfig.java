package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * Mybatis Plus 配置
 *
 * @author zong_b
 */
@Configuration
public class MybatisPlusConfig {
    @Resource
    private AppProperties appProperties;

    /**
     * mybatis-plus分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor innerInterceptor = new PaginationInnerInterceptor();
        //限制页大小
        innerInterceptor.setMaxLimit(appProperties.getMaxPageSize());
        interceptor.addInnerInterceptor(innerInterceptor);
        return interceptor;
    }

    @Bean
    public MPJConfig sqlInjector() {
        return new MPJConfig();
    }
}
