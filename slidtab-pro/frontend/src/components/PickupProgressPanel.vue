<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="pickup-overlay" @click.self="onOverlayClick">
        <div class="pickup-card">
          <!-- 头部 -->
          <div class="pickup-header">
            <div class="pickup-title">
              <AppIcon name="Box" :size="20" />
              <span>{{ headerTitle }}</span>
            </div>
            <div v-if="!running && !starting" class="pickup-close" @click="onClose">
              <AppIcon name="Close" :size="18" />
            </div>
          </div>

          <!-- 启动中 -->
          <div v-if="starting" class="pickup-body starting">
            <div class="spinner-lg">
              <AppIcon name="Refresh" :size="36" :spin="true" />
            </div>
            <p class="starting-text">正在规划取件路径…</p>
          </div>

          <!-- 进度步骤 -->
          <div v-else-if="running || finished" class="pickup-body">
            <div class="stepper">
              <div
                v-for="(step, idx) in displaySteps"
                :key="idx"
                class="step-row"
                :class="stepState(idx)"
              >
                <div class="step-indicator">
                  <AppIcon v-if="stepState(idx) === 'done'" name="Check" :size="16" />
                  <AppIcon v-else-if="stepState(idx) === 'failed'" name="Close" :size="16" />
                  <AppIcon v-else-if="stepState(idx) === 'active'" name="Refresh" :size="16" :spin="true" />
                  <span v-else class="step-num">{{ idx + 1 }}</span>
                </div>
                <div class="step-content">
                  <div class="step-title">{{ step.title }}</div>
                  <div class="step-desc">{{ step.desc }}</div>
                </div>
              </div>
            </div>

            <!-- 失败提示 -->
            <div v-if="failed" class="fail-block">
              <div class="fail-msg">
                <AppIcon name="Caution" :size="16" />
                <span>{{ failMessage }}</span>
              </div>
              <el-button type="primary" size="small" :loading="starting" @click="onRetry">
                <AppIcon name="Refresh" :size="14" /> 重试
              </el-button>
            </div>

            <!-- 完成提示 -->
            <div v-else-if="finished && !failed" class="success-block">
              <AppIcon name="Success" :size="20" />
              <span>取件成功，请到出货口领取</span>
            </div>
          </div>

          <!-- 底部 -->
          <div v-if="finished" class="pickup-footer">
            <el-button v-if="failed" plain @click="onClose">关闭</el-button>
            <el-button v-else type="primary" @click="onClose">完成</el-button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { simulatePickup, simulatePickupStatus } from '@/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
  itemId: { type: String, default: '' }
})
const emit = defineEmits(['update:visible', 'closed'])

const starting = ref(false)
const running = ref(false)
const finished = ref(false)
const failed = ref(false)
const failMessage = ref('')
const currentStep = ref(0)
const totalSteps = ref(5)
const jobId = ref('')
let pollTimer = null

// 5 步拟人化文案（与后端 ActionExecutor 步骤一一对应）
const STEP_LABELS = [
  { title: '滑台定位到目标格口', desc: '传送机构移动至图书所在位置' },
  { title: '机械臂抓取图书', desc: '机械臂下降并夹取目标图书' },
  { title: '滑台回到出货口', desc: '传送机构携带图书返回出货口' },
  { title: '机械臂放置图书', desc: '机械臂将图书放入出货口' },
  { title: '机械臂复位', desc: '机械臂回到初始位置' }
]

const displaySteps = computed(() => {
  const arr = []
  for (let i = 0; i < totalSteps.value; i++) {
    arr.push(STEP_LABELS[i] || { title: `第 ${i + 1} 步`, desc: '' })
  }
  return arr
})

const headerTitle = computed(() => {
  if (starting.value) return '准备中'
  if (failed.value) return '取件失败'
  if (finished.value) return '取件完成'
  return '取件进行中'
})

function stepState(idx) {
  if (failed.value && idx === currentStep.value) return 'failed'
  if (idx < currentStep.value) return 'done'
  if (idx === currentStep.value && running.value) return 'active'
  if (finished.value && !failed.value && idx < totalSteps.value) return 'done'
  return 'pending'
}

watch(() => props.visible, (v) => {
  if (v && props.itemId) {
    startPickup()
  } else if (!v) {
    cleanup()
  }
})

