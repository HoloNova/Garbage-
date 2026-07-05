package com.slidtable.slidtab_pro.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "手机号不能为空") String phone,
        @NotBlank(message = "学号/工号不能为空") String studentId
) {
}
