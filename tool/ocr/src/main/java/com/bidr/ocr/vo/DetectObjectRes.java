package com.bidr.ocr.vo;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.output.DetectedObjects;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Title: DetectObjectRes
 * Description: Copyright: Copyright (c) 2022 Company: BIDR Ltd.
 *
 * @author Sharp
 * @since 2024/4/11 11:13
 */


@Data
public class DetectObjectRes {
    private String className;
    private Double probability;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private Map<String, Object> data;

    public DetectObjectRes(Classifications.Classification item) {
        if (!(item instanceof DetectedObjects.DetectedObject)) {
            throw new IllegalArgumentException("item is not DetectedObject");
        }

        DetectedObjects.DetectedObject i = (DetectedObjects.DetectedObject) item;

        this.className = i.getClassName();
        this.probability = i.getProbability();
        this.x = i.getBoundingBox().getBounds().getX();
        this.y = i.getBoundingBox().getBounds().getY();
        this.width = i.getBoundingBox().getBounds().getWidth();
        this.height = i.getBoundingBox().getBounds().getHeight();
    }

    public Map<String, Object> getData() {
        if (isNull(data)) {
            data = new HashMap<>();
        }
        return data;
    }
}
