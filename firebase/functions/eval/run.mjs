// AI の名寄せの精度を確かめる（docs/design/suggestions.md「精度の確かめ方」）。本物の API を呼ぶので CI では流さず、
// モデルやプロンプトを変えるときに手元で使う。関数と同じプロンプト・スキーマ・応答の検証（lib/）を使う。
//
//   npm run build
//   OPENAI_API_KEY=... node eval/run.mjs                                   # 関数と同じ gpt-6-luna
//   ANTHROPIC_API_KEY=... node eval/run.mjs --provider anthropic           # 比べる相手（claude-haiku-5-5）
//
// 候補にするかは、アプリの AiCandidates と同じ規則を近似して判定する: 両方の品目が同じ（辞書にある名前なら辞書の品目で比べる）、
// 属性（形態・用途・種類・容量）が一致（未指定と指定ありは不一致）、信頼度が 0.7 以上。
// 名前だけを送る（備考の属性は付けない）。cases.csv の expect は same（候補にすべき）か different（候補にしてはいけない）
import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import Anthropic from "@anthropic-ai/sdk";
import { ANSWER_SCHEMA, costMicroUsd, INSTRUCTIONS, MODEL, openAiClient } from "../lib/ai.js";
import { parseAnswer } from "../lib/validate.js";

const MIN_CONFIDENCE = 0.7;
/** claude-haiku-5-5 の料金（100万トークンあたり $0.10 / $0.50）を、1トークンあたりの100万分の1ドルにしたもの */
const HAIKU_PRICE = { input: 0.1, output: 0.5 };

const { values: args } = parseArgs({
  options: {
    provider: { type: "string", default: "openai" },
    model: { type: "string" },
    cases: { type: "string", default: new URL("./cases.csv", import.meta.url).pathname },
  },
});

/** 判定する。応答の文（JSON）と、金額（100万分の1ドル） */
function client() {
  if (args.provider === "openai") {
    if (args.model && args.model !== MODEL) throw new Error(`OpenAI は関数と同じ ${MODEL} だけを使う（ai.ts を変えて試す）`);
    const ai = openAiClient(() => required("OPENAI_API_KEY"));
    return { model: MODEL, judge: async (name) => {
      const result = await ai.judge({ name, qualifiers: {} });
      return { text: result.text, cost: costMicroUsd(result.usage) };
    } };
  }
  if (args.provider === "anthropic") {
    const anthropic = new Anthropic({ apiKey: required("ANTHROPIC_API_KEY") });
    const model = args.model ?? "claude-haiku-5-5";
    return { model, judge: async (name) => {
      const response = await anthropic.messages.create({
        model,
        max_tokens: 1024,
        system: INSTRUCTIONS,
        messages: [{ role: "user", content: JSON.stringify({ name, qualifiers: {} }) }],
        // 関数（推論なし）と条件をそろえる。Claude Haiku 5.5 は effort が high 以下なら止められる
        thinking: { type: "disabled" },
        output_config: { format: { type: "json_schema", schema: ANSWER_SCHEMA } },
      });
      if (response.stop_reason === "refusal") return { text: "", cost: 0 };
      const text = response.content.filter((b) => b.type === "text").map((b) => b.text).join("");
      const cost = Math.ceil(response.usage.input_tokens * HAIKU_PRICE.input + response.usage.output_tokens * HAIKU_PRICE.output);
      return { text, cost };
    } };
  }
  throw new Error(`知らない provider: ${args.provider}`);
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} を環境変数に設定してください（リポジトリには書かない）`);
  return value;
}

// アプリの NameNormalizer.normalizedName と同じ（NFKC、前後の空白、連続した空白、英字の小文字）
const normalize = (s) => s.normalize("NFKC").trim().replace(/\s+/g, " ").toLowerCase();

/** 辞書の別名（既定の同義語を除く）と品目の名前から、正規化した名前 → 品目の ID */
function loadDictionary() {
  const dir = new URL("../../../dictionary/", import.meta.url);
  const rows = (file) => readFileSync(new URL(file, dir), "utf8").trim().split("\n").slice(1).map((l) => l.split(","));
  const map = new Map();
  for (const [id, name] of rows("items.csv")) map.set(normalize(name), id);
  for (const [alias, id, match] of rows("aliases.csv")) if (match !== "default") map.set(normalize(alias), id);
  return map;
}

// アプリの AiCandidates.comparable と同じ（正規化したうえで、カタカナをひらがなにそろえる）
const comparable = (s) => normalize(s).replace(/[\u30a1-\u30f6]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0x60));

function identity(answer, dictionary) {
  if (!answer?.item || answer.confidence < MIN_CONFIDENCE) return null;
  const item = normalize(answer.item);
  const canonical = dictionary.get(item);
  const attr = (v) => (v == null ? null : comparable(v) || null);
  return {
    item: canonical ? `c:${canonical}` : `n:${comparable(item)}`,
    attributes: [answer.form, answer.use, answer.variant, answer.volume].map(attr).join("|"),
  };
}

const ai = client();
const dictionary = loadDictionary();
const cases = readFileSync(args.cases, "utf8").trim().split("\n").slice(1).map((l) => {
  const [expect, left, right] = l.split(",");
  return { expect, left: normalize(left), right: normalize(right) };
});

const answers = new Map();
let cost = 0;
for (const name of new Set(cases.flatMap((c) => [c.left, c.right]))) {
  try {
    const result = await ai.judge(name);
    cost += result.cost;
    answers.set(name, parseAnswer(result.text));
  } catch (e) {
    console.error(`${name}: ${e}`);
    answers.set(name, null);
  }
}

let correct = 0;
const counts = { tp: 0, fp: 0, fn: 0, tn: 0 };
for (const c of cases) {
  const [a, b] = [identity(answers.get(c.left), dictionary), identity(answers.get(c.right), dictionary)];
  const candidate = a != null && b != null && a.item === b.item && a.attributes === b.attributes;
  const ok = candidate === (c.expect === "same");
  if (ok) correct++;
  counts[candidate ? (ok ? "tp" : "fp") : ok ? "tn" : "fn"]++;
  if (!ok) {
    const show = (n) => JSON.stringify({ name: n, ...answers.get(n) });
    console.log(`NG ${c.expect}: ${show(c.left)} / ${show(c.right)}`);
  }
}

const invalid = [...answers.values()].filter((a) => a == null).length;
console.log(`
モデル: ${ai.model}
正解: ${correct} / ${cases.length}（${((correct / cases.length) * 100).toFixed(1)}%）
同じと判定すべき組: ${counts.tp} / ${counts.tp + counts.fn} を候補にした（取りこぼし ${counts.fn}）
別と判定すべき組: ${counts.fp} 件を誤って候補にした（誤ってまとめる候補。0 にしたい）
応答の形が違った名前: ${invalid} / ${answers.size}
費用: ${cost} / 1,000,000 ドル（名前 ${answers.size} 件）`);
