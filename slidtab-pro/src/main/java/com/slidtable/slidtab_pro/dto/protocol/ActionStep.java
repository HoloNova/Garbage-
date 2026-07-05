package com.slidtable.slidtab_pro.dto.protocol;

/**
 * 单步动作指令：向指定设备下发一个数字命令编号。
 * <p>
 * 取件时按 {@code Item.actionSequence} 中的顺序依次执行。
 * 阻塞步等待设备回执后继续；异步步发完即继续下一步。
 * </p>
 *
 * @param device   目标设备 ID（如 esp8266_arm_01 / esp8266_mse_01）
 * @param cmd      命令编号（机械臂 0-2、滑台 0-3）
 * @param blocking 是否等待设备回执
 */
public record ActionStep(String device, int cmd, boolean blocking) {
}
