package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.common.BusinessException;
import com.slidtable.slidtab_pro.dto.protocol.DeviceState;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.dto.response.DeviceStatusView;
import com.slidtable.slidtab_pro.entity.DeviceStatus;
import com.slidtable.slidtab_pro.repository.DeviceStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceStatusRepository repository;

    public DeviceService(DeviceStatusRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录设备心跳。
     * <p>
     * 由 {@code TcpDeviceServer} 的 TCP 连接处理器调用——能调用到这里说明设备真实在 TCP 上。
     * 记录心跳时间并设置 {@code online=true}。
     * </p>
     */
    public DeviceStatus heartbeat(String deviceId, String nodeType) {
        log.info("[设备心跳] deviceId={}, nodeType={}", deviceId, nodeType);
        DeviceStatus device = repository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    DeviceStatus d = new DeviceStatus();
                    d.setDeviceId(deviceId);
                    d.setOnline(true);
                    log.info("[设备心跳] 首次注册新设备: {}", deviceId);
                    return d;
                });
        device.setOnline(true);
        device.setLastHeartbeat(LocalDateTime.now());
        if (nodeType != null) device.setNodeType(nodeType);
        DeviceStatus saved = repository.save(device);
        log.info("[设备心跳] 已更新: deviceId={}, online={}, lastHeartbeat={}",
                saved.getDeviceId(), saved.isOnline(), saved.getLastHeartbeat());
        return saved;
    }

    public DeviceStatus updateFromReport(StatusReport report) {
        log.info("[设备状态上报] deviceId={}, nodeType={}, online={}",
                report.deviceId(), report.nodeType(), report.online());
        DeviceStatus device = repository.findByDeviceId(report.deviceId())
                .orElseGet(() -> {
                    DeviceStatus d = new DeviceStatus();
                    d.setDeviceId(report.deviceId());
                    return d;
                });
        device.setOnline(Boolean.TRUE.equals(report.online()));
        device.setNodeType(report.nodeType());
        DeviceState state = report.deviceState();
        if (state != null) {
            device.setMotorState(state.motorState());
            device.setCabinetDoor(state.cabinetDoor());
            device.setConveyorState(state.conveyorState());
            device.setAlarmState(state.alarmState());
            log.info("[设备状态上报] 状态详情: deviceId={}, motor={}, door={}, conveyor={}, alarm={}",
                    report.deviceId(), state.motorState(), state.cabinetDoor(),
                    state.conveyorState(), state.alarmState());
        }
        device.setLastHeartbeat(LocalDateTime.now());
        return repository.save(device);
    }

    /**
     * TCP 断开连接时标记设备离线。
     */
    public void markOffline(String deviceId) {
        repository.findByDeviceId(deviceId).ifPresent(d -> {
            d.setOnline(false);
            repository.save(d);
            log.info("[设备离线] 已标记: deviceId={}", deviceId);
        });
    }

    public DeviceStatusView getView(String deviceId) {
        log.debug("[查询设备] deviceId={}", deviceId);
        DeviceStatus device = repository.findByDeviceId(deviceId)
                .orElseThrow(() -> {
                    log.warn("[查询设备] 不存在: deviceId={}", deviceId);
                    return new BusinessException(1002, "设备不存在: " + deviceId);
                });
        return toView(device);
    }

    public List<DeviceStatusView> listAll() {
        List<DeviceStatusView> list = repository.findAll().stream().map(this::toView).toList();
        log.info("[查询设备列表] 共 {} 台", list.size());
        return list;
    }

    private DeviceStatusView toView(DeviceStatus device) {
        return new DeviceStatusView(device.getDeviceId(), device.getNodeType(), device.isOnline(),
                device.getMotorState(), device.getCabinetDoor(), device.getConveyorState(),
                device.getAlarmState(), device.getLastHeartbeat());
    }
}
