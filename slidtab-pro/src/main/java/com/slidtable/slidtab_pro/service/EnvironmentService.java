package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.dto.protocol.SensorData;
import com.slidtable.slidtab_pro.entity.EnvironmentData;
import com.slidtable.slidtab_pro.repository.EnvironmentDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private static final double TEMP_MAX = 35.0;
    private static final double HUMIDITY_MAX = 80.0;
    private static final double HUMIDITY_MIN = 30.0;
    private static final double LIGHT_MAX = 5000.0;
    private static final double WEIGHT_MAX = 50000.0;
    private static final double SMOKE_MAX = 500.0;

    private final EnvironmentDataRepository repository;
    private final AlarmService alarmService;

    public EnvironmentService(EnvironmentDataRepository repository, AlarmService alarmService) {
        this.repository = repository;
        this.alarmService = alarmService;
    }

    public EnvironmentData record(String deviceId, SensorData sensor) {
        log.info("[环境数据录入] deviceId={}, sensor={}", deviceId, sensor);
        EnvironmentData data = new EnvironmentData();
        data.setDeviceId(deviceId);
        data.setRecordedAt(LocalDateTime.now());
        if (sensor != null) {
            data.setTemperature(sensor.temperature());
            data.setHumidity(sensor.humidity());
            data.setLight(sensor.light());
            data.setWeight(sensor.weight());
            data.setSmoke(sensor.smoke());
        }
        EnvironmentData saved = repository.save(data);
        log.info("[环境数据录入] 已保存: id={}, deviceId={}, temp={}, humidity={}, light={}, weight={}, smoke={}",
                saved.getId(), deviceId, saved.getTemperature(), saved.getHumidity(),
                saved.getLight(), saved.getWeight(), saved.getSmoke());
        checkThresholds(deviceId, saved);
        return saved;
    }

    public EnvironmentData latest(String deviceId) {
        EnvironmentData data = repository.findFirstByDeviceIdOrderByRecordedAtDesc(deviceId).orElse(null);
        log.debug("[环境最新] deviceId={} → {}", deviceId, data != null ? data.getId() : "无数据");
        return data;
    }

    public List<EnvironmentData> history(String deviceId, LocalDateTime start, LocalDateTime end) {
        log.info("[环境历史] deviceId={}, start={}, end={}", deviceId, start, end);
        List<EnvironmentData> list = repository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(deviceId, start, end);
        log.info("[环境历史] 命中 {} 条", list.size());
        return list;
    }

    private void checkThresholds(String deviceId, EnvironmentData data) {
        if (data.getTemperature() != null && data.getTemperature() > TEMP_MAX) {
            raiseAlarm(deviceId, "TEMP_THRESHOLD", "温度超阈值: " + data.getTemperature());
        }
        if (data.getHumidity() != null && (data.getHumidity() > HUMIDITY_MAX || data.getHumidity() < HUMIDITY_MIN)) {
            raiseAlarm(deviceId, "HUMIDITY_THRESHOLD", "湿度超阈值: " + data.getHumidity());
        }
        if (data.getLight() != null && data.getLight() > LIGHT_MAX) {
            raiseAlarm(deviceId, "LIGHT_THRESHOLD", "光照异常: " + data.getLight());
        }
        if (data.getWeight() != null && data.getWeight() > WEIGHT_MAX) {
            raiseAlarm(deviceId, "WEIGHT_THRESHOLD", "称重超阈值: " + data.getWeight());
        }
        if (data.getSmoke() != null && data.getSmoke() > SMOKE_MAX) {
            raiseAlarm(deviceId, "SMOKE_THRESHOLD", "烟雾超阈值: " + data.getSmoke());
        }
    }

    private void raiseAlarm(String deviceId, String type, String description) {
        log.warn("环境告警触发: device={}, type={}, desc={}", deviceId, type, description);
        alarmService.create(type, deviceId, "环境监测节点", description);
    }
}
