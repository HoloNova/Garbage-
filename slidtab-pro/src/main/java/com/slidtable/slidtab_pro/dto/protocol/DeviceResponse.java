package com.slidtable.slidtab_pro.dto.protocol;

/**
 * 设备响应的缓存记录。
 * <p>
 * 每当 TCP 设备发来一条消息（非心跳），
 * 就会被记录为一条 DeviceResponse 供前端查询。
 * </p>
 */
public record DeviceResponse(
        String content,
        long timestamp,
        String deviceId
) {
}
