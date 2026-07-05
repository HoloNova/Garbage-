package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 设备端 GET /device/info 的标准响应格式。
 * 扫描器根据此响应匹配 device_id 与 yaml 中的 actuator-id。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceInfoResponse(
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("node_type") String nodeType,
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("firmware_version") String firmwareVersion
) {
}
