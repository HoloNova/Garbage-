package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.EnvironmentData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnvironmentDataRepository extends JpaRepository<EnvironmentData, Long> {

    Optional<EnvironmentData> findFirstByDeviceIdOrderByRecordedAtDesc(String deviceId);

    List<EnvironmentData> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String deviceId, LocalDateTime start, LocalDateTime end);

    int deleteByDeviceIdStartingWith(String prefix);
}
