import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendPort = env.VITE_BACKEND_PORT || '8081'
  const httpProxyTarget = `http://localhost:${backendPort}`
  const wsProxyTarget = `ws://localhost:${backendPort}`

  return {
    plugins: [vue(), UnoCSS()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.js']
    },
    server: {
      host: '0.0.0.0',
      port: 5200,
      proxy: {
        '/api': {
          target: httpProxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        },
        '/ws': {
          target: wsProxyTarget,
          ws: true,
          changeOrigin: true
        }
      }
    }
  }
})
