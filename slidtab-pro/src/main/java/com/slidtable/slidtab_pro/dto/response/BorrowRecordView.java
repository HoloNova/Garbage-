package com.slidtable.slidtab_pro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BorrowRecordView(
        Long recordId,
        String userId,
        String userName,
        String itemId,
        String itemTitle,
        LocalDateTime borrowTime,
        LocalDateTime returnTime,
        String status
) {
}
