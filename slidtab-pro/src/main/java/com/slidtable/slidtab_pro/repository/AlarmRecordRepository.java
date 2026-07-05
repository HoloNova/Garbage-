package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.AlarmRecord;
import com.slidtable.slidtab_pro.enums.AlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, Long> {

    List<AlarmRecord> findByStatus(AlarmStatus status);

    List<AlarmRecord> findByAlarmType(String alarmType);
}
