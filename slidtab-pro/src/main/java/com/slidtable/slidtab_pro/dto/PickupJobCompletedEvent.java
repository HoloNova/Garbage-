package com.slidtable.slidtab_pro.dto;

/**
 * 取件任务完成事件。ActionExecutor 在 runJob 终态时发布，BorrowService 监听并更新 BorrowRecord 状态。
 *
 * @param recordId 借阅记录 ID
 * @param success  动作序列是否全部成功
 */
public record PickupJobCompletedEvent(Long recordId, boolean success) {
}
