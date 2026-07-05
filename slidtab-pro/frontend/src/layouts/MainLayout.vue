<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-title">{{ currentTitle }}</div>
      <el-tag v-if="auth.isAdmin" type="danger" size="small" effect="plain" round>管理员</el-tag>
    </header>

    <main class="app-main scroll-y">
      <router-view />
    </main>

    <nav class="app-tabbar">
      <div
        v-for="t in tabs"
        :key="t.name"
        class="tab-item"
        :class="{ active: activeTab === t.name, center: t.center }"
        @click="router.push(t.path)"
      >
        <div class="tab-icon" :class="{ 'center-pill': t.center }">
          <AppIcon :name="t.icon" :size="t.center ? 26 : 22" :theme="activeTab === t.name ? 'filled' : 'outline'" />
        </div>
        <span class="tab-label">{{ t.label }}</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const tabs = [
  { name: 'home', label: '首页', path: '/home', icon: 'Home' },
  { name: 'books', label: '查询', path: '/books', icon: 'Search' },
  { name: 'pickup', label: '取件', path: '/pickup', icon: 'Box', center: true },
  { name: 'env', label: '环境', path: '/env', icon: 'Monitor' },
  { name: 'profile', label: '我的', path: '/profile', icon: 'User' }
]

const currentTitle = computed(() => route.meta.title || '图书云柜')
const activeTab = computed(() => route.meta.tab || '')
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  width: 100%;
  background: var(--bc-bg);
  position: relative;
  overflow-x: hidden;
}
.app-header {
  height: var(--bc-header-h);
  padding: 0 14px;
  padding-top: var(--safe-top);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-bottom: 1px solid var(--bc-border);
}
.header-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--bc-text);
  flex: 1;
}
.app-main {
  flex: 1;
  min-height: 0;
}
.app-tabbar {
  height: var(--bc-tab-h);
  padding-bottom: var(--safe-bottom);
  flex-shrink: 0;
  display: flex;
  background: #fff;
  border-top: 1px solid var(--bc-border);
  position: relative;
  z-index: 10;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--bc-text-3);
  cursor: pointer;
  transition: color 0.15s;
}
.tab-item.active {
  color: var(--bc-primary);
}
.tab-item.center {
  position: relative;
}
.tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 28px;
}
.tab-icon.center-pill {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--bc-primary);
  color: #fff;
  margin-top: -18px;
  box-shadow: 0 4px 12px rgba(0, 153, 77, 0.35);
}
.tab-icon.center-pill :deep(svg) {
  color: #fff;
}
.tab-label {
  font-size: 11px;
  line-height: 1;
}
.tab-item.center .tab-label {
  margin-top: 2px;
}
</style>
