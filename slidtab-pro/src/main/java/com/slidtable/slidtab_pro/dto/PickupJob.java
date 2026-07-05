package com.slidtable.slidtab_pro.dto;

import com.slidtable.slidtab_pro.enums.PickupJobStatus;

import java.time.LocalDateTime;

/**
 * 取件任务内存态。字段 volatile 供执行线程写、查询线程读，无需额外锁。
 * <p>一个 recordId 同一时刻只对应一个 RUNNING job；终态后可被新预约覆盖。</p>
 */
public class PickupJob {

    private final String jobId;
    private final Long recordId;
    private final int totalSteps;
    private final LocalDateTime startedAt;

    private volatile int currentStep;
    private volatile PickupJobStatus status;
    private volatile String message;

    public PickupJob(String jobId, Long recordId, int totalSteps) {
        this.jobId = jobId;
        this.recordId = recordId;
        this.totalSteps = totalSteps;
        this.startedAt = LocalDateTime.now();
        this.currentStep = 0;
        this.status = PickupJobStatus.RUNNING;
        this.message = "已开始";
    }

    public String getJobId() { return jobId; }
    public Long getRecordId() { return recordId; }
    public int getTotalSteps() { return totalSteps; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public int getCurrentStep() { return currentStep; }
    public PickupJobStatus getStatus() { return status; }
    public String getMessage() { return message; }

    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public void setStatus(PickupJobStatus status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
}
