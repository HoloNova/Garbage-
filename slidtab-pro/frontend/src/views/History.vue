<template>
  <div class="page-container">
    <h2 class="page-title">历史记录</h2>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select">
          <el-option label="已预约" value="RESERVED" />
          <el-option label="已借出" value="BORROWED" />
          <el-option label="已归还" value="RETURNED" />
          <el-option label="已超时" value="OVERDUE" />
        </el-select>
        <el-button class="refresh-btn" @click="load">
          <AppIcon name="Refresh" :size="16" :spin="loading" />
        </el-button>
      </div>
    </el-card>

    <el-empty v-if="!filtered.length" description="暂无历史记录" :image-size="80" />
    <div v-else class="history-list" v-loading="loading">
      <div v-for="r in filtered" :key="r.recordId" class="history-item">
        <div class="h-head">
          <span class="h-title">{{ r.itemTitle }}</span>
          <el-tag size="small" :type="statusType(r.status)">{{ statusText(r.status) }}</el-tag>
        </div>
        <div class="h-meta">
          <AppIcon name="Tag" :size="12" />
          <span>{{ r.itemId }}</span>
        </div>
        <div class="h-meta">
          <AppIcon name="User" :size="12" />
          <span>{{ r.userName }}</span>
        </div>
        <div class="h-time">
          <div class="h-meta">
            <AppIcon name="Time" :size="12" />
            <span>借出 {{ formatTime(r.borrowTime) }}</span>
          </div>
          <div v-if="r.returnTime" class="h-meta">
            <AppIcon name="Return" :size="12" />
            <span>归还 {{ formatTime(r.returnTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { borrowHistory } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const list = ref([])
const statusFilter = ref('')

const filtered = computed(() =>
  statusFilter.value ? list.value.filter((r) => r.status === statusFilter.value) : list.value
)

async function load() {
  loading.value = true
  try {
    list.value = await borrowHistory(auth.userId) || []
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}
function statusText(s) { return { RESERVED: '已预约', BORROWED: '已借出', RETURNED: '已归还', OVERDUE: '已超时' }[s] || s }
function statusType(s) { return { RESERVED: 'warning', BORROWED: 'success', RETURNED: 'info', OVERDUE: 'danger' }[s] }

onMounted(load)
</script>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}
.filter-row {
  display: flex;
  gap: 8px;
}
.status-select {
  flex: 1;
}
.refresh-btn {
  flex-shrink: 0;
  width: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.history-item {
  background: #fff;
  border: 1px solid var(--bc-border);
  border-radius: 12px;
  padding: 12px;
}
.h-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.h-title {
  font-weight: 600;
  color: var(--bc-text);
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.h-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--bc-text-3);
  margin-top: 3px;
}
.h-time {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed var(--bc-border);
}
</style>
