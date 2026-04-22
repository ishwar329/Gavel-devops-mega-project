import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 3000,
    proxy: {
      '/auctions': { target: 'http://localhost:8081', ws: true },
      '/auth':     { target: 'http://localhost:8082' },
      '/users':    { target: 'http://localhost:8082' },
      '/ai/describe': { target: 'http://localhost:8083' },
      '/ai':       { target: 'http://localhost:8086' },
      '/shops':    { target: 'http://localhost:8083' },
      '/sellers':  { target: 'http://localhost:8083' },
      '/uploads':  { target: 'http://localhost:8083' },
      '/notifications': { target: 'http://localhost:8080' },
    },
  },
})
