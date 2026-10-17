package com.bidr.td.itest;

import com.bidr.td.repository.BaseTdRepo;
import org.springframework.jdbc.core.JdbcTemplate;

public class SensorRepo extends BaseTdRepo<SensorEntity> {

    public SensorRepo(JdbcTemplate taosJdbcTemplate) {
        super(taosJdbcTemplate, SensorEntity.class);
    }
}
