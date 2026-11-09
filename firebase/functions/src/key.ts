import { createHash } from "node:crypto";

/** 属性の種類（domain の Qualifier.kind） */
export const QUALIFIER_KINDS = ["form", "use", "variant", "volume"] as const;
export type QualifierKind = (typeof QUALIFIER_KINDS)[number];
export type Qualifiers = Partial<Record<QualifierKind, string[]>>;

/**
 * 判定のキー（docs/design/data-model.md「AI の名寄せ」）。アプリの JudgementKey と同じ計算で、
 * test/judgement-keys.json の組を両方のテストで確かめる
 */
export function judgementKey(normalizedName: string, qualifiers: Qualifiers): string {
  const lines = QUALIFIER_KINDS.flatMap((kind) => (qualifiers[kind] ?? []).map((value) => `${kind}=${value}`)).sort();
  const canonical = ["v1", normalizedName, ...lines].join("\n");
  return createHash("sha256").update(canonical, "utf8").digest("hex").slice(0, 32);
}
