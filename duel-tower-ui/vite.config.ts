import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig(({ mode }) => ({
  plugins: [svelte()],
  base: mode === 'production' ? '/ui/' : '/',
  server: {
    proxy: {
      '/api': 'http://localhost:9009',
      '/ws': { target: 'ws://localhost:9009', ws: true },
    },
  },
  build: {
    outDir: '../src/main/resources/static/ui',
    emptyOutDir: true,
  },
}))
