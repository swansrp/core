package com.bidr.td.inf;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.LinkedHashMap;
import java.util.List;

public interface TdSchemaInf {
    String getStableName();
    String getCreateStableSql();
    LinkedHashMap<Integer, String> getUpgradeScripts();
    List<String> getInitDataScripts();
    void initStable(JdbcTemplate taosJdbcTemplate);
}
