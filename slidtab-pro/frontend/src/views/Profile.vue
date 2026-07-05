<template>
  <div class="page-container">
    <div class="profile-card">
      <div class="avatar"><AppIcon name="User" :size="32" theme="outline" /></div>
      <div class="profile-info">
        <div class="profile-name">{{ auth.userName || '未登录' }}</div>
        <div class="profile-id">{{ auth.userId }} · {{ auth.isAdmin ? '管理员' : '普通用户' }}</div>
      </div>
    </div>

    <div class="section-title">借还</div>
    <div class="menu-group">
      <div class="menu-item" @click="go('/return')">
        <AppIcon name="Return" :size="20" class="menu-ic" />
        <span class="menu-text">归还登记</span>
        <AppIcon name="ArrowRight" :size="16" class="menu-arrow" />
      </div>
      <div class="menu-item" @click="go('/history')">
        <AppIcon name="Time" :size="20" class="menu-ic" />
        <span class="menu-text">历史记录</span>
        <AppIcon name="ArrowRight" :size="16" class="menu-arrow" />
      </div>
    </div>

    <div class="section-title">通知与管理</div>
    <div class="menu-group">
      <div class="menu-item" @click="go('/notifications')">
        <AppIcon name="Remind" :size="20" class="menu-ic" />
        <span class="menu-text">消息通知</span>
        <AppIcon name="ArrowRight" :size="16" class="menu-arrow" />
      </div>
      <div v-if="auth.isAdmin" class="menu-item" @click="go('/admin')">
        <AppIcon name="Setting" :size="20" class="menu-ic" />
        <span class="menu-text">管理员看板</span>
        <AppIcon name="ArrowRight" :size="16" class="menu-arrow" />
      </div>
    </div>

    <div class="logout-btn" @click="onLogout">
      <AppIcon name="Logout" :size="18" />
      <span>退出登录</span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

function go(path) {
  router.push(path)
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  } catch { return }
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.profile-card {
  background: linear-gradient(135deg, var(--bc-primary) 0%, var(--bc-primary-dark) 100%);
  border-radius: 14px;
  padding: 18px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
  margin-bottom: 18px;
}
.avatar {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar :deep(svg) {
  color: #fff;
}
.profile-name {
  font-size: 18px;
  font-weight: 600;
}
.profile-id {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 2px;
}
.section-title {
  font-size: 13px;
  color: var(--bc-text-3);
  margin: 14px 2px 8px;
}
.menu-group {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-bottom: 1px solid var(--bc-border);
  cursor: pointer;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-item:active {
  background: #f5f5f5;
}
.menu-ic {
  color: var(--bc-primary);
  flex-shrink: 0;
}
.menu-text {
  flex: 1;
  font-size: 15px;
  color: var(--bc-text);
}
.menu-arrow {
  color: var(--bc-text-3);
}
.logout-btn {
  margin-top: 22px;
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #f56c6c;
  font-size: 15px;
  cursor: pointer;
}
.logout-btn:active {
  background: #fef0f0;
}
</style>
