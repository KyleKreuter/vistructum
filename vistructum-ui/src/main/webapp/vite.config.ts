import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { loadEnv, type ProxyOptions } from "vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.VITE_API_TARGET ?? "http://localhost:8765";
  const mock = mode === "mock";
  const assetsTarget = env.VITE_ASSETS_TARGET;
  const mockProxy: Record<string, ProxyOptions> | undefined = assetsTarget ? { "/review/api/assets": { target: assetsTarget, changeOrigin: true } } : undefined;
  return {
    base: "/review/",
    plugins: [react(), tailwindcss()],
    publicDir: mock ? ".mock-public" : false,
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    define: {
      __MOCK__: JSON.stringify(mock),
    },
    server: {
      port: 5173,
      proxy: mock
        ? mockProxy
        : {
            "/review/api": { target, changeOrigin: true },
            "/review/login": { target, changeOrigin: true },
          },
    },
    build: {
      outDir: "dist",
      emptyOutDir: true,
      chunkSizeWarningLimit: 1000,
    },
    test: {
      environment: "node",
      include: ["src/**/*.test.ts"],
    },
  };
});
