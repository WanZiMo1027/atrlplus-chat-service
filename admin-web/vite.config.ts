import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  server: {
    proxy: {
      '/api/admin': {
        target: 'http://127.0.0.1:8080',
        autoRewrite: true,
      },
    },
  },
})
