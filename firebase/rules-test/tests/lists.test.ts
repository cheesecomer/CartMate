import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type Firestore,
} from "firebase/firestore";
import { describe, it } from "vitest";
import { as, HID, seed, seedHousehold } from "./setup";

function list(createdBy: string, visibility: "shared" | "private") {
  return {
    name: "スーパー",
    memo: null,
    visibility,
    sortKey: 1,
    createdBy,
    createdAt: serverTimestamp(),
    status: "active",
  };
}

const PRODUCT = "n_milk";

function product(createdBy: string) {
  return {
    displayName: "牛乳",
    normalizedName: "牛乳",
    searchKey: "牛乳",
    createdBy,
    createdAt: serverTimestamp(),
    mergedInto: null,
  };
}

function item(listId: string, createdBy: string, visibility: "shared" | "private") {
  return {
    listId,
    productId: PRODUCT,
    inputName: "牛乳",
    sortKey: 1,
    quantity: 1,
    unit: "本",
    memo: null,
    visibility,
    status: "open",
    createdBy,
    createdAt: serverTimestamp(),
    completedAt: null,
    completedBy: null,
  };
}

/** 項目の追加。商品（決定的ID）も同じバッチで書く */
function addItem(db: Firestore, itemId: string, data: Record<string, unknown>) {
  return writeBatch(db)
    .set(doc(db, `households/${HID}/products/${PRODUCT}`), product(data.createdBy as string))
    .set(doc(db, `households/${HID}/items/${itemId}`), data)
    .commit();
}

async function seedList(listId: string, createdBy: string, visibility: "shared" | "private") {
  await seed(`households/${HID}/lists/${listId}`, { ...list(createdBy, visibility), createdAt: new Date() });
}

describe("リスト", () => {
  it("メンバーは共有のリストもプライベートのリストも作れる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(setDoc(doc(as("hanako"), `households/${HID}/lists/l1`), list("hanako", "shared")));
    await assertSucceeds(setDoc(doc(as("hanako"), `households/${HID}/lists/l2`), list("hanako", "private")));
  });

  it("メンバーでなければ作れない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("jiro"), `households/${HID}/lists/l1`), list("jiro", "shared")));
  });

  it("ほかの人が作ったことにはできない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertFails(setDoc(doc(as("hanako"), `households/${HID}/lists/l1`), list("taro", "shared")));
  });

  it("共有のリストはメンバー全員が読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await assertSucceeds(getDoc(doc(as("hanako"), `households/${HID}/lists/l1`)));
  });

  it("プライベートのリストは作った本人だけが読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "private");
    await assertSucceeds(getDoc(doc(as("taro"), `households/${HID}/lists/l1`)));
    await assertFails(getDoc(doc(as("hanako"), `households/${HID}/lists/l1`)));
  });

  it("公開範囲で分けた購読は、それぞれ許可される", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await seedList("l2", "taro", "private");
    const lists = collection(as("hanako"), `households/${HID}/lists`);
    await assertSucceeds(getDocs(query(lists, where("visibility", "==", "shared"))));
    await assertSucceeds(
      getDocs(query(lists, where("visibility", "==", "private"), where("createdBy", "==", "hanako"))),
    );
  });

  it("ほかの人のプライベートを含む購読は拒否される", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "private");
    await assertFails(getDocs(collection(as("hanako"), `households/${HID}/lists`)));
  });

  it("公開範囲は変えられない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "private");
    await assertFails(updateDoc(doc(as("taro"), `households/${HID}/lists/l1`), { visibility: "shared" }));
  });

  it("名前と備考を変えられる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await assertSucceeds(updateDoc(doc(as("hanako"), `households/${HID}/lists/l1`), { name: "イオン", memo: "火曜市" }));
  });

  it("項目の追加と同じバッチで、前回の買い物のチェック済みを片付けられる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    const db = as("hanako");
    await assertSucceeds(
      writeBatch(db)
        .update(doc(db, `households/${HID}/lists/l1`), { clearedAt: new Date() })
        .set(doc(db, `households/${HID}/products/${PRODUCT}`), product("hanako"))
        .set(doc(db, `households/${HID}/items/i1`), item("l1", "hanako", "shared"))
        .commit(),
    );
  });

  it("片付けた時刻は、未来の時刻にできない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "shared");
    await assertFails(
      updateDoc(doc(as("taro"), `households/${HID}/lists/l1`), { clearedAt: new Date(Date.now() + 60 * 60 * 1000) }),
    );
  });

  it("ほかの人のプライベートなリストは片付けられない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "private");
    await assertFails(updateDoc(doc(as("hanako"), `households/${HID}/lists/l1`), { clearedAt: new Date() }));
  });

  it("リストは物理削除できない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "shared");
    await assertFails(deleteDoc(doc(as("taro"), `households/${HID}/lists/l1`)));
  });
});

