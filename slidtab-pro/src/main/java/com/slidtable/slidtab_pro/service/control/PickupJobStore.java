package com.slidtable.slidtab_pro.service.control;

import com.slidtable.slidtab_pro.dto.PickupJob;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 取件任务内存态仓库。
 * <p>
 * 同时按 recordId 和 jobId 建立索引：
 * <ul>
 *   <li>recordId 索引供正常借阅流程查询（一个 recordId 同一时刻只对应一个 RUNNING job）</li>
 *   <li>jobId 索引供模拟取货查询（recordId=0 会冲突，必须用 jobId）</li>
 * </ul>
 * 纯内存、无持久化；服务重启即丢失。
 */
@Component
public class PickupJobStore {

    private final ConcurrentHashMap<Long, PickupJob> jobsByRecordId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PickupJob> jobsByJobId = new ConcurrentHashMap<>();

    public void put(PickupJob job) {
        jobsByRecordId.put(job.getRecordId(), job);
        jobsByJobId.put(job.getJobId(), job);
    }

    public PickupJob get(Long recordId) {
        return jobsByRecordId.get(recordId);
    }

    public PickupJob getByJobId(String jobId) {
        return jobsByJobId.get(jobId);
    }

    public void remove(Long recordId) {
        PickupJob job = jobsByRecordId.remove(recordId);
        if (job != null) {
            jobsByJobId.remove(job.getJobId());
        }
    }
}