async function startPickup() {
  starting.value = true
  running.value = false
  finished.value = false
  failed.value = false
  failMessage.value = ''
  currentStep.value = 0

  try {
    const data = await simulatePickup(props.itemId)
    jobId.value = data.jobId
    totalSteps.value = data.steps || 5
    starting.value = false
    running.value = true
    startPolling()
  } catch (e) {
    starting.value = false
    finished.value = true
    failed.value = true
    failMessage.value = e.message || '启动取件失败，请检查设备连接'
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!jobId.value) return
    try {
      const data = await simulatePickupStatus(jobId.value)
      currentStep.value = data.currentStep || 0
      totalSteps.value = data.totalSteps || 5
      const status = data.status
      if (status === 'SUCCESS') {
        running.value = false
        finished.value = true
        failed.value = false
        currentStep.value = totalSteps.value
        stopPolling()
      } else if (status === 'FAILED') {
        running.value = false
        finished.value = true
        failed.value = true
        failMessage.value = data.message || '执行过程中失败'
        stopPolling()
      }
    } catch (e) {
      // 网络抖动忽略，下次重试
    }
  }, 500)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function cleanup() {
  stopPolling()
  starting.value = false
  running.value = false
  finished.value = false
  failed.value = false
  jobId.value = ''
}

function onRetry() {
  if (props.itemId) startPickup()
}

function onClose() {
  cleanup()
  emit('update:visible', false)
  emit('closed')
}

function onOverlayClick() {
  // 进行中不允许点击遮罩关闭，避免误操作
  if (!running.value && !starting.value) onClose()
}

onUnmounted(() => stopPolling())
</script>

<style scoped>
.pickup-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 20px;
}
.pickup-card {
  background: #fff;
  border-radius: 16px;
  width: 100%;
  max-width: 420px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.pickup-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--bc-border, #e5e6eb);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pickup-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--bc-text, #1f2329);
}
.pickup-close {
  cursor: pointer;
  color: var(--bc-text-3, #8f959e);
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: 6px;
}
.pickup-close:hover {
  background: var(--bc-bg, #f4f5f7);
}
.pickup-body {
  padding: 20px;
  overflow-y: auto;
}
.pickup-body.starting {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
}
.spinner-lg {
  color: var(--bc-primary, #00994d);
  margin-bottom: 12px;
}
.starting-text {
  color: var(--bc-text-2, #646a73);
  font-size: 14px;
  margin: 0;
}
.stepper {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.step-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  opacity: 0.5;
  transition: opacity 0.2s;
}
.step-row.done,
.step-row.active,
.step-row.failed {
  opacity: 1;
}
.step-indicator {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--bc-bg, #f4f5f7);
  color: var(--bc-text-3, #8f959e);
  font-size: 13px;
  font-weight: 600;
}
.step-row.done .step-indicator {
  background: var(--bc-primary, #00994d);
  color: #fff;
}
.step-row.active .step-indicator {
  background: var(--bc-primary, #00994d);
  color: #fff;
}
.step-row.failed .step-indicator {
  background: #f56c6c;
  color: #fff;
}
.step-num {
  font-size: 13px;
}
.step-content {
  flex: 1;
  padding-top: 3px;
}
.step-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--bc-text, #1f2329);
  line-height: 1.4;
}
.step-desc {
  font-size: 12px;
  color: var(--bc-text-3, #8f959e);
  margin-top: 2px;
  line-height: 1.4;
}
.step-row.active .step-title {
  color: var(--bc-primary, #00994d);
  font-weight: 600;
}
.step-row.failed .step-title {
  color: #f56c6c;
  font-weight: 600;
}
.fail-block {
  margin-top: 18px;
  padding: 12px;
  background: #fef0f0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}
.fail-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #f56c6c;
  font-size: 13px;
  line-height: 1.4;
}
.success-block {
  margin-top: 18px;
  padding: 14px;
  background: #f0f9eb;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--bc-primary, #00994d);
  font-size: 14px;
  font-weight: 500;
}
.pickup-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--bc-border, #e5e6eb);
  display: flex;
  justify-content: flex-end;
}
.rotating {
  animation: rotate 1s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
