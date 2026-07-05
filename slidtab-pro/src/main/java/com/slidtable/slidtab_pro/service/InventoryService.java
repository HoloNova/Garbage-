package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.common.BusinessException;
import com.slidtable.slidtab_pro.dto.response.ItemView;
import com.slidtable.slidtab_pro.dto.response.SlotView;
import com.slidtable.slidtab_pro.entity.Cabinet;
import com.slidtable.slidtab_pro.entity.Item;
import com.slidtable.slidtab_pro.entity.Slot;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.ItemType;
import com.slidtable.slidtab_pro.repository.CabinetRepository;
import com.slidtable.slidtab_pro.repository.ItemRepository;
import com.slidtable.slidtab_pro.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ItemRepository itemRepository;
    private final SlotRepository slotRepository;
    private final CabinetRepository cabinetRepository;

    public InventoryService(ItemRepository itemRepository, SlotRepository slotRepository,
                            CabinetRepository cabinetRepository) {
        this.itemRepository = itemRepository;
        this.slotRepository = slotRepository;
        this.cabinetRepository = cabinetRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemView> search(String keyword, ItemType type, ItemStatus status) {
        log.info("[库存查询] keyword='{}', type={}, status={}", keyword, type, status);
        List<Item> items;
        if (keyword != null && !keyword.isBlank() && status != null) {
            items = itemRepository.findByTitleContainingIgnoreCaseAndStatus(keyword, status);
        } else if (keyword != null && !keyword.isBlank()) {
            items = itemRepository.findByTitleContainingIgnoreCase(keyword);
        } else if (type != null) {
            items = itemRepository.findByType(type);
        } else if (status != null) {
            items = itemRepository.findByStatus(status);
        } else {
            items = itemRepository.findAll();
        }
        List<ItemView> views = items.stream().map(this::toView).toList();
        log.info("[库存查询] 命中 {} 条", views.size());
        return views;
    }

    @Transactional(readOnly = true)
    public ItemView getItem(String itemId) {
        log.debug("[查询物资] itemId={}", itemId);
        return itemRepository.findByItemId(itemId)
                .map(this::toView)
                .orElseThrow(() -> {
                    log.warn("[查询物资] 不存在: itemId={}", itemId);
                    return new BusinessException(1002, "物资不存在: " + itemId);
                });
    }

    public Item getEntity(String itemId) {
        return itemRepository.findByItemId(itemId)
                .orElseThrow(() -> {
                    log.warn("[查询物资实体] 不存在: itemId={}", itemId);
                    return new BusinessException(1002, "物资不存在: " + itemId);
                });
    }

    @Transactional(readOnly = true)
    public List<SlotView> getSlotsByCabinet(String cabinetId) {
        log.info("[查询柜位] cabinetId={}", cabinetId);
        Cabinet cabinet = cabinetRepository.findByCabinetId(cabinetId)
                .orElseThrow(() -> new BusinessException(1002, "柜体不存在: " + cabinetId));
        List<SlotView> slots = slotRepository.findByCabinet(cabinet).stream()
                .map(this::toSlotView).toList();
        log.info("[查询柜位] cabinet={} 命中 {} 个格口", cabinetId, slots.size());
        return slots;
    }

    @Transactional
    public SlotView updatePosition(String slotId, Integer testId, Integer posX, Integer posY) {
        Slot slot = slotRepository.findBySlotId(slotId)
                .orElseThrow(() -> new BusinessException(1002, "柜位不存在: " + slotId));
        if (testId != null) {
            slot.setTestId(testId);
        }
        if (posX != null) {
            slot.setPosX(posX);
        }
        if (posY != null) {
            slot.setPosY(posY);
        }
        Slot saved = slotRepository.save(slot);
        log.info("[柜位坐标更新] slotId={}, testId={}, posX={}, posY={}",
                slotId, saved.getTestId(), saved.getPosX(), saved.getPosY());
        return toSlotView(saved);
    }

    private ItemView toView(Item item) {
        Slot slot = item.getSlot();
        String slotId = slot != null ? slot.getSlotId() : null;
        String cabinetId = (slot != null && slot.getCabinet() != null) ? slot.getCabinet().getCabinetId() : null;
        return new ItemView(item.getItemId(), item.getType(), item.getTitle(), item.getAuthor(),
                item.getCategory(), item.getStatus(), slotId, cabinetId);
    }

    private SlotView toSlotView(Slot slot) {
        Item item = slot.getItem();
        String itemId = item != null ? item.getItemId() : null;
        String cabinetId = (slot.getCabinet() != null) ? slot.getCabinet().getCabinetId() : null;
        return new SlotView(slot.getSlotId(), slot.getStatus(), slot.getTestId(),
                slot.getPosX(), slot.getPosY(), itemId, cabinetId);
    }
}
