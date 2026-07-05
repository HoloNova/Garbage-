package com.slidtable.slidtab_pro.service;

import tools.jackson.databind.ObjectMapper;
import com.slidtable.slidtab_pro.dto.protocol.AckResult;
import com.slidtable.slidtab_pro.dto.protocol.ControlCommand;
import com.slidtable.slidtab_pro.service.device.TcpDeviceServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 设备控制服务（TCP-only）。
 * <p>
 * 仅通过 {@link TcpDeviceServer} 向已 TCP 直连的设备下发指令。
 * 设备端无法提供执行完成回执，因此 fire-and-forget：发送成功即返回成功。
 * </p>
 */
@Service
public class ControlService {

    private static final Logger log = LoggerFactory.getLogger(ControlService.class);

    private final TcpDeviceServer tcpDeviceServer;
    private final ObjectMapper objectMapper;

    public ControlService(TcpDeviceServer tcpDeviceServer,
                          ObjectMapper objectMapper) {
        this.tcpDeviceServer = tcpDeviceServer;
        this.objectMapper = objectMapper;
        log.info("[控制服务] 初始化: TCP-only 模式");
    }

    /**
     * 下发控制指令（fire-and-forget）。
     * 设备开始执行即视为成功，不等待执行完成回执。
     */
    public CompletableFuture<AckResult> dispatch(ControlCommand command) {
        String deviceId = command.deviceId();
        log.info("[控制指令下发] device={}, command={}, seq={}",
                deviceId, command.command(), command.seq());

        if (!tcpDeviceServer.isDeviceConnected(deviceId)) {
            log.warn("[控制指令] 设备未连接: {}", deviceId);
            return CompletableFuture.completedFuture(new AckResult(false, "设备未连接: " + deviceId));
        }

        boolean sent = tcpDeviceServer.sendCommand(deviceId, command);
        if (!sent) {
            return CompletableFuture.completedFuture(new AckResult(false, "发送失败"));
        }
        return CompletableFuture.completedFuture(new AckResult(true, "指令已下发"));
    }
}
