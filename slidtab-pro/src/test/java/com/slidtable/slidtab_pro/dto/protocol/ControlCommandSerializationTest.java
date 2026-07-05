package com.slidtable.slidtab_pro.dto.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 ControlCommand 在 Jackson 3 (tools.jackson) 下能否正确序列化为 snake_case 字段名。
 * <p>
 * 设备端 ESP8266 期望字段名：msg_type, device_id, protocol_version 等（snake_case）。
 * ControlCommand 用了 com.fasterxml.jackson.annotation.JsonProperty（Jackson 2 注解），
 * 需确认 Jackson 3 是否仍识别这些注解。
 * </p>
 */
class ControlCommandSerializationTest {

    @Test
    void shouldSerializeSnakeCaseFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ControlParams params = new ControlParams(
                null, null, null, null, null, null, null, null, null, 1);
        ControlCommand cmd = ControlCommand.buildCommand(
                "server", "esp8266_arm_01", "esp8266_arm_01", "START_ARM", params);

        String json = mapper.writeValueAsString(cmd);
        System.out.println("=== 实际序列化结果 ===");
        System.out.println(json);
        System.out.println("=====================");

        // 设备端期望 snake_case 字段名
        assertTrue(json.contains("\"msg_type\""),
                "缺少 msg_type 字段，设备端将无法识别消息类型: " + json);
        assertTrue(json.contains("\"device_id\""),
                "缺少 device_id 字段: " + json);
        assertTrue(json.contains("\"protocol_version\""),
                "缺少 protocol_version 字段: " + json);
        assertTrue(json.contains("\"params\""),
                "缺少 params 字段: " + json);
        assertTrue(json.contains("\"cmd\":1"),
                "缺少 params.cmd 字段: " + json);

        // 不应出现 camelCase 字段名
        assertFalse(json.contains("\"msgType\""),
                "出现 camelCase msgType，Jackson 3 未识别 @JsonProperty 注解: " + json);
        assertFalse(json.contains("\"deviceId\""),
                "出现 camelCase deviceId: " + json);
    }
}
