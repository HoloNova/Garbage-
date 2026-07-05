package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.dto.protocol.DeviceResponse;
import com.slidtable.slidtab_pro.entity.Item;
import com.slidtable.slidtab_pro.service.InventoryService;
import com.slidtable.slidtab_pro.service.control.ActionExecutor;
import com.slidtable.slidtab_pro.service.control.PickupJobStore;
import com.slidtable.slidtab_pro.service.device.TcpDeviceServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TCP 设备调试与管理接口。
 * <p>
 * 提供 TCP 设备列表查询、原始指令发送、响应查看等调试功能，
 * 用于前端设备调试页面。
 * </p>
 */
@RestController
@RequestMapping("/api/control/tcp")
public class TcpCommandController {

    private static final Logger log = LoggerFactory.getLogger(TcpCommandController.class);

    private final TcpDeviceServer tcpDeviceServer;
    private final InventoryService inventoryService;
    private final ActionExecutor actionExecutor;
    private final PickupJobStore pickupJobStore;
    private final ObjectMapper objectMapper;

    public TcpCommandController(TcpDeviceServer tcpDeviceServer,
                                InventoryService inventoryService,
                                ActionExecutor actionExecutor,
                                PickupJobStore pickupJobStore,
                                ObjectMapper objectMapper) {
        this.tcpDeviceServer = tcpDeviceServer;
        this.inventoryService = inventoryService;
        this.actionExecutor = actionExecutor;
        this.pickupJobStore = pickupJobStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取所有已 TCP 连接的设备列表。
     */
    @GetMapping("/devices")
    public ApiResponse<Collection<Map<String, Object>>> listDevices() {
        return ApiResponse.success(tcpDeviceServer.listDevices());
    }

    /**
     * 向指定设备发送原始文本指令。
     * <p>
     * 不对内容做任何包装，直接写入 TCP socket。
     * 适用于协议格式未定时的自由调试。
     * </p>
     */
    @PostMapping("/send-raw")
    public ApiResponse<Map<String, Object>> sendRaw(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        String content = body.get("content");

        if (deviceId == null || deviceId.isBlank()) {
            return ApiResponse.error(1001, "deviceId 不能为空");
        }
        if (content == null || content.isBlank()) {
            return ApiResponse.error(1001, "content 不能为空");
        }

        boolean sent = tcpDeviceServer.sendRaw(deviceId, content);
        if (!sent) {
            log.warn("[TCP调试] 发送失败: deviceId={} 不在线", deviceId);
            return ApiResponse.error(1003, "设备不在线: " + deviceId);
        }

        log.info("[TCP调试] 原始指令已发送: deviceId={}, len={}", deviceId, content.length());
        return ApiResponse.success(Map.of(
                "deviceId", deviceId,
                "sent", true,
                "length", content.length()
        ));
    }

    /**
     * 向所有已连接的设备广播原始文本。
     */
    @PostMapping("/broadcast")
    public ApiResponse<Map<String, Object>> broadcast(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ApiResponse.error(1001, "content 不能为空");
        }

        int count = tcpDeviceServer.broadcastRaw(content);
        return ApiResponse.success(Map.of(
                "sent", true,
                "deviceCount", count
        ));
    }

    /**
     * 获取指定设备的响应缓存。
     */
    @GetMapping("/responses")
    public ApiResponse<List<DeviceResponse>> getResponses(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "50") int limit) {
        List<DeviceResponse> all = tcpDeviceServer.getResponses(deviceId);
        if (limit > 0 && all.size() > limit) {
            all = all.subList(all.size() - limit, all.size());
        }
        return ApiResponse.success(all);
    }

    /**
     * 清空指定设备的响应缓存。
     */
    @DeleteMapping("/responses")
    public ApiResponse<String> clearResponses(@RequestParam String deviceId) {
        tcpDeviceServer.clearResponses(deviceId);
        return ApiResponse.success("ok");
    }

    /**
     * 测试设备 TCP 连接是否正常。
     * <p>
     * 仅检查连接状态，不向设备发送任何数据，避免触发设备端误动作。
     * </p>
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection(@RequestParam String deviceId) {
        boolean connected = tcpDeviceServer.isDeviceConnected(deviceId);
        return ApiResponse.success(Map.of(
                "deviceId", deviceId,
                "connected", connected,
                "message", connected ? "TCP 连接正常" : "设备未连接"
        ));
    }

    /**
     * 模拟取货：根据图书的 actionSequence 直接派发动作序列到 ActionExecutor。
     * <p>不创建借阅记录、不更新库存，仅用于测试设备动作。recordId=0 表示模拟任务。</p>
     * <p>派发前会校验所有步骤涉及的设备是否已连接，未连接立即返回错误，避免异步失败无反馈。</p>
     */
    @PostMapping("/simulate-pickup")
    public ApiResponse<Map<String, Object>> simulatePickup(@RequestParam String itemId) {
        Item item = inventoryService.getEntity(itemId);
        List<ActionStep> steps = parseActionSequence(item.getActionSequence());
        if (steps.isEmpty()) {
            return ApiResponse.error(1009, "未配置动作序列");
        }

        // 前置校验：所有步骤涉及的设备必须已连接，否则立即返回错误
        List<String> missingDevices = new ArrayList<>();
        for (ActionStep step : steps) {
            String dev = step.device();
            if (!tcpDeviceServer.isDeviceConnected(dev) && !missingDevices.contains(dev)) {
                missingDevices.add(dev);
            }
        }
        if (!missingDevices.isEmpty()) {
            log.warn("[模拟取货] 设备未就绪，拒绝派发: itemId={}, missing={}", itemId, missingDevices);
            return ApiResponse.error(1003,
                    "设备未就绪，请先连接设备: " + String.join(", ", missingDevices));
        }

        String jobId = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        actionExecutor.execute(jobId, 0L, steps);
        log.info("[模拟取货] 派发: itemId={}, jobId={}, steps={}", itemId, jobId, steps.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("steps", steps.size());
        result.put("message", "取件已启动");
        return ApiResponse.success(result);
    }

    /**
     * 查询模拟取货任务进度。
     * <p>前端轮询此接口驱动进度面板 UI。返回当前步骤、总步数、状态和消息。</p>
     */
    @GetMapping("/simulate-pickup/status")
    public ApiResponse<Map<String, Object>> simulatePickupStatus(@RequestParam String jobId) {
        PickupJob job = pickupJobStore.getByJobId(jobId);
        if (job == null) {
            return ApiResponse.error(1002, "任务不存在: " + jobId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getJobId());
        result.put("status", job.getStatus().name());
        result.put("currentStep", job.getCurrentStep());
        result.put("totalSteps", job.getTotalSteps());
        result.put("message", job.getMessage());
        return ApiResponse.success(result);
    }

    private List<ActionStep> parseActionSequence(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ActionStep>>() {});
        } catch (Exception e) {
            log.warn("[模拟取货] 动作序列解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
