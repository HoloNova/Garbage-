package com.slidtable.slidtab_pro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceStatusView(
        String deviceId,
        String nodeType,
        boolean online,
        String motorState,
        String cabinetDoor,
        String conveyorState,
        String alarmState,
        LocalDateTime lastHeartbeat
) {
}