describe("項目の追加", () => {
  it("共有のリストに共有の項目を追加できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await assertSucceeds(addItem(as("hanako"), "i1", item("l1", "hanako", "shared")));
  });

  it("共有のリストにプライベートの項目を追加できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await assertSucceeds(addItem(as("hanako"), "i1", item("l1", "hanako", "private")));
  });

  it("自分のプライベートのリストには、プライベートの項目だけを追加できる", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "private");
    await assertSucceeds(addItem(as("taro"), "i1", item("l1", "taro", "private")));
    await assertFails(addItem(as("taro"), "i2", item("l1", "taro", "shared")));
  });

  it("ほかの人のプライベートのリストには追加できない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "private");
    await assertFails(addItem(as("hanako"), "i1", item("l1", "hanako", "private")));
  });

  it("商品がなければ追加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "shared");
    await assertFails(setDoc(doc(as("taro"), `households/${HID}/items/i1`), item("l1", "taro", "shared")));
  });

  it("チェック済みの状態では追加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "shared");
    await assertFails(addItem(as("taro"), "i1", { ...item("l1", "taro", "shared"), status: "completed" }));
  });

  it("メンバーでなければ追加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedList("l1", "taro", "shared");
    await assertFails(addItem(as("jiro"), "i1", item("l1", "jiro", "shared")));
  });

  it("プライベートの項目は作った本人だけが読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await seed(`households/${HID}/items/i1`, { ...item("l1", "taro", "private"), createdAt: new Date() });
    await assertSucceeds(getDoc(doc(as("taro"), `households/${HID}/items/i1`)));
    await assertFails(getDoc(doc(as("hanako"), `households/${HID}/items/i1`)));
  });

  it("未購入の項目は削除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await seed(`households/${HID}/items/i1`, { ...item("l1", "taro", "shared"), createdAt: new Date() });
    await assertSucceeds(deleteDoc(doc(as("hanako"), `households/${HID}/items/i1`)));
  });
});

/** 端末でチェックした時刻。項目と購入の記録に同じ値を書く */
const CHECKED_AT = new Date();

/** アプリの FirestoreShoppingStore.completeItem と同じ書き込み */
function complete(
  db: Firestore,
  uid: string,
  itemId: string,
  purchase: Record<string, unknown> = {},
  checkedAt: Date = CHECKED_AT,
) {
  return writeBatch(db)
    .update(doc(db, `households/${HID}/items/${itemId}`), {
      status: "completed",
      completedAt: serverTimestamp(),
      completedBy: uid,
      checkedAt,
    })
    .set(doc(db, `households/${HID}/purchases/${itemId}`), {
      itemId,
      listId: "l1",
      listNameSnapshot: "スーパー",
      productId: PRODUCT,
      inputName: "牛乳",
      quantity: 1,
      unit: "本",
      memo: null,
      visibility: "shared",
      checkedAt: CHECKED_AT,
      purchasedAt: serverTimestamp(),
      purchasedBy: uid,
      active: true,
      revokedAt: null,
      revokedBy: null,
      ...purchase,
    })
    .commit();
}

/** アプリの FirestoreShoppingStore.reopenItem と同じ書き込み */
function reopen(db: Firestore, uid: string, itemId: string) {
  return writeBatch(db)
    .update(doc(db, `households/${HID}/items/${itemId}`), {
      status: "open",
      completedAt: null,
      completedBy: null,
      checkedAt: null,
    })
    .update(doc(db, `households/${HID}/purchases/${itemId}`), {
      active: false,
      revokedAt: serverTimestamp(),
      revokedBy: uid,
    })
    .commit();
}

