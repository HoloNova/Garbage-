package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.entity.AlarmRecord;
import com.slidtable.slidtab_pro.enums.AlarmStatus;
import com.slidtable.slidtab_pro.repository.AlarmRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmRecordRepository repository;

    public AlarmService(AlarmRecordRepository repository) {
        this.repository = repository;
    }

    public AlarmRecord create(String alarmType, String deviceId, String location, String description) {
        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmType(alarmType);
        alarm.setAlarmTime(LocalDateTime.now());
        alarm.setLocation(location);
        alarm.setDeviceId(deviceId);
        alarm.setStatus(AlarmStatus.PENDING);
        alarm.setDescription(description);
        AlarmRecord saved = repository.save(alarm);
        log.warn("[告警创建] id={}, type={}, device={}, location={}, desc={}",
                saved.getId(), alarmType, deviceId, location, description);
        return saved;
    }

    public List<AlarmRecord> listByStatus(AlarmStatus status) {
        List<AlarmRecord> list = status != null ? repository.findByStatus(status) : repository.findAll();
        log.info("[告警查询] status={} → 命中 {} 条", status, list.size());
        return list;
    }

    public AlarmRecord handle(Long id, String handler, String description) {
        log.info("[告警处理] 开始: id={}, handler={}, desc={}", id, handler, description);
        AlarmRecord alarm = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("[告警处理] 失败: 告警不存在 id={}", id);
                    return new IllegalArgumentException("告警记录不存在: " + id);
                });
        alarm.setStatus(AlarmStatus.RESOLVED);
        alarm.setHandler(handler);
        if (description != null) {
            alarm.setDescription(description);
        }
        AlarmRecord saved = repository.save(alarm);
        log.info("[告警处理] 完成: id={}, handler={}, 状态=RESOLVED", id, handler);
        return saved;
    }
}
