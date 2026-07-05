<template>
  <div class="page-container">
    <h2 class="page-title">预约取件</h2>

    <el-card shadow="never" class="pickup-card">
      <div class="section-head">
        <AppIcon name="Box" :size="18" />
        <span>我的预约</span>
      </div>
      <el-empty v-if="!reservations.length" description="暂无预约记录" :image-size="60" />
      <div v-else class="res-list">
        <div v-for="r in reservations" :key="r.recordId" class="res-item">
          <div class="res-head">
            <span class="res-title">{{ r.itemTitle }}</span>
            <el-tag size="small" :type="statusType(r.status)">{{ statusText(r.status) }}</el-tag>
          </div>
          <div class="res-meta">
            <AppIcon name="Time" :size="12" />
            <span>{{ formatTime(r.borrowTime) }}</span>
            <span class="res-id">#{{ r.recordId }}</span>
          </div>
          <el-button
            v-if="r.status === 'RESERVED'"
            size="small"
            type="primary"
            class="pickup-btn"
            :loading="startingRecordId === r.recordId"
            @click="onPickup(r)"
          >
            <AppIcon name="Box" :size="14" /> 发起取件
          </el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="progressVisible" title="取件进度" width="460px" :close-on-click-modal="false" @close="onCloseProgress">
      <div v-if="currentJob" class="progress-body">
        <el-alert
          :title="statusTitle(currentJob.status)"
          :type="statusAlertType(currentJob.status)"
          :description="currentJob.message"
          show-icon
          :closable="false"
          class="progress-alert"
        />
        <div class="progress-meta">
          <span>步骤：{{ currentJob.currentStep }} / {{ currentJob.totalSteps }}</span>
          <span class="job-id">任务号：{{ currentJob.jobId }}</span>
        </div>
        <el-progress
          :percentage="progressPercent"
          :status="progressStatus"
          :stroke-width="14"
        />
      </div>
      <template #footer>
        <el-button v-if="isTerminal" type="primary" @click="progressVisible = false">关闭</el-button>
        <el-button v-else disabled>执行中...</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pickup, getPickupStatus, borrowHistory } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const reservations = ref([])
const startingRecordId = ref(null)
const progressVisible = ref(false)
const currentJob = ref(null)
const activeRecordId = ref(null)
let pollTimer = null

async function loadHistory() {
  try {
    reservations.value = await borrowHistory(auth.userId) || []
  } catch { /* 忽略 */ }
}

async function onPickup(r) {
  startingRecordId.value = r.recordId
  try {
    const job = await pickup(r.recordId, auth.userId)
    if (!job) {
      ElMessage.warning('取件任务未启动，请重试')
      return
    }
    currentJob.value = job
    activeRecordId.value = r.recordId
    progressVisible.value = true
    startPolling(r.recordId)
  } catch { /* 拦截器已提示 */ } finally {
    startingRecordId.value = null
  }
}

function startPolling(recordId) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const job = await getPickupStatus(recordId)
      if (job) currentJob.value = job
      if (job && isTerminalStatus(job.status)) {
        stopPolling()
        loadHistory()
        if (job.status === 'SUCCESS') ElMessage.success('取件成功')
        else if (job.status === 'FAILED') ElMessage.error('取件失败：' + (job.message || ''))
      }
    } catch { /* 忽略轮询错误 */ }
  }, 600)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function onCloseProgress() {
  stopPolling()
  activeRecordId.value = null
}

const isTerminal = computed(() => isTerminalStatus(currentJob.value?.status))
const progressPercent = computed(() => {
  const j = currentJob.value
  if (!j || !j.totalSteps) return 0
  if (j.status === 'SUCCESS') return 100
  return Math.min(99, Math.round((j.currentStep / j.totalSteps) * 100))
})
const progressStatus = computed(() => {
  const s = currentJob.value?.status
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'exception'
  return undefined
})

function isTerminalStatus(s) { return s === 'SUCCESS' || s === 'FAILED' }
function statusTitle(s) { return { RUNNING: '取件执行中', SUCCESS: '取件完成', FAILED: '取件失败' }[s] || s }
function statusAlertType(s) { return { RUNNING: 'info', SUCCESS: 'success', FAILED: 'error' }[s] || 'info' }
function formatTime(t) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
function statusText(s) { return { RESERVED: '已预约', BORROWED: '已借出', RETURNED: '已归还', OVERDUE: '已超时' }[s] || s }
function statusType(s) { return { RESERVED: 'warning', BORROWED: 'success', RETURNED: 'info', OVERDUE: 'danger' }[s] }

onMounted(loadHistory)
onUnmounted(stopPolling)
</script>

<style scoped>
.pickup-card { margin-bottom: 12px; }
.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
  margin-bottom: 12px;
}
.res-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.res-item {
  background: #fafbfc;
  border: 1px solid var(--bc-border);
  border-radius: 10px;
  padding: 12px;
}
.res-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.res-title {
  font-weight: 600;
  color: var(--bc-text);
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.res-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--bc-text-3);
}
.res-id {
  color: var(--bc-text-3);
  font-family: monospace;
}
.pickup-btn {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.progress-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.progress-alert { margin: 0; }
.progress-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--bc-text-2);
}
.job-id {
  font-family: monospace;
  color: var(--bc-text-3);
}
</style>
