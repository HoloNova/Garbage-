import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ICON_SET } from './src/icons/names.js'

// IconPark 按需引入 resolver：仅匹配白名单内的图标名，
// 直接从 es/icons/<Name> 导入默认组件，确保 tree-shaking 生效。
function IconParkResolver() {
  return {
    type: 'component',
    resolve: (name) => {
      if (ICON_SET.has(name)) {
        return { name, from: '@icon-park/vue-next/es/icons/' + name }
      }
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: true,
      resolvers: [IconParkResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true,
    port: 9100,
    strictPort: true,
    cors: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true
      }
    }
  }
})
