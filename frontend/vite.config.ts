import path from 'node:path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/exercises': 'http://localhost:8080',
      '/muscle-groups': 'http://localhost:8080',
      '/routines': 'http://localhost:8080',
      '/workout-sessions': 'http://localhost:8080',
      '/dashboard': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
  },
})
