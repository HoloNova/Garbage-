package com.slidtable.slidtab_pro.service.control;

import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.PickupJobCompletedEvent;
import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.dto.protocol.ControlCommand;
import com.slidtable.slidtab_pro.dto.protocol.ControlParams;
import com.slidtable.slidtab_pro.enums.CommandType;
import com.slidtable.slidtab_pro.enums.PickupJobStatus;
import com.slidtable.slidtab_pro.service.device.TcpDeviceServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 动作执行引擎。
 * <p>
 * 采用<b>按设备阻塞</b>策略：发送指令后锁定该设备 stepDelayMs 毫秒，
 * 期间任何向同一设备发送的指令必须等待解锁。
 * </p>
 * <p>
 * {@code blocking=true} 表示<b>全局屏障</b>：等待所有已锁定设备解锁后再继续下一步，
 * 用于多设备协同的同步点（如滑台定位完成后才让机械臂抓取）。
 * {@code blocking=false} 则只对目标设备加锁，不等全局屏障，允许下一步立即对其他设备发指令。
 * </p>
 * <p>
 * 设备端无法提供"执行完成"回执，固定等待时长由 {@code device.tcp.action-step-delay-ms} 配置。
 * </p>
 */
@Component
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);
    private static final String ARM_DEVICE_PREFIX = "arm";
    private static final String SLIDE_DEVICE_PREFIX = "mse";

    /** 指令发送后设备锁定时长（设备执行动作所需时间），package-private 供测试覆盖 */
    @Value("${device.tcp.action-step-delay-ms:4000}")
    long stepDelayMs;

    /** 每台设备的解锁时刻（System.currentTimeMillis() + stepDelayMs），跨 job 共享 */
    private final Map<String, Long> deviceUnlockTime = new ConcurrentHashMap<>();

    private final TcpDeviceServer tcpDeviceServer;
    private final PickupJobStore jobStore;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "action-executor");
        t.setDaemon(true);
        return t;
    });

    public ActionExecutor(@Lazy TcpDeviceServer tcpDeviceServer, PickupJobStore jobStore,
                          ApplicationEventPublisher eventPublisher,
                          ObjectMapper objectMapper) {
        this.tcpDeviceServer = tcpDeviceServer;
        this.jobStore = jobStore;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行动作序列。提交后立即返回，进度通过 {@link PickupJobStore} 查询。
     */
    public void execute(String jobId, Long recordId, List<ActionStep> steps) {
        PickupJob job = new PickupJob(jobId, recordId, steps.size());
        jobStore.put(job);
        executor.submit(() -> runJob(job, steps));
    }

    void runJob(PickupJob job, List<ActionStep> steps) {
        boolean success = false;
        long jobStart = System.currentTimeMillis();
        log.info("[ActionExecutor] job={} 开始执行，共 {} 步，设备锁 {}ms",
                job.getJobId(), steps.size(), stepDelayMs);
        try {
            for (int i = 0; i < steps.size(); i++) {
                ActionStep step = steps.get(i);
                job.setCurrentStep(i);
                job.setMessage("执行第 " + (i + 1) + "/" + steps.size() + " 步: " + step.device() + " cmd=" + step.cmd());

                // 1. 等待目标设备解锁（按设备阻塞）
                long deviceWait = waitUntilDeviceUnlock(step.device());
                if (deviceWait > 0) {
                    log.info("[ActionExecutor] job={} 第 {} 步等待设备 {} 解锁 {}ms (t+{}ms)",
                            job.getJobId(), i + 1, step.device(), deviceWait, System.currentTimeMillis() - jobStart);
                }

                log.info("[ActionExecutor] job={} 第 {}/{} 步发送: device={}, cmd={}, blocking={}, t+{}ms",
                        job.getJobId(), i + 1, steps.size(), step.device(), step.cmd(),
                        step.blocking(), System.currentTimeMillis() - jobStart);

                // 2. 发送指令
                if (!sendStep(step)) {
                    job.setStatus(PickupJobStatus.FAILED);
                    job.setMessage("第 " + (i + 1) + " 步失败: 设备未连接或发送失败 " + step.device());
                    log.warn("[ActionExecutor] job={} 失败于第 {} 步: 设备未连接 {} (t+{}ms)",
                            job.getJobId(), i + 1, step.device(), System.currentTimeMillis() - jobStart);
                    return;
                }

                // 3. 锁定目标设备 stepDelayMs
                long unlockAt = System.currentTimeMillis() + stepDelayMs;
                deviceUnlockTime.put(step.device(), unlockAt);
                log.info("[ActionExecutor] job={} 第 {} 步已发送，锁定设备 {} 直到 t+{}ms",
                        job.getJobId(), i + 1, step.device(), unlockAt - jobStart);

                // 4. blocking=true: 等待所有设备解锁（全局屏障）
                if (step.blocking()) {
                    long barrierWait = waitForAllDevicesUnlock();
                    log.info("[ActionExecutor] job={} 第 {} 步全局屏障等待 {}ms (t+{}ms)",
                            job.getJobId(), i + 1, barrierWait, System.currentTimeMillis() - jobStart);
                }
            }
            job.setCurrentStep(steps.size());
            job.setStatus(PickupJobStatus.SUCCESS);
            job.setMessage("取件完成");
            success = true;
            log.info("[ActionExecutor] job={} 成功完成，总耗时 {}ms", job.getJobId(),
                    System.currentTimeMillis() - jobStart);
        } catch (Exception e) {
            job.setStatus(PickupJobStatus.FAILED);
            job.setMessage("执行异常: " + e.getMessage());
            log.error("[ActionExecutor] job={} 异常 (t+{}ms)", job.getJobId(),
                    System.currentTimeMillis() - jobStart, e);
        } finally {
            eventPublisher.publishEvent(new PickupJobCompletedEvent(job.getRecordId(), success));
        }
    }

    /** 等待指定设备解锁，返回实际等待毫秒数 */
    private long waitUntilDeviceUnlock(String deviceId) {
        Long unlockAt = deviceUnlockTime.get(deviceId);
        if (unlockAt == null) return 0;
        long wait = unlockAt - System.currentTimeMillis();
        if (wait <= 0) return 0;
        sleep(wait);
        return wait;
    }

    /** 等待所有已锁定设备解锁（全局屏障），返回实际等待毫秒数 */
    private long waitForAllDevicesUnlock() {
        long maxUnlock = 0;
        for (Long unlockAt : deviceUnlockTime.values()) {
            if (unlockAt > maxUnlock) maxUnlock = unlockAt;
        }
        long wait = maxUnlock - System.currentTimeMillis();
        if (wait <= 0) return 0;
        sleep(wait);
        return wait;
    }

    private boolean sendStep(ActionStep step) {
        String deviceId = step.device();
        if (!tcpDeviceServer.isDeviceConnected(deviceId)) {
            log.warn("[ActionExecutor] 设备未连接: {}, 跳过 cmd={}", deviceId, step.cmd());
            return false;
        }
        ControlCommand command = buildCommand(step);
        try {
            String json = objectMapper.writeValueAsString(command);
            log.info("[ActionExecutor] → 发送指令: device={}, command={}, cmd={}, json={}",
                    deviceId, command.command(), step.cmd(), json);
        } catch (Exception ignored) {}
        return tcpDeviceServer.sendCommand(deviceId, command);
    }

    private ControlCommand buildCommand(ActionStep step) {
        String deviceId = step.device();
        String commandName = commandForDevice(deviceId);
        ControlParams params = new ControlParams(
                null, null, null, null, null, null, null, null, null, step.cmd());
        return ControlCommand.buildCommand("server", deviceId, deviceId, commandName, params);
    }

    private String commandForDevice(String deviceId) {
        if (deviceId.contains(ARM_DEVICE_PREFIX)) return CommandType.START_ARM.name();
        if (deviceId.contains(SLIDE_DEVICE_PREFIX)) return CommandType.MOVE_TO_SLOT.name();
        return CommandType.START_ARM.name();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
