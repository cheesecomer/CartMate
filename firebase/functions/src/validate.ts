import { QUALIFIER_KINDS, type Qualifiers } from "./key.js";

/** アプリから受け取る判定の依頼 */
export interface JudgeRequest {
  householdId: string;
  normalizedName: string;
  qualifiers: Qualifiers;
  dictionaryVersion: string;
}

/** AI の応答（docs/design/data-model.md「AI の名寄せ」） */
export interface JudgeAnswer {
  item: string | null;
  form: string | null;
  volume: string | null;
  use: string | null;
  variant: string | null;
  confidence: number;
  reason: string;
}

/** 商品名の上限はルールの products.normalizedName と同じ */
const NAME_MAX = 60;
const QUALIFIER_MAX = 20;
const QUALIFIER_VALUES_MAX = 3;
const ITEM_MAX = 30;
const ATTRIBUTE_MAX = 20;
const REASON_MAX = 60;
const ID = /^[A-Za-z0-9_-]{1,128}$/;
const VERSION = /^[0-9A-Za-z.\-_]{1,20}$/;
// 制御文字（改行を含む）は名前に含まれないので、来たら形が違うとみなす
const CONTROL = /[\u0000-\u001f\u007f]/;

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
const text = (v: unknown, max: number): v is string =>
  typeof v === "string" && v.length > 0 && [...v].length <= max && !CONTROL.test(v) && v.trim() === v;
const optText = (v: unknown, max: number): v is string | null => v === null || text(v, max);
const hasOnly = (o: Record<string, unknown>, keys: readonly string[]) => Object.keys(o).every((k) => keys.includes(k));

/** 依頼の形を確かめる。違えば null */
export function parseRequest(data: unknown): JudgeRequest | null {
  if (!isRecord(data) || !hasOnly(data, ["householdId", "normalizedName", "qualifiers", "dictionaryVersion"])) return null;
  const { householdId, normalizedName, qualifiers, dictionaryVersion } = data;
  if (typeof householdId !== "string" || !ID.test(householdId)) return null;
  if (!text(normalizedName, NAME_MAX)) return null;
  if (typeof dictionaryVersion !== "string" || !VERSION.test(dictionaryVersion)) return null;
  if (!isRecord(qualifiers) || !hasOnly(qualifiers, QUALIFIER_KINDS)) return null;
  const parsed: Qualifiers = {};
  for (const kind of QUALIFIER_KINDS) {
    const values = qualifiers[kind];
    if (values === undefined) continue;
    if (!Array.isArray(values) || values.length === 0 || values.length > QUALIFIER_VALUES_MAX) return null;
    if (!values.every((v) => text(v, QUALIFIER_MAX)) || new Set(values).size !== values.length) return null;
    parsed[kind] = [...(values as string[])].sort();
  }
  return { householdId, normalizedName, qualifiers: parsed, dictionaryVersion };
}

const ANSWER_KEYS = ["item", "form", "volume", "use", "variant", "confidence", "reason"] as const;

/** AI の応答（JSON の文字列）を確かめる。違えば null */
export function parseAnswer(raw: string): JudgeAnswer | null {
  let o: unknown;
  try {
    o = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!isRecord(o) || !hasOnly(o, ANSWER_KEYS) || !ANSWER_KEYS.every((k) => k in o)) return null;
  // 前後の空白は AI の書き方の揺れなので、落としてから確かめる
  const trim = (v: unknown) => (typeof v === "string" ? v.trim() : v);
  const [item, form, volume, use, variant, confidence, reason] = ANSWER_KEYS.map((k) => trim(o[k]));
  if (!optText(item, ITEM_MAX)) return null;
  for (const v of [form, volume, use, variant]) if (!optText(v, ATTRIBUTE_MAX)) return null;
  if (typeof confidence !== "number" || !Number.isFinite(confidence) || confidence < 0 || confidence > 1) return null;
  if (!text(reason, REASON_MAX)) return null;
  return {
    item,
    form: form as string | null,
    volume: volume as string | null,
    use: use as string | null,
    variant: variant as string | null,
    confidence,
    reason,
  };
}
