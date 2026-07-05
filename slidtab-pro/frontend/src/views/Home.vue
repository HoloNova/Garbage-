<template>
  <div class="page-container">
    <div class="card-grid" style="margin-bottom: 14px">
      <div v-for="s in statCards" :key="s.label" class="stat-block">
        <div class="stat-icon" :style="{ background: s.color }">
          <AppIcon :name="s.icon" :size="22" />
        </div>
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <div class="section-head">
      <span>常用功能</span>
    </div>
    <div class="entry-grid">
      <div v-for="e in entries" :key="e.path" class="entry-item" @click="router.push(e.path)">
        <div class="entry-icon" :style="{ background: e.color }">
          <AppIcon :name="e.icon" :size="22" />
        </div>
        <div class="entry-text">{{ e.text }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { dashboard } from '@/api'

const auth = useAuthStore()
const router = useRouter()
const stats = ref({})

const statCards = computed(() => [
  { label: '图书总数', value: stats.value.totalItems ?? '-', icon: 'Box', color: '#409eff' },
  { label: '可借数量', value: stats.value.availableItems ?? '-', icon: 'Check', color: '#67c23a' },
  { label: '在借数量', value: stats.value.activeBorrows ?? '-', icon: 'Send', color: '#e6a23c' },
  { label: '在线设备', value: `${stats.value.onlineDevices ?? 0}/${stats.value.totalDevices ?? 0}`, icon: 'Monitor', color: '#909399' }
])

const entries = [
  { path: '/books', text: '图书查询', icon: 'Search', color: '#409eff' },
  { path: '/pickup', text: '预约取件', icon: 'Box', color: '#e6a23c' },
  { path: '/return', text: '归还登记', icon: 'Return', color: '#f56c6c' },
  { path: '/env', text: '环境监测', icon: 'Monitor', color: '#909399' },
  { path: '/history', text: '历史记录', icon: 'Time', color: '#9c27b0' }
]

// 管理员专属入口（动态添加）
if (auth.isAdmin) {
  entries.splice(1, 0, { path: '/admin', text: '管理后台', icon: 'Monitor', color: '#ff6b35' })
}

onMounted(async () => {
  try {
    stats.value = await dashboard() || {}
  } catch { /* 忽略 */ }
})
</script>

<style scoped>
.stat-block {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stat-block .stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.stat-block .stat-icon :deep(svg) {
  color: #fff;
}
.stat-block .stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--bc-text);
  line-height: 1;
}
.stat-block .stat-label {
  font-size: 12px;
  color: var(--bc-text-3);
}
.section-head {
  font-size: 14px;
  font-weight: 600;
  color: var(--bc-text);
  margin: 16px 2px 10px;
}
.entry-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 6px;
  background: #fff;
  border-radius: 12px;
  cursor: pointer;
}
.entry-item:active {
  background: #f5f7fa;
}
.entry-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.entry-icon :deep(svg) {
  color: #fff;
}
.entry-text {
  font-size: 12px;
  color: var(--bc-text-2);
}
</style>
