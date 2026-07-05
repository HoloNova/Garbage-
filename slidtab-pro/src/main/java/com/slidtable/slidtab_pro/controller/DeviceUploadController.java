package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.service.DeviceService;
import com.slidtable.slidtab_pro.service.EnvironmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload")
public class DeviceUploadController {

    private static final Logger log = LoggerFactory.getLogger(DeviceUploadController.class);

    private final DeviceService deviceService;
    private final EnvironmentService environmentService;

    public DeviceUploadController(DeviceService deviceService, EnvironmentService environmentService) {
        this.deviceService = deviceService;
        this.environmentService = environmentService;
    }

    @PostMapping("/sensor")
    public ApiResponse<StatusReport> uploadSensor(@Valid @RequestBody StatusReport report) {
        log.info("[设备上报-传感器] deviceId={}, msgType={}, sensorData={}",
                report.deviceId(), report.msgType(), report.sensorData());
        environmentService.record(report.deviceId(), report.sensorData());
        return ApiResponse.success(report);
    }

    @PostMapping("/status")
    public ApiResponse<StatusReport> uploadStatus(@Valid @RequestBody StatusReport report) {
        log.info("[设备上报-状态] deviceId={}, nodeType={}, online={}, deviceState={}",
                report.deviceId(), report.nodeType(), report.online(), report.deviceState());
        deviceService.updateFromReport(report);
        return ApiResponse.success(report);
    }

    @PostMapping("/alarm")
    public ApiResponse<StatusReport> uploadAlarm(@Valid @RequestBody StatusReport report) {
        log.warn("[设备上报-告警] deviceId={}, nodeType={}, resultMsg={}, deviceState={}",
                report.deviceId(), report.nodeType(), report.resultMsg(), report.deviceState());
        deviceService.updateFromReport(report);
        return ApiResponse.success(report);
    }
}
