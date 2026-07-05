package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.Cabinet;
import com.slidtable.slidtab_pro.entity.Slot;
import com.slidtable.slidtab_pro.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    Optional<Slot> findBySlotId(String slotId);

    List<Slot> findByCabinet(Cabinet cabinet);

    List<Slot> findByStatus(SlotStatus status);
}
