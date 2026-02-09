package com.bidr.kernel.config.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.TimeZone;

/**
 * Title: JacksonConfig
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/9 21:45
 */

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        configObjectMapper(mapper);
        return mapper;
    }

    public static void configObjectMapper(ObjectMapper mapper) {
        // 1️⃣ 东八区
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 2️⃣ 不使用时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3️⃣ Java8 时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 注册多格式反序列化器
        javaTimeModule.addDeserializer(java.util.Date.class, new AutoDateDeserializer());
        javaTimeModule.addDeserializer(java.time.LocalDateTime.class, new MultiFormatLocalDateTimeDeserializer());
        javaTimeModule.addDeserializer(java.time.LocalDate.class, new MultiFormatLocalDateDeserializer());

        mapper.registerModule(javaTimeModule);
    }

}