package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceState(
        @JsonProperty("cabinet_door") String cabinetDoor,
        @JsonProperty("slot_state") String slotState,
        @JsonProperty("motor_state") String motorState,
        @JsonProperty("conveyor_state") String conveyorState,
        @JsonProperty("alarm_state") String alarmState
) {
}
