package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.request.SlotPositionRequest;
import com.slidtable.slidtab_pro.dto.response.DeviceStatusView;
import com.slidtable.slidtab_pro.dto.response.ItemView;
import com.slidtable.slidtab_pro.dto.response.SlotView;
import com.slidtable.slidtab_pro.entity.AlarmRecord;
import com.slidtable.slidtab_pro.entity.EnvironmentData;
import com.slidtable.slidtab_pro.enums.AlarmStatus;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.ItemType;
import com.slidtable.slidtab_pro.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final InventoryService inventoryService;
    private final EnvironmentService environmentService;
    private final AlarmService alarmService;
    private final DeviceService deviceService;
    private final StatisticsService statisticsService;

    public QueryController(InventoryService inventoryService, EnvironmentService environmentService,
                           AlarmService alarmService, DeviceService deviceService,
                           StatisticsService statisticsService) {
        this.inventoryService = inventoryService;
        this.environmentService = environmentService;
        this.alarmService = alarmService;
        this.deviceService = deviceService;
        this.statisticsService = statisticsService;
    }

    @GetMapping("/device")
    public ApiResponse<List<DeviceStatusView>> devices() {
        return ApiResponse.success(deviceService.listAll());
    }

    @GetMapping("/device/{deviceId}")
    public ApiResponse<DeviceStatusView> device(@PathVariable String deviceId) {
        return ApiResponse.success(deviceService.getView(deviceId));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<ItemView>> inventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) ItemStatus status) {
        return ApiResponse.success(inventoryService.search(keyword, type, status));
    }

    @GetMapping("/inventory/{itemId}")
    public ApiResponse<ItemView> item(@PathVariable String itemId) {
        return ApiResponse.success(inventoryService.getItem(itemId));
    }

    @GetMapping("/slots/{cabinetId}")
    public ApiResponse<List<SlotView>> slots(@PathVariable String cabinetId) {
        return ApiResponse.success(inventoryService.getSlotsByCabinet(cabinetId));
    }

    @PutMapping("/slots/{slotId}/position")
    public ApiResponse<SlotView> updateSlotPosition(@PathVariable String slotId,
                                                    @RequestBody SlotPositionRequest body) {
        return ApiResponse.success(inventoryService.updatePosition(slotId, body.testId(), body.posX(), body.posY()));
    }

    @GetMapping("/env/latest")
    public ApiResponse<EnvironmentData> envLatest(@RequestParam String deviceId) {
        return ApiResponse.success(environmentService.latest(deviceId));
    }

    @GetMapping("/env/history")
    public ApiResponse<List<EnvironmentData>> envHistory(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ApiResponse.success(environmentService.history(deviceId, start, end));
    }

    @GetMapping("/alarm")
    public ApiResponse<List<AlarmRecord>> alarms(@RequestParam(required = false) AlarmStatus status) {
        return ApiResponse.success(alarmService.listByStatus(status));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(statisticsService.dashboard());
    }
}
