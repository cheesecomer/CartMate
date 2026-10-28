import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, deleteField, doc, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch, type Firestore } from "firebase/firestore";
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

/** アプリの FirestoreProductRepository と同じ書き込み（docs/design/data-model.md「統合」） */
const merging = (uid: string, into: string) => ({
  mergedInto: into,
  mergedAt: serverTimestamp(),
  mergedBy: uid,
  mergeUpdatedAt: serverTimestamp(),
  mergeUpdatedBy: uid,
});
const repointing = (uid: string, into: string) => ({ mergedInto: into, mergeUpdatedAt: serverTimestamp(), mergeUpdatedBy: uid });
const unmerging = (uid: string) => ({
  mergedInto: null,
  mergedAt: null,
  mergedBy: null,
  mergeUpdatedAt: serverTimestamp(),
  mergeUpdatedBy: uid,
});

/** [changes] を1つのバッチで書く */
function commit(db: Firestore, changes: [string, Record<string, unknown>][]) {
  const batch = writeBatch(db);
  for (const [id, data] of changes) batch.update(doc(db, path(id)), data);
  return batch.commit();
}

/** 「豚バラ」→「豚ばら肉」→「豚バラ肉」の多段の統合 */
async function seedChain() {
  await seedHousehold(HID, "taro", "hanako");
  await seedProduct("n_target");
  await seedProduct("n_middle", { mergedInto: "n_target", mergedBy: "taro", mergedAt: new Date() });
  await seedProduct("n_leaf", { mergedInto: "n_middle", mergedBy: "taro", mergedAt: new Date() });
}

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
    const merged = product("hanako", merging("hanako", "n_milk"));
    await assertSucceeds(setDoc(doc(as("hanako"), path("n_milk1l")), merged));
  });

  it("統合済みの商品に統合した状態では作れない（統合先は未統合の商品）", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk", { mergedInto: "n_dairy" });
    const merged = product("taro", merging("taro", "n_milk"));
    await assertFails(setDoc(doc(as("taro"), path("n_milk1l")), merged));
  });

  it("統合した人を偽れない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    const merged = product("hanako", { ...merging("hanako", "n_milk"), mergedBy: "taro" });
    await assertFails(setDoc(doc(as("hanako"), path("n_milk1l")), merged));
  });

  it("あとから統合し、統合を解除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await seedProduct("n_gyunyu");
    const db = as("hanako");
    await assertSucceeds(updateDoc(doc(db, path("n_gyunyu")), merging("hanako", "n_milk")));
    await assertSucceeds(updateDoc(doc(db, path("n_gyunyu")), unmerging("hanako")));
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
    await assertFails(updateDoc(doc(as("taro"), path("n_gyunyu")), merging("taro", "n_milk")));
  });

  it("自分自身には統合できない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await assertFails(updateDoc(doc(as("taro"), path("n_milk")), merging("taro", "n_milk")));
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

describe("商品をまとめる・解除する", () => {
  it("統合関係を変えた人と時刻がなければ、統合も解除もできない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await seedProduct("n_gyunyu");
    const db = as("taro");
    await assertFails(updateDoc(doc(db, path("n_gyunyu")), { mergedInto: "n_milk", mergedAt: serverTimestamp(), mergedBy: "taro" }));
    await updateDoc(doc(db, path("n_gyunyu")), merging("taro", "n_milk"));
    await assertFails(updateDoc(doc(db, path("n_gyunyu")), { mergedInto: null }));
  });

  it("統合済みの商品の統合先を、直接ほかの商品に変えられない（いったん解除する）", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await seedProduct("n_soy");
    await seedProduct("n_gyunyu", { mergedInto: "n_milk", mergedBy: "taro", mergedAt: new Date() });
    const db = as("taro");
    await assertFails(updateDoc(doc(db, path("n_gyunyu")), merging("taro", "n_soy")));
    await assertFails(updateDoc(doc(db, path("n_gyunyu")), repointing("taro", "n_soy")));
  });

  it("中間の商品を解除すると、その下の商品を元の統合先に付け替えられる。最初に統合した人は変わらない", async () => {
    await seedChain();
    const db = as("hanako");
    await assertSucceeds(commit(db, [["n_middle", unmerging("hanako")], ["n_leaf", repointing("hanako", "n_target")]]));
    const leaf = (await getDoc(doc(db, path("n_leaf")))).data()!;
    if (leaf.mergedInto !== "n_target" || leaf.mergedBy !== "taro" || leaf.mergeUpdatedBy !== "hanako") {
      throw new Error(JSON.stringify(leaf));
    }
  });

  it("付け替えでは、最初に統合した人と時刻を書き換えられない", async () => {
    await seedChain();
    const db = as("hanako");
    await assertFails(
      commit(db, [["n_middle", unmerging("hanako")], ["n_leaf", { ...repointing("hanako", "n_target"), mergedBy: "hanako" }]]),
    );
  });

  it("中間の商品の解除と一緒でなければ、付け替えられない", async () => {
    await seedChain();
    await assertFails(commit(as("hanako"), [["n_leaf", repointing("hanako", "n_target")]]));
  });

  it("その下の商品がすでに解除されていたら、古い統合関係を前提にした付け替えは拒否される", async () => {
    await seedChain();
    // 母が先に「豚バラ」を解除した
    await commit(as("hanako"), [["n_leaf", unmerging("hanako")]]);
    // 父の「豚ばら肉を解除して、豚バラを豚バラ肉へ付け替える」が後から届く
    await assertFails(commit(as("taro"), [["n_middle", unmerging("taro")], ["n_leaf", repointing("taro", "n_target")]]));
  });

  it("ほかの商品をまとめている商品をまとめると、その下の商品も同じ統合先に付け替えられる", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_new_target");
    await seedProduct("n_middle");
    await seedProduct("n_leaf", { mergedInto: "n_middle", mergedBy: "taro", mergedAt: new Date() });
    await assertSucceeds(
      commit(as("taro"), [["n_middle", merging("taro", "n_new_target")], ["n_leaf", repointing("taro", "n_new_target")]]),
    );
  });

  it("まとめている商品の付け替え先は、その商品の新しい統合先と同じでなければならない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_new_target");
    await seedProduct("n_other");
    await seedProduct("n_middle");
    await seedProduct("n_leaf", { mergedInto: "n_middle", mergedBy: "taro", mergedAt: new Date() });
    await assertFails(
      commit(as("taro"), [["n_middle", merging("taro", "n_new_target")], ["n_leaf", repointing("taro", "n_other")]]),
    );
  });

  it("2人が逆向きに同時にまとめても、後から届いた方は拒否され、循環しない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_a");
    await seedProduct("n_b");
    await assertSucceeds(commit(as("taro"), [["n_a", merging("taro", "n_b")]]));
    await assertFails(commit(as("hanako"), [["n_b", merging("hanako", "n_a")]]));
  });

  it("ほかの商品をまとめている商品なら、一度に18件までまとめられる（ルールが読むドキュメントの上限）", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_target");
    for (let i = 0; i < 19; i++) {
      await seedProduct(`n_s${i}`);
      await seedProduct(`n_c${i}`, { mergedInto: `n_s${i}`, mergedBy: "taro", mergedAt: new Date() });
    }
    const changes = (n: number) =>
      Array.from({ length: n }).flatMap((_, i): [string, Record<string, unknown>][] => [
        [`n_s${i}`, merging("taro", "n_target")],
        [`n_c${i}`, repointing("taro", "n_target")],
      ]);
    await assertFails(commit(as("taro"), changes(19)));
    await assertSucceeds(commit(as("taro"), changes(18)));
  });
});

