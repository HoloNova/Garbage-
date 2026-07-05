package com.slidtable.slidtab_pro.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReturnRequest(
        @NotBlank(message = "用户ID不能为空") String userId,
        @NotBlank(message = "物资ID不能为空") String itemId,
        String remark
) {
}
