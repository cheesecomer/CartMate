import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["test/**/*.test.ts"],
    // テストは同じエミュレータのデータを使うので、順に実行する
    fileParallelism: false,
  },
});
