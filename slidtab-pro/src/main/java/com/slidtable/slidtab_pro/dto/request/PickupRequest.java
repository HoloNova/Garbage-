package com.slidtable.slidtab_pro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PickupRequest(
        @NotNull(message = "借阅记录 ID 不能为空") Long recordId,
        @NotBlank(message = "用户 ID 不能为空") String userId
) {
}
