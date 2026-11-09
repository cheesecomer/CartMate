import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";
import { costMicroUsd, JUDGE_VERSION, type AiClient } from "./ai.js";
import { judgementKey } from "./key.js";
import { parseAnswer, parseRequest, type JudgeRequest } from "./validate.js";

/** pending のまま、これだけたったら問い合わせ直してよい（関数が途中で止まった） */
export const PENDING_TIMEOUT_MS = 5 * 60 * 1000;
/** failed から、これだけたったら問い合わせ直してよい */
export const RETRY_AFTER_FAILURE_MS = 24 * 60 * 60 * 1000;
/** 上限に対する使用量の割合で、知らせを出すところ */
export const THRESHOLDS = [80, 100] as const;

export type Failure = "disabled" | "limit" | "invalid" | "unavailable";
export type Status = "pending" | "done" | "failed";

export interface Deps {
  db: Firestore;
  ai: AiClient;
  now: () => Date;
}

/** onCall の CallableRequest のうち、ここで使うもの */
export interface Caller {
  auth?: { uid: string };
  app?: unknown;
  data: unknown;
}

export interface JudgeResult {
  key: string;
  status: Status;
  failure?: Failure;
}

/** 月ごとの使用量のドキュメントの ID（UTC の月） */
export function usageMonth(now: Date): string {
  return `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
}

/**
 * 今ある判定を使い続けるか、問い合わせ直すか（docs/design/data-model.md「問い合わせ直す条件」）。
 * 判定がない、バージョンが違う、pending のまま5分、failed から1日なら問い合わせ直す
 */
export function shouldAsk(existing: FirebaseFirestore.DocumentData | undefined, now: Date): boolean {
  if (!existing || existing.judgeVersion !== JUDGE_VERSION) return true;
  const since = (field: string) => now.getTime() - ((existing[field] as Timestamp | undefined)?.toMillis() ?? 0);
  switch (existing.status) {
    case "pending":
      return since("requestedAt") >= PENDING_TIMEOUT_MS;
    case "failed":
      return since("judgedAt") >= RETRY_AFTER_FAILURE_MS;
    default:
      return false;
  }
}

/**
 * 名前を AI に判定させ、家族の nameJudgements に保存する（docs/design/security.md「AI の名寄せの関数」）。
 * 同じキーを2台が同時に頼んでも、トランザクションで pending の場所を取った1台だけが AI を呼ぶ
 */
export async function judgeName(caller: Caller, deps: Deps): Promise<JudgeResult> {
  if (!caller.auth) throw new HttpsError("unauthenticated", "ログインが必要です");
  // onCall の enforceAppCheck でも拒否されるが、設定の漏れに備えてここでも確かめる
  if (!caller.app) throw new HttpsError("unauthenticated", "App Check が必要です");
  const request = parseRequest(caller.data);
  if (!request) throw new HttpsError("invalid-argument", "依頼の形が正しくありません");
  const { db } = deps;
  const member = await db.doc(`households/${request.householdId}/members/${caller.auth.uid}`).get();
  if (!member.exists) throw new HttpsError("permission-denied", "家族のメンバーではありません");

  const key = judgementKey(request.normalizedName, request.qualifiers);
  const judgementRef = db.doc(`households/${request.householdId}/nameJudgements/${key}`);
  const usageRef = db.doc(`aiUsage/${usageMonth(deps.now())}`);

  const claim = await db.runTransaction(async (tx) => {
    const [judgement, config, usage] = await Promise.all([
      tx.get(judgementRef),
      tx.get(db.doc("config/ai")),
      tx.get(usageRef),
    ]);
    const now = deps.now();
    if (!shouldAsk(judgement.data(), now)) {
      const data = judgement.data()!;
      return { status: data.status as Status, failure: data.failure as Failure | undefined, ask: false };
    }
    const base = {
      normalizedName: request.normalizedName,
      qualifiers: request.qualifiers,
      judgeVersion: JUDGE_VERSION,
      dictionaryVersion: request.dictionaryVersion,
      requestedAt: Timestamp.fromDate(now),
    };
    const failure = limitFailure(config.data(), usage.data(), request.householdId);
    if (failure) {
      tx.set(judgementRef, { ...base, status: "failed", failure, judgedAt: Timestamp.fromDate(now) });
      return { status: "failed" as const, failure, ask: false };
    }
    tx.set(judgementRef, { ...base, status: "pending" });
    // 呼ぶ前に回数を数える（失敗しても AI を呼んだ回数に入れる）
    tx.set(
      usageRef,
      { calls: FieldValue.increment(1), households: { [request.householdId]: FieldValue.increment(1) } },
      { merge: true },
    );
    return { status: "pending" as const, failure: undefined, ask: true };
  });
  if (!claim.ask) return { key, status: claim.status, ...(claim.failure ? { failure: claim.failure } : {}) };

  return ask(request, key, deps);
}

/** 止める設定か上限に当たれば、その理由 */
function limitFailure(
  config: FirebaseFirestore.DocumentData | undefined,
  usage: FirebaseFirestore.DocumentData | undefined,
  householdId: string,
): Failure | null {
  // 設定がなければ止めておく（コンソールで作ってから使い始める。docs/ai-setup.md）
  if (config?.enabled !== true) return "disabled";
  const budget = config.monthlyBudgetMicroUsd;
  const perHousehold = config.perHouseholdMonthlyCalls;
  if (typeof budget !== "number" || typeof perHousehold !== "number") return "disabled";
  if ((usage?.costMicroUsd ?? 0) >= budget) return "limit";
  if ((usage?.households?.[householdId] ?? 0) >= perHousehold) return "limit";
  return null;
}

async function ask(request: JudgeRequest, key: string, deps: Deps): Promise<JudgeResult> {
  const { db } = deps;
  const judgementRef = db.doc(`households/${request.householdId}/nameJudgements/${key}`);
  let result;
  try {
    result = await deps.ai.judge({ name: request.normalizedName, qualifiers: request.qualifiers });
  } catch (e) {
    logger.warn("AI の呼び出しに失敗しました", { error: String(e) });
    await judgementRef.update({ status: "failed", failure: "unavailable", judgedAt: Timestamp.fromDate(deps.now()) });
    return { key, status: "failed", failure: "unavailable" };
  }
  await addCost(costMicroUsd(result.usage), deps);
  const answer = parseAnswer(result.text);
  if (!answer) {
    logger.warn("AI の応答の形が正しくありません");
    await judgementRef.update({ status: "failed", failure: "invalid", judgedAt: Timestamp.fromDate(deps.now()) });
    return { key, status: "failed", failure: "invalid" };
  }
  await judgementRef.update({ status: "done", ...answer, judgedAt: Timestamp.fromDate(deps.now()) });
  return { key, status: "done" };
}

/** 使った金額を足し、上限の80%と100%を超えたら、月に一度だけ警告のログを書く（docs/ai-setup.md でメールにする） */
async function addCost(cost: number, deps: Deps): Promise<void> {
  const { db } = deps;
  const usageRef = db.doc(`aiUsage/${usageMonth(deps.now())}`);
  await db.runTransaction(async (tx) => {
    const [usage, config] = await Promise.all([tx.get(usageRef), tx.get(db.doc("config/ai"))]);
    const total = (usage.data()?.costMicroUsd ?? 0) + cost;
    const budget = config.data()?.monthlyBudgetMicroUsd;
    const notified: Record<string, boolean> = { ...(usage.data()?.notified ?? {}) };
    const reached = typeof budget === "number" && budget > 0
      ? THRESHOLDS.filter((t) => total * 100 >= budget * t && !notified[t])
      : [];
    for (const t of reached) notified[t] = true;
    tx.set(usageRef, { costMicroUsd: FieldValue.increment(cost), ...(reached.length ? { notified } : {}) }, { merge: true });
    // トランザクションはやり直されることがあるので、ログは確定してから書く
    return { reached, total, budget };
  }).then(({ reached, total, budget }) => {
    for (const t of reached) {
      logger.warn("AI の使用量が上限に近づきました", { aiBudgetThreshold: t, costMicroUsd: total, monthlyBudgetMicroUsd: budget });
    }
  });
}
