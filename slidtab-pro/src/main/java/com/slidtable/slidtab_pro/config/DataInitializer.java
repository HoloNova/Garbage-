package com.slidtable.slidtab_pro.config;

import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.entity.*;
import com.slidtable.slidtab_pro.enums.*;
import com.slidtable.slidtab_pro.repository.ActionTemplateRepository;
import com.slidtable.slidtab_pro.repository.CabinetRepository;
import com.slidtable.slidtab_pro.repository.DeviceStatusRepository;
import com.slidtable.slidtab_pro.repository.EnvironmentDataRepository;
import com.slidtable.slidtab_pro.repository.ItemRepository;
import com.slidtable.slidtab_pro.repository.SlotRepository;
import com.slidtable.slidtab_pro.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 种子数据初始化。
 * <p>
 * 播种 3 个演示用户 + 20 本图书（每本带随机生成的动作序列）。
 * 硬件/设备相关数据由 TCP 连接和业务流程驱动生成。
 * </p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String UNIFIED_SENSOR_ID = "esp8266_sensor_01";
    private static final String SLIDE_DEVICE = "esp8266_mse_01";
    private static final String ARM_DEVICE = "esp8266_arm_01";

    private static final String[] TITLES = {
            "深入理解计算机系统", "代码整洁之道", "设计模式", "算法导论", "重构",
            "Java编程思想", "Spring实战", "数据库系统概念", "计算机网络", "操作系统导论",
            "人工智能导论", "机器学习", "深度学习", "Python编程", "C程序设计语言",
            "编译原理", "计算机组成原理", "软件工程", "分布式系统", "区块链原理"
    };

    private static final String[] AUTHORS = {
            "A. S. Tanenbaum", "Brian Goetz", "Joshua Bloch", "Robert C. Martin", "Erich Gamma",
            "Thomas H. Cormen", "周志明", "吴军", "李刚", "张三丰"
    };

    private static final String[] CATEGORIES = {"计算机", "编程", "算法", "系统", "理论"};

    /**
     * 每本图书对应的滑台位置（硬编码）。
     * <ul>
     *   <li>位置 0 = 出货口（不放书）</li>
     *   <li>位置 1 = 第一层：BK001-BK007</li>
     *   <li>位置 2 = 第二层：BK008-BK014</li>
     *   <li>位置 3 = 第三层：BK015-BK020</li>
     * </ul>
     */
    private static final int[] BOOK_SLIDE_POSITIONS = {
            1, 1, 1, 1, 1, 1, 1,    // BK001-BK007 第一层
            2, 2, 2, 2, 2, 2, 2,    // BK008-BK014 第二层
            3, 3, 3, 3, 3, 3        // BK015-BK020 第三层
    };

    private final UserRepository userRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final EnvironmentDataRepository environmentDataRepository;
    private final CabinetRepository cabinetRepository;
    private final SlotRepository slotRepository;
    private final ItemRepository itemRepository;
    private final ActionTemplateRepository actionTemplateRepository;
    private final ObjectMapper objectMapper;

    public DataInitializer(UserRepository userRepository,
                           DeviceStatusRepository deviceStatusRepository,
                           EnvironmentDataRepository environmentDataRepository,
                           CabinetRepository cabinetRepository,
                           SlotRepository slotRepository,
                           ItemRepository itemRepository,
                           ActionTemplateRepository actionTemplateRepository,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.deviceStatusRepository = deviceStatusRepository;
        this.environmentDataRepository = environmentDataRepository;
        this.cabinetRepository = cabinetRepository;
        this.slotRepository = slotRepository;
        this.itemRepository = itemRepository;
        this.actionTemplateRepository = actionTemplateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        cleanupOldSensorDevices();
        seedActionTemplates();

        if (userRepository.count() > 0) {
            log.info("演示用户已存在，跳过初始化");
            return;
        }

        log.info("创建演示用户...");
        user("U001", "管理员", "13800000001", "S001", UserIdentity.ADMIN);
        user("U002", "张三", "13800000002", "S002", UserIdentity.USER);
        user("U003", "李四", "13800000003", "S003", UserIdentity.USER);
        log.info("演示用户创建完成: {} 人", userRepository.count());

        seedBooks();
        log.info("硬件/设备数据由 TCP 连接和业务流程自动生成");
    }

    /**
     * 播种默认动作模板：标准 5 步取件序列。
     * 即使演示用户已存在，模板表为空时也会补播种。
     */
    private void seedActionTemplates() {
        if (actionTemplateRepository.count() > 0) {
            log.info("动作模板已存在，跳过播种");
            return;
        }
        List<ActionStep> seq = new ArrayList<>();
        seq.add(new ActionStep(SLIDE_DEVICE, 1, true));   // 滑台移动到目标格口
        seq.add(new ActionStep(ARM_DEVICE, 1, true));    // 机械臂抓取
        seq.add(new ActionStep(SLIDE_DEVICE, 0, true));  // 滑台回到出货口
        seq.add(new ActionStep(ARM_DEVICE, 2, true));    // 机械臂放置
        seq.add(new ActionStep(ARM_DEVICE, 0, true));    // 机械臂复位
        try {
            ActionTemplate t = new ActionTemplate();
            t.setName("标准取件序列");
            t.setDescription("滑台定位→机械臂抓取→滑台回出货口→机械臂放置→机械臂复位");
            t.setSequenceJson(objectMapper.writeValueAsString(seq));
            actionTemplateRepository.save(t);
            log.info("播种动作模板: {}", t.getName());
        } catch (Exception e) {
            log.warn("播种动作模板失败: {}", e.getMessage());
        }
    }

    private void seedBooks() {
        if (itemRepository.count() > 0) {
            log.info("图书已存在，跳过播种");
            return;
        }

        Cabinet cab = new Cabinet();
        cab.setCabinetId("cabinet_01");
        cab.setName("智能图书柜");
        cab.setLocation("演示大厅");
        cab.setOnline(true);
        cabinetRepository.save(cab);

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int seeded = 0;
        for (int i = 0; i < TITLES.length; i++) {
            String slotId = String.format("S%02d", i + 1);

            Slot slot = new Slot();
            slot.setSlotId(slotId);
            slot.setCabinet(cab);
            slot.setStatus(SlotStatus.OCCUPIED);
            slot.setTestId(i + 1);
            slot.setPosX(rnd.nextInt(100, 600));
            slot.setPosY(rnd.nextInt(100, 400));
            slotRepository.save(slot);

            Item item = new Item();
            item.setItemId(String.format("BK%03d", i + 1));
            item.setType(ItemType.BOOK);
            item.setTitle(TITLES[i]);
            item.setAuthor(AUTHORS[i % AUTHORS.length]);
            item.setCategory(CATEGORIES[i % CATEGORIES.length]);
            item.setStatus(ItemStatus.AVAILABLE);
            item.setSlot(slot);
            item.setActionSequence(pickupActionSequence(i));
            itemRepository.save(item);
            seeded++;
        }

        log.info("播种图书完成: {} 本", seeded);
    }

    /**
     * 生成取件动作序列：滑台定位→机械臂抓取→滑台回出货口→机械臂放置→机械臂复位。
     * 全部 blocking 步，每步发送后等待所有设备解锁（全局屏障）再执行下一步。
     *
     * @param bookIndex 图书索引（0-19），对应 BOOK_SLIDE_POSITIONS 中的滑台位置
     */
    private String pickupActionSequence(int bookIndex) {
        int slidePos = BOOK_SLIDE_POSITIONS[bookIndex];
        List<ActionStep> seq = new ArrayList<>();
        seq.add(new ActionStep(SLIDE_DEVICE, slidePos, true)); // 滑台移动到图书所在层
        seq.add(new ActionStep(ARM_DEVICE, 1, true));          // 机械臂抓取
        seq.add(new ActionStep(SLIDE_DEVICE, 0, true));        // 滑台回到出货口
        seq.add(new ActionStep(ARM_DEVICE, 2, true));          // 机械臂放置
        seq.add(new ActionStep(ARM_DEVICE, 0, true));          // 机械臂复位
        try {
            return objectMapper.writeValueAsString(seq);
        } catch (Exception e) {
            log.warn("动作序列序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private void cleanupOldSensorDevices() {
        int deletedDevices = deviceStatusRepository.deleteByDeviceIdStartingWith("sensor_");
        if (deletedDevices > 0) {
            log.info("[清理] 删除了 {} 个旧传感器设备记录（deviceId 以 sensor_ 开头）", deletedDevices);
        }
        int deletedEnv = environmentDataRepository.deleteByDeviceIdStartingWith("sensor_");
        if (deletedEnv > 0) {
            log.info("[清理] 删除了 {} 条旧传感器环境数据（deviceId 以 sensor_ 开头）", deletedEnv);
        }
    }

    private User user(String userId, String name, String phone, String studentId, UserIdentity identity) {
        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setPhone(phone);
        u.setStudentId(studentId);
        u.setIdentity(identity);
        return userRepository.save(u);
    }
}
