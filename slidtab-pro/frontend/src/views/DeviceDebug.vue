<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="debug-header">
      <h2 class="page-title" style="margin:0">设备调试</h2>
      <div class="header-actions">
        <el-tag v-if="devices.length" type="success" size="small" effect="plain">
          {{ onlineCount }} / {{ devices.length }} 在线
        </el-tag>
        <el-button size="small" :loading="loading.devices" @click="loadDevices" circle>
          <AppIcon name="Refresh" :size="16" />
        </el-button>
      </div>
    </div>

    <!-- 提示：无设备 -->
    <el-empty v-if="!loading.devices && !devices.length" description="暂无 TCP 设备连接" :image-size="80">
      <template #description>
        <p style="color:var(--bc-text-3);font-size:13px;margin-bottom:12px">
          请确保设备已通过 TCP 连接到服务器 ({{ tcpHost }}:{{ tcpPort }})
        </p>
        <el-button size="small" @click="loadDevices">重新加载</el-button>
      </template>
    </el-empty>

    <!-- 设备列表 -->
    <template v-if="devices.length">
      <!-- 设备卡片列表 -->
      <div class="dev-list">
        <div
          v-for="d in devices"
          :key="d.deviceId"
          class="dev-card"
          :class="{ active: selectedDevice === d.deviceId }"
          @click="d.connected ? (selectedDevice = d.deviceId) : null"
        >
          <div class="dev-card-head">
            <div class="dev-name">
              <span class="status-dot" :class="d.connected ? 'online' : 'offline'" />
              <span class="dev-id">{{ d.deviceId }}</span>
              <span v-if="d.identified === false" class="dev-unidentified">未识别</span>
            </div>
            <el-tag :type="d.connected ? 'success' : 'info'" size="small" effect="plain">
              {{ d.connected ? '在线' : '离线' }}
            </el-tag>
          </div>
          <div class="dev-meta">
            <AppIcon name="Monitor" :size="12" />
            <span>{{ d.nodeType || '-' }}</span>
            <span class="meta-sep">|</span>
            <AppIcon name="Ipi" :size="12" />
            <span>{{ d.ip }}:{{ d.port }}</span>
          </div>
          <div v-if="d.connected" class="dev-card-actions">
            <el-button size="small" :loading="testing === d.deviceId" @click.stop="testDevice(d.deviceId)">
              测试连接
            </el-button>
            <el-button size="small" @click.stop="clearResponses(d.deviceId)">
              清空日志
            </el-button>
          </div>
          <!-- 测试结果 -->
          <div v-if="testResults[d.deviceId]" class="test-result" :class="testResults[d.deviceId].ok ? 'ok' : 'fail'">
            {{ testResults[d.deviceId].message }}
          </div>
        </div>
      </div>

      <!-- 命令终端 -->
      <div class="terminal-section">
        <div class="section-label">命令终端</div>
        <div class="terminal-card">
          <!-- 设备选择 -->
          <div class="term-row">
            <el-select v-model="selectedDevice" placeholder="选择目标设备" style="width:100%">
              <el-option
                v-for="d in devices"
                :key="d.deviceId"
                :label="d.deviceId + (d.identified === false ? ' (未识别)' : '') + (d.connected ? ' (在线)' : ' (离线)')"
                :value="d.deviceId"
                :disabled="!d.connected"
              />
            </el-select>
          </div>
          <!-- 指令输入 -->
          <div class="term-row">
            <el-input
              v-model="commandInput"
              type="textarea"
              :rows="4"
              placeholder='输入原始指令（纯文本 / JSON），例如：&#10;{"msg_type":"control","command":"MOVE_TO_SLOT"}'
              :disabled="!selectedDevice || !isSelectedOnline"
            />
          </div>
          <!-- 操作按钮 -->
          <div class="term-row term-actions">
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!canSend"
              @click="sendCommand"
            >
              发送
            </el-button>
            <el-button
              :loading="broadcasting"
              :disabled="!commandInput.trim() || !onlineCount"
              @click="broadcastCommand"
            >
              广播
            </el-button>
            <el-button
              :disabled="!selectedDevice"
              @click="loadResponses"
            >
              刷新日志
            </el-button>
          </div>
          <div v-if="sendError" class="send-error">{{ sendError }}</div>
        </div>
      </div>

      <!-- 通信日志 -->
      <div class="log-section">
        <div class="section-label">
          通信日志
          <span class="log-count">{{ responses.length }} 条</span>
        </div>
        <div ref="logRef" class="log-panel">
          <div v-if="!responses.length" class="log-empty">暂无通信记录，发送指令后将在此显示</div>
          <div v-for="(r, i) in responses" :key="i" class="log-entry" :class="r.direction">
            <div class="log-time">{{ formatTime(r.timestamp) }}</div>
            <div class="log-badge">{{ r.direction === 'send' ? '→' : '←' }}</div>
            <pre class="log-content">{{ r.content }}</pre>
          </div>
        </div>
      </div>
    </template>

    <!-- 加载状态 -->
    <div v-if="loading.devices && !devices.length" class="loading-state">
      <el-skeleton :rows="3" animated />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tcpDevices, tcpSendRaw, tcpBroadcast, tcpResponses, tcpClearResponses, tcpTestConnection } from '@/api'