describe("チェックと Undo", () => {
  async function seedOpenItem(visibility: "shared" | "private" = "shared", createdBy = "taro") {
    await seedHousehold(HID, "taro", "hanako");
    await seedList("l1", "taro", "shared");
    await seed(`households/${HID}/products/${PRODUCT}`, { ...product("taro"), createdAt: new Date() });
    await seed(`households/${HID}/items/i1`, { ...item("l1", createdBy, visibility), createdAt: new Date() });
  }

  it("チェックと購入の記録を同時に書ける", async () => {
    await seedOpenItem();
    await assertSucceeds(complete(as("hanako"), "hanako", "i1"));
  });

  it("購入の記録なしではチェックできない", async () => {
    await seedOpenItem();
    await assertFails(
      updateDoc(doc(as("hanako"), `households/${HID}/items/i1`), {
        status: "completed",
        completedAt: serverTimestamp(),
        completedBy: "hanako",
      }),
    );
  });

  it("購入の記録の内容は項目とリストに一致しなければならない", async () => {
    await seedOpenItem();
    await assertFails(complete(as("hanako"), "hanako", "i1", { listNameSnapshot: "別の名前" }));
    await assertFails(complete(as("hanako"), "hanako", "i1", { quantity: 3 }));
  });

  it("項目と購入の記録で、チェックした時刻が食い違う書き込みは拒否される", async () => {
    await seedOpenItem();
    await assertFails(complete(as("hanako"), "hanako", "i1", {}, new Date(CHECKED_AT.getTime() - 60_000)));
  });

  it("チェックを外すときは、チェックした時刻も消さなければならない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    const db = as("taro");
    await assertFails(
      writeBatch(db)
        .update(doc(db, `households/${HID}/items/i1`), { status: "open", completedAt: null, completedBy: null })
        .update(doc(db, `households/${HID}/purchases/i1`), { active: false, revokedAt: serverTimestamp(), revokedBy: "taro" })
        .commit(),
    );
  });

  it("購入日時はサーバーの時刻でなければならない", async () => {
    await seedOpenItem();
    await assertFails(complete(as("hanako"), "hanako", "i1", { purchasedAt: new Date() }));
  });

  it("チェックした時刻は、5分より先や7日より前にできない", async () => {
    await seedOpenItem();
    const at = (minutes: number) => new Date(Date.now() + minutes * 60_000);
    const db = as("hanako");
    await assertFails(complete(db, "hanako", "i1", { checkedAt: at(6) }, at(6)));
    await assertFails(complete(db, "hanako", "i1", { checkedAt: at(-8 * 24 * 60) }, at(-8 * 24 * 60)));
    // オフラインのまま数日たってから届いたチェックは受け付ける
    await assertSucceeds(complete(db, "hanako", "i1", { checkedAt: at(-6 * 24 * 60) }, at(-6 * 24 * 60)));
  });

  it("購入の記録の公開範囲と備考は項目に一致しなければならない", async () => {
    await seedOpenItem();
    await assertFails(complete(as("hanako"), "hanako", "i1", { visibility: "private" }));
    await assertFails(complete(as("hanako"), "hanako", "i1", { memo: "特売なら" }));
  });

  it("チェックした人を偽れない", async () => {
    await seedOpenItem();
    await assertFails(complete(as("hanako"), "taro", "i1"));
  });

  it("チェック済みの項目はもう一度チェックできない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertFails(complete(as("hanako"), "hanako", "i1"));
  });

  it("チェックを外すと購入の記録が無効になる", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertSucceeds(reopen(as("hanako"), "hanako", "i1"));
  });

  it("購入の記録を無効にせずにチェックを外せない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertFails(
      updateDoc(doc(as("taro"), `households/${HID}/items/i1`), { status: "open", completedAt: null, completedBy: null }),
    );
  });

  it("項目のチェックを外さずに、購入の記録だけを無効にできない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertFails(
      updateDoc(doc(as("taro"), `households/${HID}/purchases/i1`), { active: false, revokedAt: serverTimestamp(), revokedBy: "taro" }),
    );
  });

  it("購入の記録は削除できない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertFails(deleteDoc(doc(as("taro"), `households/${HID}/purchases/i1`)));
    await reopen(as("taro"), "taro", "i1");
    await assertFails(deleteDoc(doc(as("taro"), `households/${HID}/purchases/i1`)));
  });

  it("Undo のあとにもう一度チェックできる", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await reopen(as("taro"), "taro", "i1");
    await assertSucceeds(complete(as("hanako"), "hanako", "i1"));
  });

  it("チェック済みの項目は削除できない", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertFails(deleteDoc(doc(as("taro"), `households/${HID}/items/i1`)));
  });

  it("プライベートな項目の購入の記録は本人だけが読める", async () => {
    await seedOpenItem("private", "taro");
    await assertSucceeds(complete(as("taro"), "taro", "i1", { visibility: "private" }));
    await assertSucceeds(getDoc(doc(as("taro"), `households/${HID}/purchases/i1`)));
    await assertFails(getDoc(doc(as("hanako"), `households/${HID}/purchases/i1`)));
  });

  it("共有の購入の記録はメンバー全員が読める", async () => {
    await seedOpenItem();
    await complete(as("taro"), "taro", "i1");
    await assertSucceeds(getDoc(doc(as("hanako"), `households/${HID}/purchases/i1`)));
  });
});
