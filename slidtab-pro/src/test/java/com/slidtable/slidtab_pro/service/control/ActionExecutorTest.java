package com.slidtable.slidtab_pro.service.control;

import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.enums.PickupJobStatus;
import com.slidtable.slidtab_pro.service.device.TcpDeviceServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionExecutorTest {

    @Mock
    private TcpDeviceServer tcpDeviceServer;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private final PickupJobStore jobStore = new PickupJobStore();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ActionExecutor(tcpDeviceServer, jobStore, eventPublisher, objectMapper);
        executor.stepDelayMs = 0; // 测试中不等待
    }

    // ==================== runJob 路径 ====================

    @Test
    void runJob_allStepsSent_statusSuccess() {
        when(tcpDeviceServer.isDeviceConnected("esp8266_arm_01")).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_arm_01"), any())).thenReturn(true);

        List<ActionStep> steps = List.of(
                new ActionStep("esp8266_arm_01", 1, true),
                new ActionStep("esp8266_arm_01", 2, true));
        PickupJob job = new PickupJob("job-1", 100L, steps.size());

        executor.runJob(job, steps);

        assertThat(job.getStatus()).isEqualTo(PickupJobStatus.SUCCESS);
        assertThat(job.getCurrentStep()).isEqualTo(2);
        assertThat(job.getMessage()).isEqualTo("取件完成");
    }

    @Test
    void runJob_deviceNotConnected_statusFailedFast() {
        when(tcpDeviceServer.isDeviceConnected("esp8266_arm_01")).thenReturn(false);

        List<ActionStep> steps = List.of(new ActionStep("esp8266_arm_01", 1, true));
        PickupJob job = new PickupJob("job-2", 101L, 1);

        executor.runJob(job, steps);

        assertThat(job.getStatus()).isEqualTo(PickupJobStatus.FAILED);
        assertThat(job.getMessage()).contains("设备未连接");
    }

    @Test
    void runJob_nonBlockingStep_doesNotDelay() {
        when(tcpDeviceServer.isDeviceConnected("esp8266_mse_01")).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_mse_01"), any())).thenReturn(true);

        List<ActionStep> steps = List.of(
                new ActionStep("esp8266_mse_01", 0, false),
                new ActionStep("esp8266_mse_01", 1, false));
        PickupJob job = new PickupJob("job-3", 102L, 2);

        executor.runJob(job, steps);

        assertThat(job.getStatus()).isEqualTo(PickupJobStatus.SUCCESS);
    }

    @Test
    void runJob_mixedDevices_statusSuccess() {
        when(tcpDeviceServer.isDeviceConnected("esp8266_mse_01")).thenReturn(true);
        when(tcpDeviceServer.isDeviceConnected("esp8266_arm_01")).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_mse_01"), any())).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_arm_01"), any())).thenReturn(true);

        List<ActionStep> steps = List.of(
                new ActionStep("esp8266_mse_01", 2, true),
                new ActionStep("esp8266_arm_01", 1, true),
                new ActionStep("esp8266_mse_01", 0, true),
                new ActionStep("esp8266_arm_01", 2, true),
                new ActionStep("esp8266_arm_01", 0, true));
        PickupJob job = new PickupJob("job-4", 103L, 5);

        executor.runJob(job, steps);

        assertThat(job.getStatus()).isEqualTo(PickupJobStatus.SUCCESS);
        assertThat(job.getCurrentStep()).isEqualTo(5);
    }

    @Test
    void runJob_step2DeviceNotConnected_stopsAtStep2() {
        when(tcpDeviceServer.isDeviceConnected("esp8266_mse_01")).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_mse_01"), any())).thenReturn(true);
        when(tcpDeviceServer.isDeviceConnected("esp8266_arm_01")).thenReturn(false);

        List<ActionStep> steps = List.of(
                new ActionStep("esp8266_mse_01", 2, true),
                new ActionStep("esp8266_arm_01", 1, true));
        PickupJob job = new PickupJob("job-5", 104L, 2);

        executor.runJob(job, steps);

        assertThat(job.getStatus()).isEqualTo(PickupJobStatus.FAILED);
        assertThat(job.getMessage()).contains("第 2 步失败");
    }

    @Test
    void execute_asyncJobEventuallyCompletes() throws Exception {
        when(tcpDeviceServer.isDeviceConnected("esp8266_arm_01")).thenReturn(true);
        when(tcpDeviceServer.sendCommand(eq("esp8266_arm_01"), any())).thenReturn(true);

        List<ActionStep> steps = List.of(new ActionStep("esp8266_arm_01", 0, true));
        executor.execute("job-async", 200L, steps);

        for (int i = 0; i < 50; i++) {
            PickupJob job = jobStore.get(200L);
            if (job != null && job.getStatus() != PickupJobStatus.RUNNING) break;
            Thread.sleep(50);
        }
        PickupJob done = jobStore.get(200L);
        assertThat(done).isNotNull();
        assertThat(done.getStatus()).isEqualTo(PickupJobStatus.SUCCESS);
    }
}
