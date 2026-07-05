package com.slidtable.slidtab_pro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "environment_data")
public class EnvironmentData extends BaseEntity {

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    private Double temperature;

    private Double humidity;

    private Double light;

    private Double weight;

    private Double smoke;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
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

    public Double getLight() {
        return light;
    }

    public void setLight(Double light) {
        this.light = light;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getSmoke() {
        return smoke;
    }

    public void setSmoke(Double smoke) {
        this.smoke = smoke;
    }
}
