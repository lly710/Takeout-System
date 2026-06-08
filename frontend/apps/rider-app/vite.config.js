import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5175,
    proxy: {
      "/api/rider": {
        target: "http://localhost:8103",
        changeOrigin: true
      },
      "/api": {
        target: "http://localhost:8088",
        changeOrigin: true
      }
    }
  }
});
