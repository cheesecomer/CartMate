import { initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, setDoc, Timestamp, type Firestore } from "firebase/firestore";
import { afterAll, afterEach, beforeAll } from "vitest";

export let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-cartmate",
    firestore: {
      rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

afterEach(async () => {
  await env.clearFirestore();
});

afterAll(async () => {
  await env.cleanup();
});

/** [uid] でログインした端末の Firestore */
export function as(uid: string): Firestore {
  return env.authenticatedContext(uid).firestore();
}

export function signedOut(): Firestore {
  return env.unauthenticatedContext().firestore();
}

/** ルールを通さずに前提のデータを置く */
export async function seed(path: string, data: Record<string, unknown>): Promise<void> {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

export const HID = "h1";
export const OTHER_HID = "h2";

/** 家族 [hid] と、そのメンバー（先頭がオーナー）を置く */
export async function seedHousehold(hid: string, owner: string, ...members: string[]): Promise<void> {
  await seed(`households/${hid}`, { name: `${owner}さんの家族`, createdBy: owner, createdAt: Timestamp.now() });
  let joined = 0;
  for (const [uid, role] of [[owner, "owner"], ...members.map((m) => [m, "member"])]) {
    await seed(`households/${hid}/members/${uid}`, {
      role,
      displayName: uid,
      photoUrl: null,
      // 参加の順を決めるため、1分ずつずらす
      joinedAt: Timestamp.fromMillis(Date.parse("2026-10-01T00:00:00Z") + joined++ * 60_000),
      joinedVia: null,
    });
    await seed(`users/${uid}`, { householdId: hid, updatedAt: Timestamp.now() });
  }
}

/** 招待 [code] を置く。既定は有効期限内で未使用 */
export async function seedInvite(code: string, hid: string, overrides: Record<string, unknown> = {}): Promise<void> {
  await seed(`invites/${code}`, {
    householdId: hid,
    createdBy: "taro",
    inviterName: "山田 太郎",
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 23 * 60 * 60 * 1000),
    maxUses: 1,
    usedCount: 0,
    ...overrides,
  });
}
