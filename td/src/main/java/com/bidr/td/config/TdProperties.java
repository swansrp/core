package com.bidr.td.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "td")
public class TdProperties {
    private String url = "jdbc:TAOS-RS://localhost:6041/water_guard";
    private String username = "root";
    private String password = "taosdata";
    private Pool pool = new Pool();
    private String database = "water_guard";
    private String precision = "ms";
    private int keepDays = 3650;
    private Schema schema = new Schema();

    @Data
    public static class Pool {
        private int initialSize = 5;
        private int maxActive = 20;
    }

    @Data
    public static class Schema {
        private boolean autoDrop = false;
    }
}
