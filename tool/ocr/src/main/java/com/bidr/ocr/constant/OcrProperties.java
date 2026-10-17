package com.bidr.ocr.constant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OCR配置
 *
 * @author 别动我
 * @since 2022/3/16 22:07
 */
@Data
@ConfigurationProperties(prefix = "ai.ocr")
@Configuration
public class OcrProperties {

    private double rotateThreshold;
    private String detectUrl;
    private String detectQuickUrl;
    private String recognizeUrl;
    private String recognizeQuickUrl;
    private String rotateUrl;
    private String deviceType;
}
