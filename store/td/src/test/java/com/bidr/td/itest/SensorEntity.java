package com.bidr.td.itest;

import com.bidr.td.annotation.*;
import com.bidr.td.constant.TdDataType;

@TdStable("itest_sensor")
public class SensorEntity {

    @TdTimestamp
    private Long ts;

    @TdColumn(name = "temperature", type = TdDataType.DOUBLE)
    private Double temperature;

    @TdColumn(name = "humidity", type = TdDataType.DOUBLE)
    private Double humidity;

    @TdColumn(name = "status", type = TdDataType.INT)
    private Integer status;

    @TdTag(name = "device_id", type = TdDataType.BINARY, length = 64)
    private String deviceId;

    @TdTag(name = "location", type = TdDataType.NCHAR, length = 128)
    private String location;

    public SensorEntity() {
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
