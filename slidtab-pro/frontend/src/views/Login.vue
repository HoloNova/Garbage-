<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo"><AppIcon name="Book" :size="44" theme="outline" /></div>
        <h1>图书云柜</h1>
        <p>基于物联网感知与智能调度的无人学习资源服务系统</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onLogin">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large">
            <template #prefix><AppIcon name="Iphone" :size="18" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="学号 / 工号" prop="studentId">
          <el-input v-model="form.studentId" placeholder="请输入学号或工号" size="large">
            <template #prefix><AppIcon name="User" :size="18" /></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="onLogin">
          登 录
        </el-button>
      </el-form>

      <div class="demo-hint">
        <div class="hint-title">演示账号（点击填充）</div>
        <div class="demo-list">
          <div class="demo-item" @click="fill('13800000001', 'S001')">
            <AppIcon name="Setting" :size="16" />
            <span>管理员</span>
          </div>
          <div class="demo-item" @click="fill('13800000002', 'S002')">
            <AppIcon name="User" :size="16" />
            <span>张三</span>
          </div>
          <div class="demo-item" @click="fill('13800000003', 'S003')">
            <AppIcon name="User" :size="16" />
            <span>李四</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ phone: '', studentId: '' })
const rules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  studentId: [{ required: true, message: '请输入学号/工号', trigger: 'blur' }]
}

function fill(phone, studentId) {
  form.phone = phone
  form.studentId = studentId
}

async function onLogin() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const data = await login(form.phone, form.studentId)
      auth.setUser(data)
      ElMessage.success(`欢迎，${data.name}`)
      router.push(route.query.redirect || '/home')
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: linear-gradient(160deg, #0f3d24 0%, #00994d 100%);
}
.login-card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 18px;
  padding: 32px 22px 22px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.25);
}
.login-header {
  text-align: center;
  margin-bottom: 24px;
}
.login-logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 10px;
  border-radius: 18px;
  background: linear-gradient(135deg, #00994d, #007a3d);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.login-logo :deep(svg) {
  color: #fff;
}
.login-header h1 {
  margin: 0 0 6px;
  color: var(--bc-primary-dark);
  font-size: 24px;
}
.login-header p {
  margin: 0;
  color: var(--bc-text-3);
  font-size: 12px;
  line-height: 1.5;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  background: var(--bc-primary);
  border-color: var(--bc-primary);
}
.demo-hint {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--bc-border);
}
.hint-title {
  font-size: 12px;
  color: var(--bc-text-3);
  margin-bottom: 10px;
}
.demo-list {
  display: flex;
  gap: 8px;
}
.demo-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 4px;
  border-radius: 8px;
  background: #f5f7fa;
  font-size: 12px;
  color: var(--bc-text-2);
  cursor: pointer;
}
.demo-item:active {
  background: #ecf5ef;
  color: var(--bc-primary);
}
</style>