describe("名前の訂正", () => {
  it("正しい名前の商品を作り、同じ書き込みでそこにまとめられる（1件目の記録の訂正）", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_typo");
    const db = as("taro");
    await assertSucceeds(
      writeBatch(db)
        .set(doc(db, path("n_milk")), product("taro"))
        .update(doc(db, path("n_typo")), merging("taro", "n_milk"))
        .commit(),
    );
  });

  it("作らずに、ない商品にはまとめられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_typo");
    await assertFails(updateDoc(doc(as("taro"), path("n_typo")), merging("taro", "n_milk")));
  });

  it("この商品にまとめられている正しい名前の商品と入れ替えられる", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_typo");
    await seedProduct("n_milk", { mergedInto: "n_typo", mergedBy: "taro", mergedAt: new Date() });
    await seedProduct("n_other", { mergedInto: "n_typo", mergedBy: "taro", mergedAt: new Date() });
    await assertSucceeds(
      commit(as("taro"), [
        ["n_milk", unmerging("taro")],
        ["n_typo", merging("taro", "n_milk")],
        ["n_other", repointing("taro", "n_milk")],
      ]),
    );
  });
});

describe("商品の非表示", () => {
  const hiding = (uid: string) => ({ hiddenAt: serverTimestamp(), hiddenBy: uid });
  const showing = { hiddenAt: deleteField(), hiddenBy: deleteField() };

  it("メンバーは統合先の商品を非表示にして、また表示できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_test");
    await assertSucceeds(updateDoc(doc(as("hanako"), path("n_test")), hiding("hanako")));
    await assertSucceeds(updateDoc(doc(as("taro"), path("n_test")), showing));
  });

  it("統合されている商品は非表示にできない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await seedProduct("n_gyunyu", { mergedInto: "n_milk", mergedBy: "taro", mergedAt: new Date() });
    await assertFails(updateDoc(doc(as("taro"), path("n_gyunyu")), hiding("taro")));
  });

  it("非表示にした人と時刻は偽れない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_test");
    await assertFails(updateDoc(doc(as("hanako"), path("n_test")), hiding("taro")));
    await assertFails(updateDoc(doc(as("hanako"), path("n_test")), { hiddenAt: new Date(0), hiddenBy: "hanako" }));
  });

  it("非表示と一緒に、ほかのフィールドは変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_test");
    await assertFails(updateDoc(doc(as("taro"), path("n_test")), { ...hiding("taro"), displayName: "別の名前" }));
  });

  it("メンバーでなければ非表示にできない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_test");
    await assertFails(updateDoc(doc(as("jiro"), path("n_test")), hiding("jiro")));
  });
});

