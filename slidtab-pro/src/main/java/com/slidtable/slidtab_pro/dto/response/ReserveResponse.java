package com.slidtable.slidtab_pro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReserveResponse(
        Long recordId,
        String slotId,
        String cabinetId,
        String cabinetName,
        String cabinetLocation,
        LocalDateTime expireTime
) {
}
