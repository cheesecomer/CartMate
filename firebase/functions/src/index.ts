import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onCall } from "firebase-functions/v2/https";
import { openAiClient } from "./ai.js";
import { judgeName as judge } from "./judge.js";

initializeApp();

const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");

/** AI の名寄せの判定（docs/design/security.md「AI の名寄せの関数」）。ログインと App Check を求める */
export const judgeName = onCall(
  {
    region: "asia-northeast1",
    enforceAppCheck: true,
    secrets: [OPENAI_API_KEY],
    // 呼び出しは購入のあとに1回ずつなので、同時に多くは要らない。暴走しても広がらないよう抑える
    maxInstances: 5,
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  (request) => judge(request, { db: getFirestore(), ai: openAiClient(OPENAI_API_KEY.value()), now: () => new Date() }),
);
