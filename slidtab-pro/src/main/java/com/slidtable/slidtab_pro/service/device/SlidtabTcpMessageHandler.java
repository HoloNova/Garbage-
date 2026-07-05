package com.slidtable.slidtab_pro.service.device;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.slidtable.slidtab_pro.dto.protocol.DeviceState;
import com.slidtable.slidtab_pro.dto.protocol.InventoryState;
import com.slidtable.slidtab_pro.dto.protocol.SensorData;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.service.DeviceService;
import com.slidtable.slidtab_pro.service.EnvironmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 图书云柜业务侧 TCP 消息处理器。
 * <p>
 * 从 {@link DeviceTcpConnection} 搬运而来的设备协议解析与业务分发逻辑：
 * 状态上报、传感器数据、告警、心跳、设备注册、指令回执。
 * 通过 {@link TcpMessageHandler} 接口与传输层解耦——传输层不感知业务，
 * 其他后端可替换本实现接入自己的服务。
 * </p>
 *
 * <p>不注入 {@link TcpDeviceServer}，避免循环依赖；通过 {@link DeviceTcpConnection}
 * 暴露的 {@code setDeviceId} / {@code isCurrentConnection} 间接协作。</p>
 */
@Component
public class SlidtabTcpMessageHandler implements TcpMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SlidtabTcpMessageHandler.class);
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final EnvironmentService environmentService;

    public SlidtabTcpMessageHandler(ObjectMapper objectMapper,
                                    DeviceService deviceService,
                                    EnvironmentService environmentService) {
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.environmentService = environmentService;
    }

    @Override
    public void onConnected(DeviceTcpConnection conn) {
        log.info("[TCP设备] 新连接: {}:{}", conn.getRemoteAddr(), conn.getRemotePort());
    }

    @Override
    public void onMessage(DeviceTcpConnection conn, String line) {
        if (line.isEmpty()) return;

        JsonNode root;
        try {
            root = objectMapper.readTree(line);
        } catch (Exception e) {
            log.warn("[TCP协议] JSON 解析失败: {} — 原始: {}", e.getMessage(), truncate(line, 200));
            return;
        }

        String remoteIp = conn.getRemoteAddr();
        String msgDeviceId = root.has("device_id") ? root.get("device_id").asText() : "";
        if (!msgDeviceId.isEmpty() && !msgDeviceId.equals(conn.getDeviceId())) {
            String oldId = conn.getDeviceId();
            String nodeType = root.has("node_type") ? root.get("node_type").asText("ACTUATOR") : "ACTUATOR";
            conn.setDeviceId(msgDeviceId, nodeType);
            log.info("[TCP设备] 识别: {} → {} ({})", oldId, msgDeviceId, remoteIp);
        }

        String msgType = root.has("msg_type") ? root.get("msg_type").asText("") : "";
        String status = root.has("status") ? root.get("status").asText("") : "";
        String source = root.has("source") ? root.get("source").asText(conn.getDeviceId()) : conn.getDeviceId();
        String seq = root.has("seq") ? root.get("seq").asText(genSeq()) : genSeq();
        String timestamp = root.has("timestamp") ? root.get("timestamp").asText(now()) : now();

        log.debug("[TCP设备] ← 收到: type={}, deviceId={}, source={}", msgType, conn.getDeviceId(), source);

        switch (msgType) {
            case "register" -> handleRegister(conn, root, remoteIp);
            case "heartbeat" -> handleHeartbeat(conn);
            case "sensor" -> handleSensorData(conn, root, seq, timestamp, source);
            case "alarm" -> handleAlarm(conn, root, seq, timestamp, source);
            case "control_response" -> handleControlResponse(conn, root);
            case "status" -> handleStatusReport(conn, root, seq, timestamp, source);
            default -> handleGeneric(conn, root, seq, timestamp, source, msgType, status);
        }
    }

    @Override
    public void onDisconnected(DeviceTcpConnection conn) {
        String deviceId = conn.getDeviceId();
        // 竞态防护：仅当自己仍是该 deviceId 的当前连接时才标记离线。
        // 设备已重连时 server map 已指向新连接，旧连接的清理不应把新连接拖离线。
        if (!"unknown".equals(deviceId) && conn.isCurrentConnection()) {
            deviceService.markOffline(deviceId);
            log.info("[TCP设备] 已标记离线: {}", deviceId);
        }
    }

    // ==================== 消息处理器 ====================

    private void handleRegister(DeviceTcpConnection conn, JsonNode root, String remoteIp) {
        int port = root.has("port") ? root.get("port").asInt(8081) : 8081;
        String nodeType = root.has("node_type") ? root.get("node_type").asText("ACTUATOR") : "ACTUATOR";
        log.info("[TCP注册] 设备注册: deviceId={}, ip={}, port={}, nodeType={}",
                conn.getDeviceId(), remoteIp, port, nodeType);
        deviceService.heartbeat(conn.getDeviceId(), conn.getNodeType());
        sendResponse(conn, "register_ack", "0000", "注册成功");
    }

    private void handleHeartbeat(DeviceTcpConnection conn) {
        log.debug("[TCP心跳] deviceId={}", conn.getDeviceId());
        deviceService.heartbeat(conn.getDeviceId(), conn.getNodeType());
    }

    private void handleSensorData(DeviceTcpConnection conn, JsonNode root, String seq, String timestamp, String source) {
        SensorData sensorData = extractSensorData(root);
        if (sensorData == null) return;

        StatusReport report = new StatusReport(
                "1.0", "sensor", seq, timestamp, source, "server",
                conn.getDeviceId(), "success", true, "SENSOR",
                null, null, null, sensorData, null, null
        );
        environmentService.record(conn.getDeviceId(), sensorData);
        deviceService.heartbeat(conn.getDeviceId(), conn.getNodeType());
        log.info("[TCP传感器] deviceId={}, data={}", conn.getDeviceId(), sensorData);
    }

    private void handleAlarm(DeviceTcpConnection conn, JsonNode root, String seq, String timestamp, String source) {
        String resultMsg = root.has("result_msg") ? root.get("result_msg").asText("") : "";
        DeviceState deviceState = extractDeviceState(root);

        StatusReport report = new StatusReport(
                "1.0", "alarm", seq, timestamp, source, "server",
                conn.getDeviceId(), "fail", true, conn.getNodeType(),
                null, null, resultMsg, null, deviceState, null
        );
        deviceService.updateFromReport(report);
        log.warn("[TCP告警] deviceId={}, msg={}", conn.getDeviceId(), resultMsg);
    }

    private void handleControlResponse(DeviceTcpConnection conn, JsonNode root) {
        String seq = root.has("seq") ? root.get("seq").asText("") : "";
        String resultCode = root.has("result_code") ? root.get("result_code").asText("0000") : "0000";
        String resultMsg = root.has("result_msg") ? root.get("result_msg").asText("") : "";
        log.info("[TCP回执] deviceId={}, seq={}, result_code={}, result_msg={}",
                conn.getDeviceId(), seq, resultCode, resultMsg);
        conn.addResponse(resultCode + ": " + resultMsg);
    }

    private void handleStatusReport(DeviceTcpConnection conn, JsonNode root, String seq, String timestamp, String source) {
        boolean online = !root.has("online") || root.get("online").asBoolean(true);
        String action = root.has("action") ? root.get("action").asText("") : "";
        String resultCode = root.has("result_code") ? root.get("result_code").asText("") : "";
        String resultMsg = root.has("result_msg") ? root.get("result_msg").asText("") : "";
        SensorData sensorData = extractSensorData(root);
        DeviceState deviceState = extractDeviceState(root);
        InventoryState inventoryState = extractInventoryState(root);

        StatusReport report = new StatusReport(
                "1.0", "status", seq, timestamp, source, "server",
                conn.getDeviceId(), "success", online, conn.getNodeType(),
                action, resultCode, resultMsg, sensorData, deviceState, inventoryState
        );
        deviceService.updateFromReport(report);
        log.debug("[TCP状态] deviceId={}, online={}", conn.getDeviceId(), online);
    }

    private void handleGeneric(DeviceTcpConnection conn, JsonNode root, String seq, String timestamp,
                                String source, String msgType, String status) {
        SensorData sensorData = extractSensorData(root);
        if (sensorData != null) {
            environmentService.record(conn.getDeviceId(), sensorData);
            log.info("[TCP通用→传感器] deviceId={}, data={}", conn.getDeviceId(), sensorData);
            return;
        }

        DeviceState deviceState = extractDeviceState(root);
        if (deviceState != null) {
            StatusReport report = new StatusReport(
                    "1.0", "status", seq, timestamp, source, "server",
                    conn.getDeviceId(), "success", true, conn.getNodeType(),
                    null, null, null, null, deviceState, null
            );
            deviceService.updateFromReport(report);
            log.debug("[TCP通用→状态] deviceId={}", conn.getDeviceId());
            return;
        }

        if (root.size() > 0 && root.size() <= 3 && !"unknown".equals(conn.getDeviceId())) {
            deviceService.heartbeat(conn.getDeviceId(), conn.getNodeType());
            log.debug("[TCP通用→心跳] deviceId={}", conn.getDeviceId());
            return;
        }

        conn.addResponse(root.toPrettyString());
    }

    // ==================== 工具方法 ====================

    private void sendResponse(DeviceTcpConnection conn, String msgType, String resultCode, String resultMsg) {
        try {
            String json = objectMapper.writeValueAsString(java.util.Map.of(
                    "msg_type", msgType,
                    "result_code", resultCode,
                    "result_msg", resultMsg,
                    "device_id", conn.getDeviceId(),
                    "timestamp", now()
            )) + "\n";
            conn.sendRaw(json);
        } catch (Exception e) {
            log.debug("发送响应失败: {}", e.getMessage());
        }
    }

    private SensorData extractSensorData(JsonNode root) {
        if (!hasAnyField(root, "temperature", "humidity", "light", "weight", "smoke")) {
            return null;
        }
        return new SensorData(
                getDouble(root, "temperature"),
                getDouble(root, "humidity"),
                getDouble(root, "light"),
                getDouble(root, "weight"),
                getDouble(root, "smoke")
        );
    }

    private DeviceState extractDeviceState(JsonNode root) {
        if (!hasAnyField(root, "cabinet_door", "motor_state", "conveyor_state",
                "slot_state", "alarm_state")) {
            return null;
        }
        return new DeviceState(
                getText(root, "cabinet_door"),
                getText(root, "slot_state"),
                getText(root, "motor_state"),
                getText(root, "conveyor_state"),
                getText(root, "alarm_state")
        );
    }

    private InventoryState extractInventoryState(JsonNode root) {
        if (!hasAnyField(root, "book_id", "slot_id", "item_state")) {
            return null;
        }
        return new InventoryState(
                getText(root, "book_id"),
                getText(root, "slot_id"),
                getText(root, "item_state")
        );
    }

    private static boolean hasAnyField(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) return true;
        }
        return false;
    }

    private static Double getDouble(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asDouble() : null;
    }

    private static String getText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String genSeq() {
        return System.currentTimeMillis() + "00001";
    }

    private static String now() {
        return DTF.format(Instant.now());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
