package com.slidtable.slidtab_pro;

import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.protocol.ControlParams;
import com.slidtable.slidtab_pro.dto.protocol.DeviceState;
import com.slidtable.slidtab_pro.dto.protocol.SensorData;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.dto.response.LoginResponse;
import com.slidtable.slidtab_pro.dto.response.ReserveResponse;
import com.slidtable.slidtab_pro.entity.*;
import com.slidtable.slidtab_pro.enums.*;
import com.slidtable.slidtab_pro.repository.*;
import com.slidtable.slidtab_pro.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SlidtabProApplicationTests {

    @Autowired UserRepository userRepository;
    @Autowired CabinetRepository cabinetRepository;
    @Autowired SlotRepository slotRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired DeviceStatusRepository deviceRepository;
    @Autowired AlarmRecordRepository alarmRepository;
    @Autowired BorrowRecordRepository borrowRecordRepository;
    @Autowired UserService userService;
    @Autowired InventoryService inventoryService;
    @Autowired BorrowService borrowService;
    @Autowired EnvironmentService environmentService;
    @Autowired DeviceService deviceService;
    @Autowired StatisticsService statisticsService;

    private Cabinet cab;
    private Slot slot;

    @BeforeEach
    void setUp() {
        // 按外键约束顺序清理（保留演示用户，由 DataInitializer 统一创建）
        borrowRecordRepository.deleteAll();
        alarmRepository.deleteAll();
        itemRepository.deleteAll();
        slotRepository.deleteAll();
        cabinetRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void login_seededAdmin_returnsAdminIdentity() {
        // 演示用户由 DataInitializer 自动创建（U001 管理员, U002 张三, U003 李四）
        LoginResponse resp = userService.login(
                new com.slidtable.slidtab_pro.dto.request.LoginRequest("13800000001", "S001"));
        assertThat(resp.userId()).isEqualTo("U001");
        assertThat(resp.identity()).isEqualTo(UserIdentity.ADMIN);
    }

    @Test
    void createAndQueryItem() {
        cab = cabinet("cabinet_01", "测试柜");
        slot = slot("T01", cab, SlotStatus.OCCUPIED, 1, 100, 200);
        item("IT001", ItemType.BOOK, "测试图书", slot);

        var items = inventoryService.search(null, null, null);
        assertThat(items).isNotEmpty();
        assertThat(inventoryService.getItem("IT001")).isNotNull();
    }

    @Test
    void fullBorrowFlow_reserve_works() {
        // 使用 DataInitializer 创建的演示用户 U002
        cab = cabinet("cabinet_01", "测试柜");
        slot = slot("A01", cab, SlotStatus.OCCUPIED, 1, 100, 200);
        item("BK001", ItemType.BOOK, "测试图书", slot);

        // 预约（业务逻辑，不依赖硬件）
        ReserveResponse reserved = borrowService.reserve("U002", "BK001");
        assertThat(reserved.recordId()).isNotNull();
        assertThat(reserved.slotId()).isEqualTo("A01");
        assertThat(reserved.cabinetId()).isEqualTo("cabinet_01");
    }

    @Test
    void fullBorrowFlow_pickup_failsWithoutHardware() throws Exception {
        // 使用 DataInitializer 创建的演示用户 U002
        cab = cabinet("cabinet_01", "测试柜");
        slot = slot("A01", cab, SlotStatus.OCCUPIED, 1, 100, 200);
        item("BK001", ItemType.BOOK, "测试图书", slot);

        // 预约
        ReserveResponse reserved = borrowService.reserve("U002", "BK001");

        // 取件 — 无真实 TCP 设备 → 异步 job 最终 FAILED
        PickupJob job = borrowService.pickup(reserved.recordId(), "U002");
        assertThat(job).isNotNull();

        for (int i = 0; i < 50; i++) {
            PickupJob current = borrowService.getPickupJob(reserved.recordId());
            if (current != null && current.getStatus() != PickupJobStatus.RUNNING) break;
            Thread.sleep(100);
        }
        PickupJob done = borrowService.getPickupJob(reserved.recordId());
        assertThat(done).isNotNull();
        assertThat(done.getStatus()).isEqualTo(PickupJobStatus.FAILED);
    }

    @Test
    void environmentRecord_andThresholdAlarmWork() {
        long alarmsBefore = alarmRepository.findByStatus(AlarmStatus.PENDING).size();
        SensorData hotData = new SensorData(40.0, 85.0, 6000.0, null, null);
        var saved = environmentService.record("sensor_test", hotData);
        assertThat(saved.getTemperature()).isEqualTo(40.0);

        var latest = environmentService.latest("sensor_test");
        assertThat(latest).isNotNull();
        assertThat(latest.getTemperature()).isEqualTo(40.0);

        long alarmsAfter = alarmRepository.findByStatus(AlarmStatus.PENDING).size();
        assertThat(alarmsAfter).isGreaterThan(alarmsBefore);
    }

    @Test
    void deviceStatusLifecycle() {
        // 创建设备记录（不是通过 TCP，直接模拟）
        device("test_dev_01", "ACTUATOR");

        // 查询设备
        var view = deviceService.getView("test_dev_01");
        assertThat(view.deviceId()).isEqualTo("test_dev_01");

        // 状态上报
        DeviceState state = new DeviceState("open", "empty", "running", "stopped", "off");
        StatusReport report = new StatusReport("1.0", "status", "seq1", "2026-01-01T00:00:00",
                "test_dev_01", "server", "test_dev_01", "success",
                true, "ACTUATOR", null, null, null, null, state, null);
        deviceService.updateFromReport(report);
        var updated = deviceService.getView("test_dev_01");
        assertThat(updated.motorState()).isEqualTo("running");

        // 设备列表
        var all = deviceService.listAll();
        assertThat(all).isNotEmpty();
    }

    @Test
    void statisticsDashboard_returnsCounts() {
        // 准备一条数据让统计有内容（用户由 DataInitializer 提供）
        cab = cabinet("cabinet_01", "测试柜");
        slot = slot("A01", cab, SlotStatus.OCCUPIED, 1, 100, 200);
        item("BK001", ItemType.BOOK, "测试图书", slot);
        device("dev_01", "ACTUATOR");

        Map<String, Object> dashboard = statisticsService.dashboard();
        assertThat(dashboard).containsKeys("totalItems", "totalDevices", "onlineDevices", "pendingAlarms");
    }

    // ==================== 测试辅助方法 ====================

    private Cabinet cabinet(String cabinetId, String name) {
        Cabinet c = new Cabinet();
        c.setCabinetId(cabinetId);
        c.setName(name);
        c.setLocation("测试位置");
        c.setOnline(true);
        return cabinetRepository.save(c);
    }

    private Slot slot(String slotId, Cabinet cabinet, SlotStatus status, int testId, int posX, int posY) {
        Slot s = new Slot();
        s.setSlotId(slotId);
        s.setCabinet(cabinet);
        s.setStatus(status);
        s.setTestId(testId);
        s.setPosX(posX);
        s.setPosY(posY);
        return slotRepository.save(s);
    }

    private void item(String itemId, ItemType type, String title, Slot slot) {
        Item i = new Item();
        i.setItemId(itemId);
        i.setType(type);
        i.setTitle(title);
        i.setStatus(ItemStatus.AVAILABLE);
        i.setSlot(slot);
        i.setActionSequence("[{\"device\":\"esp8266_arm_01\",\"cmd\":1,\"blocking\":true}]");
        itemRepository.save(i);
    }

    private void device(String deviceId, String nodeType) {
        DeviceStatus d = new DeviceStatus();
        d.setDeviceId(deviceId);
        d.setNodeType(nodeType);
        d.setOnline(false);
        d.setLastHeartbeat(LocalDateTime.now());
        deviceRepository.save(d);
    }
}
