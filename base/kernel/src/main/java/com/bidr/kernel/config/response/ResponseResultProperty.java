package com.bidr.kernel.config.response;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties("bidr.result")
public class ResponseResultProperty {
    private String formatBean;
    private List<String> classWhiteList;
}
