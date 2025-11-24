package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Map;

/**
 * Mybatis Plus 配置
 *
 * @author zong_b
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {
    @Resource
    private AppProperties appProperties;
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private DataSource dataSource;

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

    @PostConstruct
    public void initTables() {
        Map<String, MybatisPlusTableInitializerInf> beans =
                applicationContext.getBeansOfType(MybatisPlusTableInitializerInf.class);

        for (MybatisPlusTableInitializerInf initializer : beans.values()) {
            initializer.initTable(dataSource);
        }
    }
}
