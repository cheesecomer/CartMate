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
