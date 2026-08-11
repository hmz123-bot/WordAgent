import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { nodePolyfills } from 'vite-plugin-node-polyfills';

export default defineConfig(({ mode }) => {
  // 从 .env（及 .env.local 等）读取，prefix='' 表示加载全部变量（含非 VITE_ 前缀）
  // 这样 DEEPSEEK_API_KEY 不会进入前端产物，只在 dev server 进程内使用。
  const env = loadEnv(mode, (globalThis as any).process?.cwd?.() ?? '', '');
  const deepseekKey = env.DEEPSEEK_API_KEY;

  return {
    plugins: [
      react(),
      // 为浏览器端提供 Buffer/process 等 Node 垫片：mammoth（.docx 导入）依赖 Buffer。
      // docx（.docx 导出）是浏览器原生库，无需 Node 垫片。
      nodePolyfills({ include: ['buffer', 'process', 'util', 'stream', 'events'] }),
    ],
    build: {
      // 不自动清空 dist，避免在某些环境下触发安全删除（回收站）失败导致构建中断
      emptyOutDir: false,
    },
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true,
          timeout: 120000,
        },
        // DeepSeek 代理：浏览器请求 /ds/* 由 dev server 转发到 api.deepseek.com，
        // 并在服务端注入 Authorization，避免把 API Key 打到前端产物里。
        '/ds': {
          target: 'https://api.deepseek.com',
          changeOrigin: true,
          secure: true,
          timeout: 120000,
          rewrite: (p) => p.replace(/^\/ds/, ''),
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              if (!deepseekKey) {
                (globalThis as any).console?.warn?.(
                  '[vite] 未检测到 DEEPSEEK_API_KEY，/ds 代理将无法鉴权。请在 .env 中配置后重启 dev server。',
                );
                return;
              }
              proxyReq.setHeader('Authorization', 'Bearer ' + deepseekKey);
            });
          },
        },
      },
    },
  };
});