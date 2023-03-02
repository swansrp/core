package com.bidr.kernel.config.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 系统配置属性
 * @author zong_b
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    /**
     * 数据库分页时，最大的页大小
     */
    private Long maxPageSize ;
}