// ===== 状态 =====
const devices = ref([])
const responses = ref([])
const selectedDevice = ref('')
const commandInput = ref('')
const sendError = ref('')
const testResults = ref({})

const loading = ref({ devices: false })
const sending = ref(false)
const broadcasting = ref(false)
const testing = ref('')

const logRef = ref(null)
let pollTimer = null
let responseTimer = null
let pendingResponseTimer = null

// ===== 常量 =====
const tcpHost = window.location.hostname || 'localhost'
const tcpPort = '5000'

// ===== 计算属性 =====
const onlineCount = computed(() => devices.value.filter(d => d.connected).length)

const isSelectedOnline = computed(() => {
  const d = devices.value.find(d => d.deviceId === selectedDevice.value)
  return d?.connected ?? false
})

const canSend = computed(() => {
  return selectedDevice.value && isSelectedOnline.value && commandInput.value.trim()
})

// ===== 方法 =====

/** 加载设备列表 */
async function loadDevices() {
  loading.value.devices = true
  try {
    const list = await tcpDevices() || []
    devices.value = list
    // 清理与当前连接状态矛盾的测试结果（避免"连接成功"与"离线"同屏）
    const next = {}
    for (const d of list) {
      const tr = testResults.value[d.deviceId]
      if (tr && tr.ok === d.connected) next[d.deviceId] = tr
    }
    testResults.value = next
    // 如果选中的设备已不在列表，清空选择
    if (selectedDevice.value && !list.find(d => d.deviceId === selectedDevice.value)) {
      selectedDevice.value = ''
    }
    // 自动选择第一个在线设备
    if (!selectedDevice.value && list.length) {
      const firstOnline = list.find(d => d.connected)
      if (firstOnline) selectedDevice.value = firstOnline.deviceId
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value.devices = false
  }
}

/**
 * 合并加载响应日志（保留本地发送记录）。
 * 后端响应以 receive 方向合并到日志中，不会覆盖本地 send 条目。
 */
async function loadResponses(targetDeviceId) {
  const deviceId = targetDeviceId || selectedDevice.value
  if (!deviceId) return
  try {
    const list = await tcpResponses(deviceId) || []
    const receiveEntries = list.map(r => ({
      ...r,
      direction: 'receive',
      timestamp: r.timestamp || Date.now()
    }))
    // 合并：保留本地 send 条目，替换同一设备的后端 receive 条目
    const sends = responses.value.filter(e => e.direction === 'send')
    responses.value = [...sends, ...receiveEntries]
    scrollToBottom()
  } catch {
    // 静默失败
  }
}

/** 发送指令 */
async function sendCommand() {
  if (!canSend.value) return
  sendError.value = ''
  sending.value = true

  const deviceId = selectedDevice.value
  const content = commandInput.value.trim()

  // 在日志中先添加"已发送"条目
  const sendEntry = {
    content,
    direction: 'send',
    timestamp: Date.now(),
    device_id: deviceId
  }
  responses.value = [...responses.value, sendEntry]
  scrollToBottom()

  try {
    await tcpSendRaw(deviceId, content)
    // 发送成功后短暂延迟后加载设备响应（捕获当前 deviceId 防竞态）
    if (pendingResponseTimer) clearTimeout(pendingResponseTimer)
    pendingResponseTimer = setTimeout(() => loadResponses(deviceId), 500)
  } catch (e) {
    // 发送失败：移除刚才添加的乐观条目
    responses.value = responses.value.filter(e => e !== sendEntry)
    sendError.value = '发送失败: ' + (e.message || '未知错误')
  } finally {
    sending.value = false
  }
}

/** 广播指令 */
async function broadcastCommand() {
  if (!commandInput.value.trim() || !onlineCount.value) return
  broadcasting.value = true
  try {
    const result = await tcpBroadcast(commandInput.value.trim())
    ElMessage.success(`已广播到 ${result.deviceCount || 0} 台设备`)
    responses.value = [...responses.value, {
      content: `[广播] ${commandInput.value.trim()}`,
      direction: 'send',
      timestamp: Date.now(),
      device_id: '*'
    }]
  } catch {
    ElMessage.error('广播失败，请检查连接')
  } finally {
    broadcasting.value = false
    scrollToBottom()
  }
}

/** 测试设备连接 */
async function testDevice(deviceId) {
  testing.value = deviceId
  try {
    const result = await tcpTestConnection(deviceId)
    testResults.value = {
      ...testResults.value,
      [deviceId]: {
        ok: result.connected,
        message: result.message || (result.connected ? '连接正常' : '设备未连接')
      }
    }
    if (result.connected) {
      ElMessage.success(`设备 ${deviceId} 连接正常`)
    } else {
      ElMessage.warning(`设备 ${deviceId} 未连接`)
    }
  } catch {
    testResults.value = {
      ...testResults.value,
      [deviceId]: { ok: false, message: '测试请求失败' }
    }
  } finally {
    testing.value = ''
  }
}

/** 清空响应日志（先调用 API 确认成功后再清 UI） */
async function clearResponses(deviceId) {
  try {
    await tcpClearResponses(deviceId)
    responses.value = []
    ElMessage.success('日志已清空')
  } catch {
    ElMessage.error('清空失败')
  }
}

/** 自动滚到底部 */
function scrollToBottom() {
  nextTick(() => {
    if (logRef.value) {
      logRef.value.scrollTop = logRef.value.scrollHeight
    }
  })
}

/** 格式化时间 */
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

// ===== 当选中的设备变化时，加载响应日志 =====
watch(selectedDevice, () => {
  responses.value = []
  commandInput.value = ''
  sendError.value = ''
  if (pendingResponseTimer) clearTimeout(pendingResponseTimer)
  if (selectedDevice.value) {
    loadResponses()
  }
})

// ===== 生命周期 =====
onMounted(() => {
  loadDevices()
  // 每 5 秒刷新设备列表
  pollTimer = setInterval(loadDevices, 5000)
  // 每 3 秒刷新响应
  responseTimer = setInterval(() => {
    if (selectedDevice.value) loadResponses()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (responseTimer) clearInterval(responseTimer)
  if (pendingResponseTimer) clearTimeout(pendingResponseTimer)
})
</script>

<style scoped>
.debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== 设备列表 ===== */
.dev-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}
.dev-card {
  background: #fff;
  border: 1px solid var(--bc-border);
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.dev-card:hover {
  border-color: var(--bc-primary);
}
.dev-card.active {
  border-color: var(--bc-primary);
  box-shadow: 0 0 0 2px rgba(0, 153, 77, 0.1);
}
.dev-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.dev-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
}
.dev-id {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 13px;
}
.dev-unidentified {
  font-size: 11px;
  color: #e6a23c;
  margin-left: 4px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.online {
  background: #67c23a;
  box-shadow: 0 0 4px rgba(103, 194, 58, 0.5);
}
.status-dot.offline {
  background: #c0c4cc;
}
.dev-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--bc-text-3);
  margin-bottom: 8px;
}
.meta-sep {
  color: var(--bc-border);
  margin: 0 2px;
}
.dev-card-actions {
  display: flex;
  gap: 8px;
}
.dev-card-actions .el-button {
  flex: 1;
  font-size: 12px;
}
.test-result {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  border: 1px solid;
}
.test-result.ok {
  background: #f0f9eb;
  border-color: #b3e19d;
  color: #67c23a;
}
.test-result.fail {
  background: #fef0f0;
  border-color: #fbc4c4;
  color: #f56c6c;
}

/* ===== 命令终端 ===== */
.terminal-section {
  margin-bottom: 14px;
}
.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--bc-text);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.terminal-card {
  background: #fff;
  border: 1px solid var(--bc-border);
  border-radius: 12px;
  padding: 14px;
}
.term-row {
  margin-bottom: 10px;
}
.term-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 0;
}
.term-actions .el-button {
  flex: 1;
}
.send-error {
  margin-top: 8px;
  padding: 6px 10px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 6px;
  color: #f56c6c;
  font-size: 12px;
}

