import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "vitest";
import { as, HID, OTHER_HID, seed, seedHousehold, signedOut } from "./setup";

const KEY = "0123456789abcdef0123456789abcdef";
const judgementPath = (hid: string) => `households/${hid}/nameJudgements/${KEY}`;

/** 関数が書く判定（docs/design/data-model.md「AI の名寄せ」） */
const judgement = {
  normalizedName: "胡麻ドレ",
  qualifiers: {},
  status: "done",
  item: "ごまドレッシング",
  form: null,
  volume: null,
  use: null,
  variant: null,
  confidence: 0.9,
  reason: "「胡麻ドレ」はごまドレッシングの略称",
  judgeVersion: "gpt-6-luna/1",
  dictionaryVersion: "2026.10.1",
  requestedAt: new Date(),
  judgedAt: new Date(),
};

describe("AI の名寄せの判定", () => {
  it("家族のメンバーは読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seed(judgementPath(HID), judgement);
    await assertSucceeds(getDoc(doc(as("taro"), judgementPath(HID))));
    await assertSucceeds(getDoc(doc(as("hanako"), judgementPath(HID))));
  });

  it("ほかの家族のメンバーとログインしていない人は読めない", async () => {
    await seedHousehold(HID, "taro");
    await seedHousehold(OTHER_HID, "jiro");
    await seed(judgementPath(HID), judgement);
    await assertFails(getDoc(doc(as("jiro"), judgementPath(HID))));
    await assertFails(getDoc(doc(signedOut(), judgementPath(HID))));
  });

  it("メンバーでもアプリからは作れず、変えられず、消せない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("taro"), judgementPath(HID)), judgement));
    await seed(judgementPath(HID), { ...judgement, status: "pending", item: null });
    await assertFails(updateDoc(doc(as("taro"), judgementPath(HID)), { status: "done", item: "ごまドレッシング" }));
    await assertFails(deleteDoc(doc(as("taro"), judgementPath(HID))));
  });
});

describe("AI の使用量と設定", () => {
  it("メンバーでも読めず、書けない", async () => {
    await seedHousehold(HID, "taro");
    await seed("aiUsage/202611", { costMicroUsd: 93, calls: 1, households: { [HID]: 1 } });
    await seed("config/ai", { enabled: true, monthlyBudgetMicroUsd: 1_000_000, perHouseholdMonthlyCalls: 200 });
    const db = as("taro");
    await assertFails(getDoc(doc(db, "aiUsage/202611")));
    await assertFails(updateDoc(doc(db, "aiUsage/202611"), { calls: 0, costMicroUsd: 0 }));
    await assertFails(setDoc(doc(db, "aiUsage/202612"), { calls: 0 }));
    await assertFails(getDoc(doc(db, "config/ai")));
    await assertFails(updateDoc(doc(db, "config/ai"), { enabled: false }));
    await assertFails(updateDoc(doc(db, "config/ai"), { perHouseholdMonthlyCalls: 100000 }));
  });
});
