import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // エミュレータは1つなので、テストファイルを並列に実行しない
    fileParallelism: false,
    testTimeout: 20_000,
  },
});
