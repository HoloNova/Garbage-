package com.slidtable.slidtab_pro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.slidtable.slidtab_pro.enums.SlotStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SlotView(
        String slotId,
        SlotStatus status,
        Integer testId,
        Integer posX,
        Integer posY,
        String itemId,
        String cabinetId
) {
}
