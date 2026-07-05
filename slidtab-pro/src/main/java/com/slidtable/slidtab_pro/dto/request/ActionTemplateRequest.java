package com.slidtable.slidtab_pro.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建/更新动作模板的请求体。sequenceJson 是 ActionStep 列表的 JSON 字符串。
 */
public record ActionTemplateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String sequenceJson
) {
}
