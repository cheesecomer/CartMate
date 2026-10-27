import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "vitest";
import { as, HID, seed, seedHousehold } from "./setup";

function product(createdBy: string, extra: Record<string, unknown> = {}) {
  return {
    displayName: "牛乳",
    normalizedName: "牛乳",
    searchKey: "牛乳",
    createdBy,
    createdAt: serverTimestamp(),
    mergedInto: null,
    ...extra,
  };
}

/** ルールを通さずに置く商品 */
async function seedProduct(id: string, extra: Record<string, unknown> = {}) {
  await seed(`households/${HID}/products/${id}`, { ...product("taro"), createdAt: new Date(), ...extra });
}

const path = (id: string) => `households/${HID}/products/${id}`;

describe("商品", () => {
  it("メンバーは商品を作れる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(setDoc(doc(as("hanako"), path("n_milk")), product("hanako")));
  });

  it("メンバーでなければ作れない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("jiro"), path("n_milk")), product("jiro")));
  });

  it("表記ゆれのチップを選んだら、未統合の商品に統合した状態で作れる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    const merged = product("hanako", { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "hanako" });
    await assertSucceeds(setDoc(doc(as("hanako"), path("n_milk1l")), merged));
  });

  it("統合済みの商品に統合した状態では作れない（統合先は未統合の商品）", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk", { mergedInto: "n_dairy" });
    const merged = product("taro", { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "taro" });
    await assertFails(setDoc(doc(as("taro"), path("n_milk1l")), merged));
  });

  it("統合した人を偽れない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    const merged = product("hanako", { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "taro" });
    await assertFails(setDoc(doc(as("hanako"), path("n_milk1l")), merged));
  });

  it("あとから統合し、統合を解除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await seedProduct("n_gyunyu");
    const db = as("hanako");
    await assertSucceeds(updateDoc(doc(db, path("n_gyunyu")), { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "hanako" }));
    await assertSucceeds(updateDoc(doc(db, path("n_gyunyu")), { mergedInto: null }));
  });

  it("2つの端末が同じ商品を作っても、同じ ID で作り直せる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await assertSucceeds(setDoc(doc(as("hanako"), path("n_milk")), product("hanako", { displayName: "ぎゅうにゅう" })));
  });

  it("作り直しで正規化した名前は変えられない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await assertFails(setDoc(doc(as("hanako"), path("n_milk")), product("hanako", { normalizedName: "豆乳" })));
  });

  it("統合済みの商品には、あとからも統合できない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk", { mergedInto: "n_dairy" });
    await seedProduct("n_gyunyu");
    await assertFails(
      updateDoc(doc(as("taro"), path("n_gyunyu")), { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "taro" }),
    );
  });

  it("自分自身には統合できない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await assertFails(
      updateDoc(doc(as("taro"), path("n_milk")), { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "taro" }),
    );
  });

  it("商品は削除できない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await assertFails(deleteDoc(doc(as("taro"), path("n_milk"))));
  });

  it("辞書の名寄せを外し、元に戻せる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_tamago");
    const db = as("hanako");
    await assertSucceeds(updateDoc(doc(db, path("n_tamago")), { dictionaryOptOut: true }));
    await assertSucceeds(updateDoc(doc(db, path("n_tamago")), { dictionaryOptOut: false }));
  });

  it("辞書の名寄せの設定は真偽値だけ", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_tamago");
    await assertFails(updateDoc(doc(as("taro"), path("n_tamago")), { dictionaryOptOut: "yes" }));
  });

  it("名寄せの設定と一緒に、ほかのフィールドは変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_tamago");
    await assertFails(updateDoc(doc(as("taro"), path("n_tamago")), { dictionaryOptOut: true, normalizedName: "卵" }));
  });

  it("メンバーでなければ名寄せの設定を変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_tamago");
    await assertFails(updateDoc(doc(as("jiro"), path("n_tamago")), { dictionaryOptOut: true }));
  });
});
