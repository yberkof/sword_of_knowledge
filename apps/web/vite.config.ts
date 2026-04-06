import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { fileURLToPath } from "url";
import { defineConfig, loadEnv } from "vite";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, "");
  return {
    plugins: [react(), tailwindcss()],
    define: {
      "process.env.GEMINI_API_KEY": JSON.stringify(env.GEMINI_API_KEY),
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
        "@sok/shared": path.resolve(__dirname, "../../packages/shared/src/index.ts"),
        "@sok/game-contract": path.resolve(__dirname, "../../packages/game-contract/src/index.ts"),
      },
    },
    server: {
      hmr: process.env.DISABLE_HMR !== "true",
      allowedHosts: ["localhost", "127.0.0.1", "0.0.0.0", "787c-91-186-228-32.ngrok-free.app"],
      proxy: {
        "/api": {
          target: env.VITE_API_BASE_URL || "http://localhost:3000",
          changeOrigin: true,
        },
        "/socket.io": {
          target: env.VITE_SOCKET_URL || env.VITE_API_BASE_URL || "http://localhost:3000",
          changeOrigin: true,
          ws: true,
        },
      },
    },
  };
});
