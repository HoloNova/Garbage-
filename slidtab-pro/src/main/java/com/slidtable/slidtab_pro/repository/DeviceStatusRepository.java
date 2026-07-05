package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Long> {

    Optional<DeviceStatus> findByDeviceId(String deviceId);

    List<DeviceStatus> findByOnline(boolean online);

    int deleteByDeviceIdStartingWith(String prefix);
}
