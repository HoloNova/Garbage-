package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusReport(
        @NotBlank @JsonProperty("protocol_version") String protocolVersion,
        @NotBlank @JsonProperty("msg_type") String msgType,
        @NotBlank @JsonProperty("seq") String seq,
        @NotBlank @JsonProperty("timestamp") String timestamp,
        @NotBlank @JsonProperty("source") String source,
        @NotBlank @JsonProperty("target") String target,
        @NotBlank @JsonProperty("device_id") String deviceId,
        @NotBlank @JsonProperty("status") String status,
        @JsonProperty("online") Boolean online,
        @JsonProperty("node_type") String nodeType,
        @JsonProperty("action") String action,
        @JsonProperty("result_code") String resultCode,
        @JsonProperty("result_msg") String resultMsg,
        @JsonProperty("sensor_data") SensorData sensorData,
        @JsonProperty("device_state") DeviceState deviceState,
        @JsonProperty("inventory_state") InventoryState inventoryState
) {
}
