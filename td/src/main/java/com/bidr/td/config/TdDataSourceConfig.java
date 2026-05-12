package com.bidr.td.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;

/**
 * TDengine 数据源配置
 * <p>
 * 注意：不将 taosDataSource 注册为 Spring DataSource Bean，
 * 避免触发 dynamic-datasource-spring-boot-starter 的 @ConditionalOnMissingBean，
 * 导致动态路由数据源（MySQL master/slave）无法被创建。
 * 仅通过 taosJdbcTemplate 暴露 TDengine 访问能力。
 * </p>
 *
 * @author Sharp
 */
@Configuration
public class TdDataSourceConfig {

    private HikariDataSource taosDs;

    @Bean
    public JdbcTemplate taosJdbcTemplate(TdProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getUrl());
        config.setUsername(props.getUsername());
        config.setPassword(props.getPassword());
        config.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        config.setMaximumPoolSize(props.getPool().getMaxActive());
        config.setMinimumIdle(props.getPool().getInitialSize());
        // Removed setConnectionTestQuery - HikariCP uses JDBC 4.0 isValid() automatically
        config.setPoolName("TaosPool");
        taosDs = new HikariDataSource(config);
        return new JdbcTemplate(taosDs);
    }

    @PreDestroy
    public void close() {
        if (taosDs != null && !taosDs.isClosed()) {
            taosDs.close();
        }
    }
}
