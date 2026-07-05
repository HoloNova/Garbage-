package com.slidtable.slidtab_pro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.ItemType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemView(
        String itemId,
        ItemType type,
        String title,
        String author,
        String category,
        ItemStatus status,
        String slotId,
        String cabinetId
) {
}
