package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.entity.AlarmRecord;
import com.slidtable.slidtab_pro.entity.DeviceStatus;
import com.slidtable.slidtab_pro.enums.AlarmStatus;
import com.slidtable.slidtab_pro.enums.BorrowStatus;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.ItemType;
import com.slidtable.slidtab_pro.repository.AlarmRecordRepository;
import com.slidtable.slidtab_pro.repository.BorrowRecordRepository;
import com.slidtable.slidtab_pro.repository.DeviceStatusRepository;
import com.slidtable.slidtab_pro.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    private final ItemRepository itemRepository;
    private final BorrowRecordRepository borrowRepository;
    private final DeviceStatusRepository deviceRepository;
    private final AlarmRecordRepository alarmRepository;

    public StatisticsService(ItemRepository itemRepository, BorrowRecordRepository borrowRepository,
                             DeviceStatusRepository deviceRepository, AlarmRecordRepository alarmRepository) {
        this.itemRepository = itemRepository;
        this.borrowRepository = borrowRepository;
        this.deviceRepository = deviceRepository;
        this.alarmRepository = alarmRepository;
    }

    public Map<String, Object> dashboard() {
        log.info("[统计看板] 开始聚合统计");
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalItems", itemRepository.count());
        stats.put("availableItems", itemRepository.findByStatus(ItemStatus.AVAILABLE).size());
        stats.put("borrowedItems", itemRepository.findByStatus(ItemStatus.BORROWED).size());
        stats.put("reservedItems", itemRepository.findByStatus(ItemStatus.RESERVED).size());

        Map<String, Long> byType = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            byType.put(type.name(), (long) itemRepository.findByType(type).size());
        }
        stats.put("itemsByType", byType);

        stats.put("activeBorrows", borrowRepository.findByStatus(BorrowStatus.BORROWED).size());
        stats.put("reservations", borrowRepository.findByStatus(BorrowStatus.RESERVED).size());

        List<DeviceStatus> devices = deviceRepository.findAll();
        stats.put("totalDevices", devices.size());
        stats.put("onlineDevices", devices.stream().filter(DeviceStatus::isOnline).count());

        stats.put("pendingAlarms", alarmRepository.findByStatus(AlarmStatus.PENDING).size());
        log.info("[统计看板] 完成: totalItems={}, availableItems={}, activeBorrows={}, onlineDevices={}/{}, pendingAlarms={}",
                stats.get("totalItems"), stats.get("availableItems"), stats.get("activeBorrows"),
                stats.get("onlineDevices"), stats.get("totalDevices"), stats.get("pendingAlarms"));
        return stats;
    }
}
