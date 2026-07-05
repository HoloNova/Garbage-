package com.slidtable.slidtab_pro.dto.protocol;

/**
 * 设备回执结果。
 * <p>success=true 表示 result_code=0000；其余均为失败，message 携带原始 result_msg。</p>
 */
public record AckResult(boolean success, String message) {
}
