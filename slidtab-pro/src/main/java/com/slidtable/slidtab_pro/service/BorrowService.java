package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.common.BusinessException;
import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.PickupJobCompletedEvent;
import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.dto.protocol.InventoryState;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.dto.response.BorrowRecordView;
import com.slidtable.slidtab_pro.dto.response.ReserveResponse;
import com.slidtable.slidtab_pro.entity.BorrowRecord;
import com.slidtable.slidtab_pro.entity.Item;
import com.slidtable.slidtab_pro.entity.Slot;
import com.slidtable.slidtab_pro.entity.User;
import com.slidtable.slidtab_pro.enums.BorrowStatus;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.PickupJobStatus;
import com.slidtable.slidtab_pro.enums.SlotStatus;
import com.slidtable.slidtab_pro.repository.BorrowRecordRepository;
import com.slidtable.slidtab_pro.service.control.ActionExecutor;
import com.slidtable.slidtab_pro.service.control.PickupJobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BorrowService {

    private static final Logger log = LoggerFactory.getLogger(BorrowService.class);
    private static final int PICKUP_EXPIRE_HOURS = 24;
    private static final String SLIDE_DEVICE = "esp8266_mse_01";
    private static final String ARM_DEVICE = "esp8266_arm_01";
    private static final long RETURN_JOB_TIMEOUT_SECONDS = 90L;

    private final BorrowRecordRepository recordRepository;
    private final UserService userService;
    private final InventoryService inventoryService;
    private final ActionExecutor actionExecutor;
    private final PickupJobStore jobStore;
    private final ObjectMapper objectMapper;

    public BorrowService(BorrowRecordRepository recordRepository, UserService userService,
                         InventoryService inventoryService,
                         ActionExecutor actionExecutor, PickupJobStore jobStore,
                         ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.userService = userService;
        this.inventoryService = inventoryService;
        this.actionExecutor = actionExecutor;
        this.jobStore = jobStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReserveResponse reserve(String userId, String itemId) {
        log.info("[预约] 开始: userId={}, itemId={}", userId, itemId);
        User user = userService.getByUserId(userId);
        Item item = inventoryService.getEntity(itemId);
        if (item.getStatus() != ItemStatus.AVAILABLE) {
            log.warn("[预约] 失败: 物资不可预约 itemId={}, status={}", itemId, item.getStatus());
            throw new BusinessException(1006, "物资当前不可预约: " + item.getStatus());
        }
        Slot slot = item.getSlot();
        if (slot == null) {
            log.warn("[预约] 失败: 物资未关联柜位 itemId={}", itemId);
            throw new BusinessException(1006, "物资未关联柜位");
        }

        item.setStatus(ItemStatus.RESERVED);
        slot.setStatus(SlotStatus.RESERVED);

        BorrowRecord record = new BorrowRecord();
        record.setUser(user);
        record.setItem(item);
        record.setStatus(BorrowStatus.RESERVED);
        record.setBorrowTime(LocalDateTime.now());
        recordRepository.save(record);

        log.info("[预约] 成功: recordId={}, userId={}, itemId={}, cabinet={}, slot={}, 过期时间={}",
                record.getId(), userId, itemId, slot.getCabinet().getCabinetId(), slot.getSlotId(),
                LocalDateTime.now().plusHours(PICKUP_EXPIRE_HOURS));
        return new ReserveResponse(record.getId(), slot.getSlotId(),
                slot.getCabinet().getCabinetId(), slot.getCabinet().getName(),
                slot.getCabinet().getLocation(), LocalDateTime.now().plusHours(PICKUP_EXPIRE_HOURS));
    }

    /**
     * 启动取件：异步执行 item.actionSequence，立即返回 job 摘要。前端轮询 status 接口。
     */
    @Transactional
    public PickupJob pickup(Long recordId, String userId) {
        log.info("[取件] 启动: recordId={}, userId={}", recordId, userId);
        BorrowRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(1002, "借阅记录不存在"));
        if (!record.getUser().getUserId().equals(userId)) {
            throw new BusinessException(1003, "无权操作此记录");
        }
        if (record.getStatus() != BorrowStatus.RESERVED) {
            throw new BusinessException(1007, "该预约已处理或已取消");
        }

        PickupJob existing = jobStore.get(recordId);
        if (existing != null && existing.getStatus() == PickupJobStatus.RUNNING) {
            throw new BusinessException(1008, "取件正在进行中，请勿重复触发");
        }

        Item item = record.getItem();
        List<ActionStep> steps = parseActionSequence(item.getActionSequence());
        if (steps.isEmpty()) {
            throw new BusinessException(1009, "物资未配置动作序列");
        }

        String jobId = "PJ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("[取件] 派发动作序列: recordId={}, jobId={}, steps={}, itemId={}",
                recordId, jobId, steps.size(), item.getItemId());
        actionExecutor.execute(jobId, recordId, steps);

        return jobStore.get(recordId);
    }

    public PickupJob getPickupJob(Long recordId) {
        return jobStore.get(recordId);
    }

    /**
     * ActionExecutor 完成动作序列后发布事件，此处监听并更新借阅记录状态。
     * <p>注意：在 action-executor 线程中同步执行，内部 try-catch 避免异常反噬发布者。</p>
     */
    @EventListener
    @Transactional
    public void onPickupComplete(PickupJobCompletedEvent event) {
        try {
            BorrowRecord record = recordRepository.findById(event.recordId()).orElse(null);
            if (record == null) {
                log.warn("[取件完成] 记录不存在: recordId={}", event.recordId());
                return;
            }
            if (record.getStatus() != BorrowStatus.RESERVED) {
                log.warn("[取件完成] 记录状态非 RESERVED，跳过: recordId={}, status={}",
                        event.recordId(), record.getStatus());
                return;
            }
            Item item = record.getItem();
            Slot slot = item.getSlot();
            if (event.success()) {
                record.setStatus(BorrowStatus.BORROWED);
                record.setBorrowTime(LocalDateTime.now());
                item.setStatus(ItemStatus.BORROWED);
                slot.setStatus(SlotStatus.TAKEN);
                recordRepository.save(record);
                log.info("[取件完成] 成功: recordId={}, itemId={}", event.recordId(), item.getItemId());
            } else {
                log.warn("[取件完成] 动作失败，保持 RESERVED: recordId={}, itemId={}",
                        event.recordId(), item.getItemId());
            }
        } catch (Exception e) {
            log.error("[取件完成] 监听器异常: recordId={}", event.recordId(), e);
        }
    }

    @Transactional
    public StatusReport returnItem(String userId, String itemId, String remark) {
        log.info("[归还] 开始: userId={}, itemId={}, remark={}", userId, itemId, remark);
        User user = userService.getByUserId(userId);
        Item item = inventoryService.getEntity(itemId);
        BorrowRecord record = recordRepository.findByUserAndItemAndStatus(user, item, BorrowStatus.BORROWED)
                .orElseThrow(() -> {
                    log.warn("[归还] 失败: 未找到在借记录 userId={}, itemId={}", userId, itemId);
                    return new BusinessException(1002, "未找到对应的借出记录");
                });

        Slot slot = item.getSlot();
        List<ActionStep> steps = buildReturnActionSequence(slot);
        if (steps.isEmpty()) {
            throw new BusinessException(1009, "归还动作序列配置错误");
        }

        String jobId = "RT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("[归还] 派发动作序列: recordId={}, jobId={}, steps={}, itemId={}, slot={}",
                record.getId(), jobId, steps.size(), itemId, slot.getSlotId());
        actionExecutor.execute(jobId, record.getId(), steps);

        PickupJob job = waitForJobCompletion(record.getId());
        if (job == null || job.getStatus() != PickupJobStatus.SUCCESS) {
            String msg = job != null ? job.getMessage() : "等待超时";
            log.error("[归还] 动作失败: itemId={}, msg={}", itemId, msg);
            throw new BusinessException(1007, "归还入库失败: " + msg);
        }

        record.setStatus(BorrowStatus.RETURNED);
        record.setReturnTime(LocalDateTime.now());
        item.setStatus(ItemStatus.AVAILABLE);
        slot.setStatus(SlotStatus.OCCUPIED);
        recordRepository.save(record);

        log.info("[归还] 成功: user={}, item={}, remark={}", user.getUserId(), itemId, remark);
        return buildReport(record, "0000", "return success");
    }

    /**
     * 归还动作序列（取件的逆序）：机械臂抓取→滑台移动到格口→机械臂放置→滑台回出货口→机械臂复位。
     * 全部阻塞步，与取件共用 ActionExecutor 单线程 executor，保证不并发。
     */
    private List<ActionStep> buildReturnActionSequence(Slot slot) {
        int slidePos = slot.getTestId() != null ? (slot.getTestId() - 1) % 4 : 0;
        return List.of(
                new ActionStep(ARM_DEVICE, 1, true),
                new ActionStep(SLIDE_DEVICE, slidePos, true),
                new ActionStep(ARM_DEVICE, 2, true),
                new ActionStep(SLIDE_DEVICE, 0, true),
                new ActionStep(ARM_DEVICE, 0, true)
        );
    }

    /**
     * 同步轮询 PickupJobStore 等待动作序列完成。
     * <p>归还是用户在场操作，需立即反馈；同时走 ActionExecutor 单线程队列保证不与取件并发。</p>
     */
    private PickupJob waitForJobCompletion(Long recordId) {
        long deadline = System.currentTimeMillis() + RETURN_JOB_TIMEOUT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            PickupJob job = jobStore.get(recordId);
            if (job != null && (job.getStatus() == PickupJobStatus.SUCCESS
                    || job.getStatus() == PickupJobStatus.FAILED)) {
                return job;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public List<BorrowRecordView> history(String userId) {
        User user = userService.getByUserId(userId);
        List<BorrowRecordView> views = recordRepository.findByUser(user).stream()
                .map(r -> new BorrowRecordView(r.getId(), r.getUser().getUserId(), r.getUser().getName(),
                        r.getItem().getItemId(), r.getItem().getTitle(), r.getBorrowTime(),
                        r.getReturnTime(), r.getStatus().name()))
                .toList();
        log.info("[借还历史] userId={}, 命中 {} 条", userId, views.size());
        return views;
    }

    private StatusReport buildReport(BorrowRecord record, String code, String msg) {
        Item item = record.getItem();
        Slot slot = item.getSlot();
        InventoryState inventoryState = new InventoryState(item.getItemId(),
                slot != null ? slot.getSlotId() : null, record.getStatus().name().toLowerCase());
        return new StatusReport("1.0", "status", String.valueOf(System.currentTimeMillis()),
                LocalDateTime.now().toString(), "server", "app", "server",
                "success", true, "gateway", "RESERVE", code, msg, null, null, inventoryState);
    }

    private List<ActionStep> parseActionSequence(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ActionStep>>() {});
        } catch (Exception e) {
            log.warn("[动作序列] 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
