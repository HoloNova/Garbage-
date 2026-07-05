<template>
  <div class="page-container">
    <h2 class="page-title">归还登记</h2>

    <el-card shadow="never" class="form-card">
      <div class="section-head">
        <AppIcon name="Return" :size="18" />
        <span>归还操作</span>
      </div>
      <el-form label-position="top" @submit.prevent="onReturn">
        <el-form-item label="图书编号">
          <el-input v-model="form.itemId" placeholder="扫描或输入图书编号（如 BK20260001）" clearable>
            <template #prefix><AppIcon name="Scan" :size="16" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="异常说明（选填）">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如图书损坏、缺件等" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit-btn" @click="onReturn">
          <AppIcon name="Return" :size="18" /> {{ loading ? '正在执行动作序列...' : '确认归还' }}
        </el-button>
        <el-alert
          v-if="loading"
          title="归还动作执行中"
          description="机械臂和滑台正在执行5步动作序列，预计30-60秒，请勿离开页面"
          type="info"
          show-icon
          :closable="false"
          class="progress-alert"
        />
      </el-form>

      <el-alert
        v-if="result"
        :title="result.success ? '归还成功' : '归还失败'"
        :type="result.success ? 'success' : 'error'"
        :description="result.message"
        show-icon
        :closable="false"
        class="result-alert"
      />
    </el-card>

    <el-card shadow="never" class="borrowed-card">
      <div class="section-head">
        <AppIcon name="Book" :size="18" />
        <span>我的在借图书</span>
      </div>
      <el-empty v-if="!borrowed.length" description="暂无在借图书" :image-size="60" />
      <div v-else class="borrowed-list">
        <div v-for="b in borrowed" :key="b.recordId" class="borrowed-item">
          <div class="b-head">
            <span class="b-title">{{ b.itemTitle }}</span>
            <el-button size="small" type="primary" plain @click="form.itemId = b.itemId">填入</el-button>
          </div>
          <div class="b-meta">
            <AppIcon name="Tag" :size="12" />
            <span>{{ b.itemId }}</span>
          </div>
          <div class="b-meta">
            <AppIcon name="Time" :size="12" />
            <span>{{ formatTime(b.borrowTime) }}</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { returnItem, borrowHistory } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ itemId: '', remark: '' })
const result = ref(null)
const borrowed = ref([])

async function loadBorrowed() {
  try {
    const data = await borrowHistory(auth.userId) || []
    borrowed.value = data.filter((r) => r.status === 'BORROWED')
  } catch { /* 忽略 */ }
}

async function onReturn() {
  if (!form.itemId) {
    ElMessage.warning('请输入图书编号')
    return
  }
  loading.value = true
  result.value = null
  try {
    const data = await returnItem(auth.userId, form.itemId, form.remark || null)
    result.value = {
      success: data.result_code === '0000',
      message: data.result_code === '0000'
        ? `归还成功！图书 ${data.inventory_state?.book_id || ''} 已入库，状态：${data.inventory_state?.item_state || 'placed'}`
        : `归还失败：${data.result_msg || data.result_code}`
    }
    if (data.result_code === '0000') {
      ElMessage.success('归还成功')
      form.itemId = ''
      form.remark = ''
      loadBorrowed()
    }
  } catch {
    result.value = { success: false, message: '归还请求失败，请检查图书编号或是否在借状态' }
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(loadBorrowed)
</script>

<style scoped>
.form-card {
  margin-bottom: 12px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
  margin-bottom: 12px;
}
.submit-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.result-alert {
  margin-top: 14px;
}
.progress-alert {
  margin-top: 10px;
}
.borrowed-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.borrowed-item {
  background: #fafbfc;
  border: 1px solid var(--bc-border);
  border-radius: 10px;
  padding: 12px;
}
.b-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.b-title {
  font-weight: 600;
  color: var(--bc-text);
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.b-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--bc-text-3);
  margin-top: 2px;
}
</style>