/* ===== 通信日志 ===== */
.log-section {
  margin-bottom: 20px;
}
.log-count {
  font-weight: 400;
  font-size: 12px;
  color: var(--bc-text-3);
}
.log-panel {
  background: #1a1d21;
  border: 1px solid var(--bc-border);
  border-radius: 12px;
  padding: 12px;
  max-height: 360px;
  overflow-y: auto;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
}
.log-panel::-webkit-scrollbar {
  width: 4px;
}
.log-panel::-webkit-scrollbar-thumb {
  background: #444;
  border-radius: 2px;
}
.log-empty {
  color: #666;
  text-align: center;
  padding: 20px;
  font-size: 12px;
}
.log-entry {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid #2a2a2a;
}
.log-entry:last-child {
  border-bottom: none;
}
.log-time {
  color: #666;
  flex-shrink: 0;
  min-width: 60px;
  padding-top: 1px;
}
.log-badge {
  flex-shrink: 0;
  width: 20px;
  text-align: center;
  font-weight: 700;
}
.log-entry.send .log-badge {
  color: #409eff;
}
.log-entry.receive .log-badge {
  color: #67c23a;
}
.log-content {
  margin: 0;
  color: #c8ccd4;
  white-space: pre-wrap;
  word-break: break-all;
  flex: 1;
  min-width: 0;
}

/* ===== 加载状态 ===== */
.loading-state {
  padding: 40px 14px;
}
</style>
