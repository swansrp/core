package com.bidr.td.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TdDataSourceConfig {

    @Bean
    public DataSource taosDataSource(TdProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getUrl());
        config.setUsername(props.getUsername());
        config.setPassword(props.getPassword());
        config.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        config.setMaximumPoolSize(props.getPool().getMaxActive());
        config.setMinimumIdle(props.getPool().getInitialSize());
        // Removed setConnectionTestQuery - HikariCP uses JDBC 4.0 isValid() automatically
        config.setPoolName("TaosPool");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate taosJdbcTemplate(DataSource taosDataSource) {
        return new JdbcTemplate(taosDataSource);
    }
}
