package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.protocol.AckResult;
import com.slidtable.slidtab_pro.dto.protocol.ControlCommand;
import com.slidtable.slidtab_pro.service.ControlService;
import com.slidtable.slidtab_pro.service.DeviceService;
import com.slidtable.slidtab_pro.service.device.TcpDeviceServer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 设备控制指令端点（TCP-only）。
 * <p>仅保留指令下发与心跳；扫描/注册/配置端点已随 registration/discovery 模块移除。</p>
 */
@RestController
@RequestMapping("/api/control")
public class ControlCommandController {

    private static final Logger log = LoggerFactory.getLogger(ControlCommandController.class);

    private final ControlService controlService;
    private final DeviceService deviceService;
    private final TcpDeviceServer tcpDeviceServer;

    public ControlCommandController(ControlService controlService,
                                    DeviceService deviceService,
                                    TcpDeviceServer tcpDeviceServer) {
        this.controlService = controlService;
        this.deviceService = deviceService;
        this.tcpDeviceServer = tcpDeviceServer;
    }

    /**
     * 下发控制指令（fire-and-forget，设备开始执行即返回成功）。
     */
    @PostMapping("/send")
    public ApiResponse<AckResult> send(@Valid @RequestBody ControlCommand command) {
        AckResult result = controlService.dispatch(command).join();
        return ApiResponse.success(result);
    }

    /**
     * 仅当设备真实 TCP 连接时才允许标记在线。
     */
    @PostMapping("/heartbeat")
    public ApiResponse<String> heartbeat(@RequestParam String deviceId) {
        if (!tcpDeviceServer.isDeviceConnected(deviceId)) {
            log.warn("[心跳] 拒绝: deviceId={} 未通过 TCP 连接", deviceId);
            return ApiResponse.error(1003, "设备未通过 TCP 连接");
        }
        deviceService.heartbeat(deviceId, null);
        return ApiResponse.success("ok");
    }
}
