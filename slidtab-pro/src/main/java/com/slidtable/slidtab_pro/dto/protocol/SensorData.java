package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SensorData(
        Double temperature,
        Double humidity,
        Double light,
        Double weight,
        Double smoke
) {
}
