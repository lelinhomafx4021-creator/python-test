import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'
  const proxyOptions = {
    target: proxyTarget,
    changeOrigin: true,
    configure: (proxy: any) => {
      proxy.on('proxyReq', (proxyReq: any) => {
        proxyReq.removeHeader('origin')
        proxyReq.removeHeader('referer')
      })
    },
  }

  return {
    plugins: [vue()],
    server: {
      allowedHosts: ['.trycloudflare.com'],
      proxy: {
        '/api': proxyOptions,
        '/gateway': proxyOptions,
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            echarts: ['echarts'],
            'vendor-vue': ['vue'],
            'vendor-markdown': ['markdown-it'],
          },
        },
      },
    },
  }
})
