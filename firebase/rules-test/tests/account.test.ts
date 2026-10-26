import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, Timestamp } from "firebase/firestore";
import { describe, it } from "vitest";
import { as, HID, seed, seedHousehold } from "./setup";

const now = Timestamp.now();

async function seedData(owner: string, visibility: "shared" | "private", status: "open" | "completed" = "open") {
  await seed(`households/${HID}/lists/l-${owner}-${visibility}`, {
    name: "スーパー",
    memo: null,
    visibility,
    sortKey: 1,
    createdBy: owner,
    createdAt: now,
    status: "active",
  });
  await seed(`households/${HID}/items/i-${owner}-${visibility}`, {
    listId: `l-${owner}-${visibility}`,
    productId: "n_milk",
    inputName: "牛乳",
    sortKey: 1,
    quantity: 1,
    unit: "本",
    memo: null,
    visibility,
    status,
    createdBy: owner,
    createdAt: now,
    completedAt: status === "completed" ? now : null,
    completedBy: status === "completed" ? owner : null,
  });
  await seed(`households/${HID}/purchases/i-${owner}-${visibility}`, {
    itemId: `i-${owner}-${visibility}`,
    listId: `l-${owner}-${visibility}`,
    visibility,
    purchasedBy: owner,
    purchasedAt: now,
    active: true,
  });
}

/** アプリの AccountDeletion と同じ削除 */
function deleteOwn(uid: string, visibility: "shared" | "private", owner = uid) {
  const db = as(uid);
  return Promise.all([
    deleteDoc(doc(db, `households/${HID}/purchases/i-${owner}-${visibility}`)),
    deleteDoc(doc(db, `households/${HID}/items/i-${owner}-${visibility}`)),
    deleteDoc(doc(db, `households/${HID}/lists/l-${owner}-${visibility}`)),
  ]);
}

describe("アカウントの削除", () => {
  it("自分のプライベートなリスト・項目（チェック済みも）・購入の記録を削除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedData("hanako", "private", "completed");
    await assertSucceeds(deleteOwn("hanako", "private"));
  });

  it("共有のリスト・チェック済みの項目・購入の記録は削除できない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedData("hanako", "shared", "completed");
    const db = as("hanako");
    await assertFails(deleteDoc(doc(db, `households/${HID}/purchases/i-hanako-shared`)));
    await assertFails(deleteDoc(doc(db, `households/${HID}/items/i-hanako-shared`)));
    await assertFails(deleteDoc(doc(db, `households/${HID}/lists/l-hanako-shared`)));
  });

  it("ほかの人のプライベートなデータは削除できない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedData("taro", "private", "completed");
    await assertFails(deleteOwn("hanako", "private", "taro"));
  });

  it("家族から外されたあとは、元の家族のデータを削除できない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedData("hanako", "private");
    await deleteDoc(doc(as("taro"), `households/${HID}/members/hanako`));
    await assertFails(deleteDoc(doc(as("hanako"), `households/${HID}/lists/l-hanako-private`)));
  });

  it("自分の users だけを削除できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(deleteDoc(doc(as("hanako"), "users/hanako")));
    await assertFails(deleteDoc(doc(as("hanako"), "users/taro")));
  });
});