describe("常に非表示", () => {
  const excluding = (uid: string) => ({ suggestionExcluded: true, suggestionExcludedAt: serverTimestamp(), suggestionExcludedBy: uid });
  const including = { suggestionExcluded: false, suggestionExcludedAt: deleteField(), suggestionExcludedBy: deleteField() };

  it("メンバーは常に非表示にして、解除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await assertSucceeds(updateDoc(doc(as("hanako"), path("n_milk")), excluding("hanako")));
    await assertSucceeds(updateDoc(doc(as("taro"), path("n_milk")), including));
  });

  it("設定した人と時刻は偽れず、解除するときは消さなければならない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedProduct("n_milk");
    await assertFails(updateDoc(doc(as("hanako"), path("n_milk")), excluding("taro")));
    await assertFails(updateDoc(doc(as("hanako"), path("n_milk")), { suggestionExcluded: true }));
    await updateDoc(doc(as("hanako"), path("n_milk")), excluding("hanako"));
    await assertFails(updateDoc(doc(as("hanako"), path("n_milk")), { suggestionExcluded: false }));
  });

  it("ほかのフィールドと一緒には変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await assertFails(updateDoc(doc(as("taro"), path("n_milk")), { ...excluding("taro"), displayName: "別" }));
  });

  it("メンバーでなければ変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_milk");
    await assertFails(updateDoc(doc(as("jiro"), path("n_milk")), excluding("jiro")));
  });

  it("名前の訂正で作る商品は、常に非表示を引き継いで作れる", async () => {
    await seedHousehold(HID, "taro");
    await seedProduct("n_vague", { suggestionExcluded: true, suggestionExcludedBy: "taro", suggestionExcludedAt: new Date() });
    const db = as("taro");
    await assertSucceeds(
      writeBatch(db)
        .set(doc(db, path("n_hook")), { ...product("taro"), ...excluding("taro") })
        .update(doc(db, path("n_vague")), merging("taro", "n_hook"))
        .commit(),
    );
    await assertFails(setDoc(doc(db, path("n_other")), { ...product("taro"), ...excluding("hanako") }));
  });
});
