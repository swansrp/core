package com.bidr.td.config;

import com.bidr.td.inf.TdSchemaInf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TDengine 超级表 Schema 初始化器
 * 在所有 Spring Bean 初始化完成后，自动扫描所有 TdSchemaInf 实现，
 * 调用 initStable() 确保超级表结构最新。
 *
 * @author Sharp
 */
@Component
public class TdSchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TdSchemaInitializer.class);

    private final JdbcTemplate taosJdbcTemplate;
    private final List<TdSchemaInf> schemaList;

    public TdSchemaInitializer(@Qualifier("taosJdbcTemplate") JdbcTemplate taosJdbcTemplate,
                               List<TdSchemaInf> schemaList) {
        this.taosJdbcTemplate = taosJdbcTemplate;
        this.schemaList = schemaList;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (schemaList == null || schemaList.isEmpty()) {
            log.info("未发现 TdSchemaInf 实现，跳过 TD 超级表初始化");
            return;
        }
        log.info("开始初始化 {} 个 TD 超级表 Schema...", schemaList.size());
        for (TdSchemaInf schema : schemaList) {
            try {
                String stableName = schema.getStableName();
                log.info("初始化 TD 超级表: {}", stableName);
                schema.initStable(taosJdbcTemplate);
                log.info("TD 超级表 {} 初始化完成", stableName);
            } catch (Exception e) {
                log.error("TD 超级表 {} 初始化失败: {}", schema.getStableName(), e.getMessage(), e);
                throw new RuntimeException("TD 超级表 " + schema.getStableName() + " 初始化失败", e);
            }
        }
        log.info("所有 TD 超级表 Schema 初始化完成");
    }
}
