package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ControlCommand(
        @NotBlank @JsonProperty("protocol_version") String protocolVersion,
        @NotBlank @JsonProperty("msg_type") String msgType,
        @NotBlank @JsonProperty("seq") String seq,
        @NotBlank @JsonProperty("timestamp") String timestamp,
        @NotBlank @JsonProperty("source") String source,
        @NotBlank @JsonProperty("target") String target,
        @NotBlank @JsonProperty("device_id") String deviceId,
        @NotBlank @JsonProperty("command") String command,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("timeout_ms") Long timeoutMs,
        @JsonProperty("params") ControlParams params
) {
    private static final AtomicLong SEQ_COUNTER = new AtomicLong(0);
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 构造一条控制指令。seq 全局唯一（毫秒时间戳 + 自增计数器），timeout 默认 5s。
     */
    public static ControlCommand buildCommand(String source, String target, String deviceId,
                                              String command, ControlParams params) {
        return new ControlCommand(
                "1.0", "control", nextSeq(), now(), source, target, deviceId, command,
                0, 5000L, params
        );
    }

    private static String nextSeq() {
        return System.currentTimeMillis() + "-" + SEQ_COUNTER.incrementAndGet();
    }

    private static String now() {
        return DTF.format(Instant.now());
    }
}
