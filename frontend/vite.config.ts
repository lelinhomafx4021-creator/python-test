import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/gateway': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // echarts 完整包 ~1MB，拆到独立 chunk 按需加载
          'echarts': ['echarts'],
          // vue 核心运行时 + 路由（如有）单独一个 chunk
          'vendor-vue': ['vue'],
          // markdown 渲染库单独拆分（仅聊天页使用）
          'vendor-markdown': ['markdown-it'],
        },
      },
    },
  },
})
