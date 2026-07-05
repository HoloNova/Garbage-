<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-input v-model="query.keyword" placeholder="书名 / 作者 / 分类" clearable @keyup.enter="onSearch" class="kw-input">
        <template #prefix><AppIcon name="Search" :size="16" /></template>
      </el-input>
      <div class="filter-row">
        <el-select v-model="query.status" placeholder="状态" clearable size="default">
          <el-option label="可借" value="AVAILABLE" />
          <el-option label="已借出" value="BORROWED" />
          <el-option label="已预约" value="RESERVED" />
          <el-option label="维护中" value="MAINTENANCE" />
        </el-select>
        <el-button type="primary" @click="onSearch">
          <AppIcon name="Search" :size="16" /> 查询
        </el-button>
      </div>
    </el-card>

    <div v-loading="loading" class="list-wrap">
      <div v-if="list.length === 0 && !loading" class="empty">
        <AppIcon name="Book" :size="40" />
        <p>暂无匹配图书</p>
      </div>
      <div v-for="row in list" :key="row.itemId" class="item-card">
        <div class="item-head">
          <span class="item-id">{{ row.itemId }}</span>
          <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </div>
        <div class="item-title">{{ row.title }}</div>
        <div class="item-meta">
          <span v-if="row.author">{{ row.author }}</span>
        </div>
        <div class="item-meta-2">
          <span v-if="row.category"><AppIcon name="Tag" :size="13" /> {{ row.category }}</span>
          <span><AppIcon name="Box" :size="13" /> {{ row.cabinetId ? `${row.cabinetId}/${row.slotId}` : '未上架' }}</span>
        </div>
        <div class="card-actions">
          <el-button
            v-if="row.status === 'AVAILABLE'"
            type="primary"
            size="small"
            class="reserve-btn"
            @click="onReserve(row)"
          >
            <AppIcon name="Check" :size="14" /> 预约
          </el-button>
          <el-button
            size="small"
            :type="row.status === 'AVAILABLE' ? 'default' : 'warning'"
            plain
            :loading="simulating === row.itemId"
            @click="onSimulate(row)"
          >
            <AppIcon name="Cpu" :size="14" /> 模拟取货
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchItems, reserve, simulatePickup } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const list = ref([])
const simulating = ref('')
const query = reactive({ keyword: '', status: '' })

const statusText = (s) => ({ AVAILABLE: '可借', BORROWED: '已借出', RESERVED: '已预约', MAINTENANCE: '维护中' }[s] || s)
const statusTag = (s) => ({ AVAILABLE: 'success', BORROWED: 'danger', RESERVED: 'warning', MAINTENANCE: 'info' }[s])

async function load() {
  loading.value = true
  try {
    list.value = await searchItems({
      keyword: query.keyword || undefined,
      status: query.status || undefined
    }) || []
  } finally {
    loading.value = false
  }
}

function onSearch() { load() }

async function onReserve(row) {
  try {
    await ElMessageBox.confirm(`确认预约「${row.title}」？`, '预约确认', { type: 'info' })
  } catch { return }
  try {
    const data = await reserve(auth.userId, row.itemId)
    ElMessageBox.alert(
      `预约成功！\n取件柜位：${data.cabinetName}（${data.cabinetLocation}）\n格口：${data.slotId}\n过期时间：${formatTime(data.expireTime)}\n\n请前往「预约取件」页面发起取件。`,
      '预约成功',
      { confirmButtonText: '知道了', callback: () => {} }
    )
    load()
  } catch { /* 拦截器已提示 */ }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

async function onSimulate(row) {
  simulating.value = row.itemId
  try {
    const data = await simulatePickup(row.itemId)
    ElMessage.success(`已派发 ${data.steps} 步动作序列，请观察设备动作`)
  } catch (e) {
    ElMessage.error('模拟取货失败: ' + (e.message || ''))
  } finally {
    simulating.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}
.kw-input {
  margin-bottom: 10px;
}
.filter-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.filter-row .el-select {
  flex: 1;
}
.list-wrap {
  min-height: 120px;
}
.empty {
  text-align: center;
  color: var(--bc-text-3);
  padding: 40px 0;
}
.empty p {
  margin: 8px 0 0;
  font-size: 13px;
}
.item-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 10px;
  position: relative;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.item-id {
  font-size: 12px;
  color: var(--bc-text-3);
  font-family: monospace;
}
.item-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--bc-text);
  margin-bottom: 8px;
}
.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--bc-text-2);
  margin-bottom: 4px;
}
.item-meta-2 {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--bc-text-3);
}
.item-meta-2 span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.reserve-btn {
  flex: 1;
}
</style>
