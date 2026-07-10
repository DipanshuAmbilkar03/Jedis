import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Standalone landing-page module - not shared with /frontend
export default defineConfig({
  plugins: [react()],
})
