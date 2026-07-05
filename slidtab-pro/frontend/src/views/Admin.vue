<template>
  <div class="page-container">
    <h2 class="page-title">管理员看板</h2>

    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-item">
        <div class="stat-icon" :style="{ background: s.color }">
          <AppIcon :name="s.icon" :size="20" :fill="'#fff'" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <el-tabs v-model="tab" class="admin-tabs">
      <el-tab-pane label="库存" name="inventory">
        <div v-loading="loading.inv" class="tab-body">
          <el-empty v-if="!items.length" description="暂无图书" :image-size="60" />
          <div v-else class="card-list">
            <div v-for="it in items" :key="it.itemId" class="inv-card">
              <div class="card-head">
                <span class="card-title">{{ it.title }}</span>
                <el-tag size="small" :type="statusTag(it.status)">{{ statusText(it.status) }}</el-tag>
              </div>
              <div class="card-meta">
                <AppIcon name="Tag" :size="12" />
                <span>{{ it.itemId }}</span>
              </div>
              <div class="card-meta">
                <AppIcon name="Box" :size="12" />
                <span>{{ it.cabinetId }} / {{ it.slotId }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="设备" name="device">
        <div v-loading="loading.dev" class="tab-body">
          <el-empty v-if="!devices.length" description="暂无设备" :image-size="60" />
          <div v-else class="card-list">
            <div v-for="d in devices" :key="d.deviceId" class="dev-card">
              <div class="card-head">
                <span class="card-title">{{ d.deviceId }}</span>
                <el-tag size="small" :type="d.online ? 'success' : 'info'">{{ d.online ? '在线' : '离线' }}</el-tag>
              </div>
              <div class="card-meta">
                <AppIcon name="Monitor" :size="12" />
                <span>{{ d.nodeType }}</span>
              </div>
              <div class="dev-states">
                <span class="state-pill">柜门 {{ d.cabinetDoor || '-' }}</span>
                <span class="state-pill">电机 {{ d.motorState || '-' }}</span>
                <span class="state-pill">传送 {{ d.conveyorState || '-' }}</span>
                <span class="state-pill" :class="{ warn: d.alarmState && d.alarmState !== 'normal' }">告警 {{ d.alarmState || '-' }}</span>
              </div>
              <div class="card-meta">
                <AppIcon name="Time" :size="12" />
                <span>{{ formatTime(d.lastHeartbeat) }}</span>
              </div>
              <div class="dev-actions">
                <el-button size="small" @click="onHeartbeat(d.deviceId)">心跳</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`告警 (${alarms.length})`" name="alarm">
        <div v-loading="loading.alm" class="tab-body">
          <el-empty v-if="!alarms.length" description="暂无告警" :image-size="60" />
          <div v-else class="card-list">
            <div v-for="a in alarms" :key="a.id" class="alarm-card" :class="'border-' + alarmTag(a.status)">
              <div class="card-head">
                <span class="card-title">{{ a.alarmType }}</span>
                <el-tag size="small" :type="alarmTag(a.status)">{{ alarmText(a.status) }}</el-tag>
              </div>
              <div class="card-meta">
                <AppIcon name="Remind" :size="12" />
                <span>{{ a.location || a.deviceId }}</span>
              </div>
              <div class="card-desc">{{ a.description }}</div>
              <div class="card-meta">
                <AppIcon name="Time" :size="12" />
                <span>{{ formatTime(a.alarmTime) }}</span>
              </div>
              <div v-if="a.status !== 'RESOLVED'" class="alarm-actions">
                <el-button size="small" type="primary" @click="openHandle(a)">处理</el-button>
              </div>
              <div v-else class="card-handler">处理人：{{ a.handler || '-' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="统计" name="stats">
        <div class="stats-grid">
          <div class="stat-row"><span class="stat-key">图书总数</span><span class="stat-val">{{ stats.totalItems }}</span></div>
          <div class="stat-row"><span class="stat-key">可借数量</span><span class="stat-val">{{ stats.availableItems }}</span></div>
          <div class="stat-row"><span class="stat-key">已借出</span><span class="stat-val">{{ stats.borrowedItems }}</span></div>
          <div class="stat-row"><span class="stat-key">已预约</span><span class="stat-val">{{ stats.reservedItems }}</span></div>
          <div class="stat-row"><span class="stat-key">在借记录</span><span class="stat-val">{{ stats.activeBorrows }}</span></div>
          <div class="stat-row"><span class="stat-key">预约记录</span><span class="stat-val">{{ stats.reservations }}</span></div>
          <div class="stat-row"><span class="stat-key">设备总数</span><span class="stat-val">{{ stats.totalDevices }}</span></div>
          <div class="stat-row"><span class="stat-key">在线设备</span><span class="stat-val">{{ stats.onlineDevices }}</span></div>
          <div class="stat-row"><span class="stat-key">待处理告警</span><span class="stat-val warn">{{ stats.pendingAlarms }}</span></div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="设备调试" name="devtest">
        <DeviceDebug />
      </el-tab-pane>

      <el-tab-pane label="动作编排" name="action">
        <div class="tab-body">
          <div class="action-toolbar">
            <el-button type="primary" size="small" @click="openCreate">
              <AppIcon name="Add" :size="14" /> 新建模板
            </el-button>
            <el-button size="small" @click="loadTemplates">
              <AppIcon name="Refresh" :size="14" />
            </el-button>
          </div>
          <el-empty v-if="!templates.length" description="暂无模板，点击「新建模板」开始编排" :image-size="60" />
          <div v-else class="card-list">
            <div v-for="t in templates" :key="t.id" class="tpl-card">
              <div class="card-head">
                <span class="card-title">{{ t.name }}</span>
                <el-tag size="small">{{ countSteps(t.sequenceJson) }} 步</el-tag>
              </div>
              <div v-if="t.description" class="card-desc">{{ t.description }}</div>
              <div class="card-meta">
                <AppIcon name="Time" :size="12" />
                <span>{{ formatTime(t.updatedAt) }}</span>
              </div>
              <div class="dev-actions">
                <el-button size="small" type="success" :loading="runningId === t.id" @click="onRun(t)">试运行</el-button>
                <el-button size="small" @click="openEdit(t)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="onDelete(t)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="handleDlg.visible" title="处理告警" width="320px">
      <el-form label-position="top">
        <el-form-item label="告警">{{ handleDlg.type }}</el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="handleDlg.handler" placeholder="处理人姓名" />
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="handleDlg.desc" type="textarea" :rows="3" placeholder="处理结果说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="handleDlg.loading" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <ActionTemplateEditor
      v-model="editorVisible"
      :template="editingTemplate"
      @saved="onTemplateSaved"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  dashboard, searchItems, listDevices, listAlarms, handleAlarm, heartbeat,
  listActionTemplates, deleteActionTemplate, runActionTemplate
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import DeviceDebug from './DeviceDebug.vue'
import ActionTemplateEditor from './ActionTemplateEditor.vue'

const auth = useAuthStore()
const tab = ref('inventory')
const stats = ref({})
const items = ref([])
const devices = ref([])
const alarms = ref([])
const templates = ref([])
const loading = reactive({ inv: false, dev: false, alm: false })
const handleDlg = reactive({ visible: false, id: null, type: '', handler: '', desc: '', loading: false })
const editorVisible = ref(false)
const editingTemplate = ref(null)
const runningId = ref(null)

const statCards = computed(() => [
  { label: '图书总数', value: stats.value.totalItems ?? '-', icon: 'Box', color: '#409eff' },
  { label: '在借记录', value: stats.value.activeBorrows ?? '-', icon: 'Send', color: '#e6a23c' },
  { label: '在线设备', value: `${stats.value.onlineDevices ?? 0}/${stats.value.totalDevices ?? 0}`, icon: 'Monitor', color: '#67c23a' },
  { label: '待处理告警', value: stats.value.pendingAlarms ?? '-', icon: 'Remind', color: '#f56c6c' }
])

const statusText = (s) => ({ AVAILABLE: '可借', BORROWED: '已借出', RESERVED: '已预约', MAINTENANCE: '维护中' }[s] || s)
const statusTag = (s) => ({ AVAILABLE: 'success', BORROWED: 'danger', RESERVED: 'warning', MAINTENANCE: 'info' }[s])
const alarmText = (s) => ({ PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决' }[s] || s)
const alarmTag = (s) => ({ PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success' }[s])

async function loadStats() {
  try { stats.value = await dashboard() || {} } catch { /* 忽略 */ }
}
async function loadItems() {
  loading.inv = true
  try { items.value = await searchItems({}) || [] } finally { loading.inv = false }
}
async function loadDevices() {
  loading.dev = true
  try { devices.value = await listDevices() || [] } finally { loading.dev = false }
}
async function loadAlarms() {
  loading.alm = true
  try { alarms.value = await listAlarms() || [] } finally { loading.alm = false }
}

async function onHeartbeat(deviceId) {
  try {
    await heartbeat(deviceId)
    ElMessage.success('心跳已更新')
    loadDevices()
  } catch { /* 拦截器已提示 */ }
}

function openHandle(row) {
  handleDlg.id = row.id
  handleDlg.type = row.alarmType
  handleDlg.handler = auth.userName
  handleDlg.desc = ''
  handleDlg.visible = true
}

async function confirmHandle() {
  if (!handleDlg.handler) { ElMessage.warning('请填写处理人'); return }
  handleDlg.loading = true
  try {
    await handleAlarm(handleDlg.id, handleDlg.handler, handleDlg.desc || null)
    ElMessage.success('告警已处理')
    handleDlg.visible = false
    loadAlarms()
    loadStats()
  } catch { /* 拦截器已提示 */ } finally {
    handleDlg.loading = false
  }
}

async function loadTemplates() {
  try {
    templates.value = await listActionTemplates() || []
  } catch (e) {
    ElMessage.warning('加载动作模板失败: ' + (e.message || ''))
  }
}

function countSteps(json) {
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

function openCreate() {
  editingTemplate.value = null
  editorVisible.value = true
}

function openEdit(t) {
  editingTemplate.value = t
  editorVisible.value = true
}

async function onRun(t) {
  runningId.value = t.id
  try {
    const data = await runActionTemplate(t.id)
    ElMessage.success(`已派发 ${data.steps} 步动作序列，请观察设备动作`)
  } catch (e) {
    ElMessage.error('试运行失败: ' + (e.message || ''))
  } finally {
    runningId.value = null
  }
}

async function onDelete(t) {
  try {
    await ElMessageBox.confirm(`确认删除模板「${t.name}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await deleteActionTemplate(t.id)
    ElMessage.success('模板已删除')
    loadTemplates()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.message || ''))
  }
}

function onTemplateSaved() {
  loadTemplates()
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

let pollTimer = null

onMounted(async () => {
  await Promise.all([loadStats(), loadItems(), loadDevices(), loadAlarms(), loadTemplates()])
  // 每 3 秒自动刷新设备列表与统计（TCP 设备实时状态）
  pollTimer = setInterval(() => {
    loadDevices()
    loadStats()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.stat-item {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--bc-text);
  line-height: 1.2;
}
.stat-label {
  font-size: 11px;
  color: var(--bc-text-3);
}
.admin-tabs {
  background: #fff;
  padding: 8px 12px;
  border-radius: 12px;
}
.admin-tabs :deep(.el-tabs__nav) {
  width: 100%;
  display: flex;
}
.admin-tabs :deep(.el-tabs__item) {
  flex: 1;
  padding: 0;
  text-align: center;
  font-size: 13px;
}
.tab-body {
  min-height: 200px;
  padding-top: 4px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.inv-card,
.dev-card,
.alarm-card {
  background: #fafbfc;
  border: 1px solid var(--bc-border);
  border-radius: 10px;
  padding: 12px;
}
.tpl-card {
  background: #fafbfc;
  border: 1px solid var(--bc-border);
  border-left: 3px solid #409eff;
  border-radius: 10px;
  padding: 12px;
}
.action-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.alarm-card.border-danger { border-left: 3px solid #f56c6c; }
.alarm-card.border-warning { border-left: 3px solid #e6a23c; }
.alarm-card.border-success { border-left: 3px solid #67c23a; }
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.card-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--bc-text-3);
  margin-top: 3px;
}
.card-desc {
  font-size: 12px;
  color: var(--bc-text-2);
  margin: 4px 0;
  line-height: 1.5;
}
.dev-states {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0;
}
.state-pill {
  font-size: 11px;
  background: #fff;
  border: 1px solid var(--bc-border);
  border-radius: 4px;
  padding: 2px 6px;
  color: var(--bc-text-2);
}
.state-pill.warn {
  color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}
.dev-actions,
.alarm-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.dev-actions .el-button,
.alarm-actions .el-button {
  flex: 1;
}
.card-handler {
  margin-top: 6px;
  font-size: 12px;
  color: var(--bc-text-3);
}
.stats-grid {
  display: flex;
  flex-direction: column;
}
.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--bc-border);
  font-size: 14px;
}
.stat-row:last-child {
  border-bottom: none;
}
.stat-key {
  color: var(--bc-text-2);
}
.stat-val {
  font-weight: 600;
  color: var(--bc-text);
}
.stat-val.warn {
  color: #f56c6c;
}
</style>
