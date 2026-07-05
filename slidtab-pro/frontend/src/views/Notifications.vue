<template>
  <div class="page-container">
    <h2 class="page-title">消息通知</h2>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-radio-group v-model="filter" class="filter-group" size="default">
          <el-radio-button value="all">全部 {{ all.length }}</el-radio-button>
          <el-radio-button value="alarm">告警 {{ alarmCount }}</el-radio-button>
          <el-radio-button value="borrow">借还 {{ borrowCount }}</el-radio-button>
        </el-radio-group>
        <el-button class="refresh-btn" @click="loadAll">
          <AppIcon name="Refresh" :size="16" :spin="loading" />
        </el-button>
      </div>
    </el-card>

    <el-empty v-if="!filtered.length" description="暂无通知" :image-size="80" />
    <div v-else class="notice-list">
      <div v-for="n in filtered" :key="n.id" class="notice-item" :class="'border-' + n.tagType">
        <div class="notice-icon" :class="'bg-' + n.tagType">
          <AppIcon :name="n.category === '告警' ? 'Remind' : 'Book'" :size="18" :fill="'#fff'" />
        </div>
        <div class="notice-body">
          <div class="notice-head">
            <el-tag size="small" :type="n.tagType" effect="plain">{{ n.category }}</el-tag>
            <span class="notice-time">{{ n.time }}</span>
          </div>
          <div class="notice-title">{{ n.title }}</div>
          <div class="notice-desc">{{ n.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listAlarms, borrowHistory } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const filter = ref('all')
const loading = ref(false)
const alarms = ref([])
const history = ref([])

const alarmCount = computed(() => alarms.value.length)
const borrowCount = computed(() => history.value.length)

const all = computed(() => {
  const alarmNotices = alarms.value.map((a) => ({
    id: 'a' + a.id,
    time: formatTime(a.alarmTime),
    type: 'danger',
    hollow: a.status !== 'RESOLVED',
    tagType: 'danger',
    category: '告警',
    title: `${a.alarmType} - ${a.location || a.deviceId}`,
    desc: a.description + (a.status === 'PENDING' ? '（待处理）' : a.status === 'PROCESSING' ? '（处理中）' : '（已解决）')
  }))
  const borrowNotices = history.value.map((r) => ({
    id: 'b' + r.recordId,
    time: formatTime(r.returnTime || r.borrowTime),
    type: r.status === 'OVERDUE' ? 'danger' : r.status === 'RETURNED' ? 'info' : 'success',
    hollow: false,
    tagType: r.status === 'OVERDUE' ? 'danger' : r.status === 'RETURNED' ? 'info' : 'success',
    category: '借还',
    title: `${r.itemTitle} - ${statusText(r.status)}`,
    desc: `${r.userName} 于 ${formatTime(r.borrowTime)} 借出${r.returnTime ? '，' + formatTime(r.returnTime) + ' 归还' : ''}`
  }))
  return [...alarmNotices, ...borrowNotices].sort((a, b) => b.time.localeCompare(a.time))
})

const filtered = computed(() => {
  if (filter.value === 'alarm') return all.value.filter((n) => n.category === '告警')
  if (filter.value === 'borrow') return all.value.filter((n) => n.category === '借还')
  return all.value
})

function statusText(s) { return { RESERVED: '已预约', BORROWED: '已借出', RETURNED: '已归还', OVERDUE: '已超时' }[s] || s }
function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

async function loadAll() {
  loading.value = true
  try {
    const [a, h] = await Promise.all([
      listAlarms(),
      borrowHistory(auth.userId)
    ])
    alarms.value = a || []
    history.value = h || []
  } catch { /* 忽略 */ } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-group {
  flex: 1;
}
.filter-group :deep(.el-radio-button) {
  flex: 1;
}
.filter-group :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 8px 0;
  font-size: 12px;
}
.refresh-btn {
  flex-shrink: 0;
  width: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notice-item {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  gap: 10px;
  border-left: 3px solid var(--bc-border);
}
.notice-item.border-danger { border-left-color: #f56c6c; }
.notice-item.border-warning { border-left-color: #e6a23c; }
.notice-item.border-success { border-left-color: #67c23a; }
.notice-item.border-info { border-left-color: #909399; }
.notice-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.notice-icon.bg-danger { background: #f56c6c; }
.notice-icon.bg-warning { background: #e6a23c; }
.notice-icon.bg-success { background: #67c23a; }
.notice-icon.bg-info { background: #909399; }
.notice-body {
  flex: 1;
  min-width: 0;
}
.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.notice-time {
  font-size: 11px;
  color: var(--bc-text-3);
}
.notice-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
  margin-bottom: 2px;
}
.notice-desc {
  font-size: 12px;
  color: var(--bc-text-2);
  line-height: 1.5;
}
</style>
