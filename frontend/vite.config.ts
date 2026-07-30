import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://backend:8080',
      '/actuator': 'http://backend:8080',
      '/branding': 'http://backend:8080'
    }
  },
  test: {
    environment: 'jsdom'
  }
});
