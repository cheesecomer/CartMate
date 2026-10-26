import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, Timestamp, writeBatch, type Firestore } from "firebase/firestore";
import { describe, it } from "vitest";
import { as, env, HID, OTHER_HID, seed, seedHousehold } from "./setup";

// hanako は自分しかいない家族 OTHER_HID から、taro の家族 HID に合流する
const FROM = OTHER_HID;
const TO = HID;
const PRODUCT = "n_milk";
const createdAt = Timestamp.fromMillis(Date.parse("2026-09-01T00:00:00Z"));

const sourceList = {
  name: "スーパー",
  memo: null,
  visibility: "shared",
  sortKey: 1,
  createdBy: "hanako",
  createdAt,
  status: "active",
};

const sourceItem = {
  listId: "l1",
  productId: PRODUCT,
  inputName: "牛乳",
  sortKey: 1,
  quantity: 1,
  unit: "本",
  memo: null,
  visibility: "shared",
  status: "open",
  createdBy: "hanako",
  createdAt,
  completedAt: null,
  completedBy: null,
};

const sourceProduct = {
  displayName: "牛乳",
  normalizedName: "牛乳",
  searchKey: "牛乳",
  createdBy: "hanako",
  createdAt,
  mergedInto: null,
};

/** 合流の最初の手順（合流先に参加する）までが終わった状態 */
async function seedMerging() {
  await seedHousehold(TO, "taro");
  await seedHousehold(FROM, "hanako");
  // 合流先の members（招待で参加済み）。所属（users）はまだ元の家族のまま
  await seed(`households/${TO}/members/hanako`, {
    role: "member",
    displayName: "hanako",
    photoUrl: null,
    joinedAt: Timestamp.now(),
    joinedVia: "7KQ2M9XPHA",
  });
  await seed(`households/${FROM}/lists/l1`, sourceList);
  await seed(`households/${FROM}/items/i1`, sourceItem);
  await seed(`households/${FROM}/products/${PRODUCT}`, sourceProduct);
}

/** アプリの FamilyMerger.copy と同じ書き込み */
function copy(db: Firestore, path: string, data: Record<string, unknown>) {
  return writeBatch(db).set(doc(db, `households/${TO}/${path}`), { ...data, importedFrom: FROM }).commit();
}

describe("合流", () => {
  it("商品・リスト・項目を、作成日時も含めてそのまま写せる", async () => {
    await seedMerging();
    const db = as("hanako");
    await assertSucceeds(copy(db, `products/${PRODUCT}`, sourceProduct));
    await assertSucceeds(copy(db, "lists/l1", { ...sourceList, sortKey: 5 }));
    await assertSucceeds(copy(db, "items/i1", sourceItem));
  });

  it("リストは並び順（sortKey）だけを変えられる", async () => {
    await seedMerging();
    await assertFails(copy(as("hanako"), "lists/l1", { ...sourceList, name: "別の名前" }));
  });

  it("項目の内容を変えて写せない", async () => {
    await seedMerging();
    await assertFails(copy(as("hanako"), "items/i1", { ...sourceItem, createdAt: Timestamp.now() }));
    await assertFails(copy(as("hanako"), "items/i1", { ...sourceItem, sortKey: 9 }));
  });

  it("コピー元にないドキュメントは写せない", async () => {
    await seedMerging();
    await assertFails(copy(as("hanako"), "items/i2", sourceItem));
  });

  it("コピー元の家族のメンバーでなければ写せない", async () => {
    await seedMerging();
    // jiro は合流先のメンバーにしておく
    await seed(`households/${TO}/members/jiro`, {
      role: "member",
      displayName: "jiro",
      photoUrl: null,
      joinedAt: Timestamp.now(),
      joinedVia: null,
    });
    await assertFails(copy(as("jiro"), "items/i1", { ...sourceItem }));
  });

  it("合流先のメンバーでなければ写せない", async () => {
    await seedHousehold(TO, "taro");
    await seedHousehold(FROM, "hanako");
    await seed(`households/${FROM}/items/i1`, sourceItem);
    await assertFails(copy(as("hanako"), "items/i1", sourceItem));
  });

  it("ほかの人のプライベートな項目は写せない", async () => {
    await seedMerging();
    await seed(`households/${FROM}/members/kenta`, {
      role: "member",
      displayName: "kenta",
      photoUrl: null,
      joinedAt: Timestamp.now(),
      joinedVia: null,
    });
    const privateItem = { ...sourceItem, visibility: "private", createdBy: "kenta" };
    await seed(`households/${FROM}/items/i2`, privateItem);
    await assertFails(copy(as("hanako"), "items/i2", privateItem));
  });

  it("写し終えたら、所属を切り替えて元の家族を抜けられる", async () => {
    await seedMerging();
    const db = as("hanako");
    await assertSucceeds(
      writeBatch(db)
        .set(doc(db, "users/hanako"), { householdId: TO, updatedAt: Timestamp.now() })
        .delete(doc(db, `households/${FROM}/members/hanako`))
        .commit(),
    );
    await env.withSecurityRulesDisabled(async (context) => {
      const user = await getDoc(doc(context.firestore(), "users/hanako"));
      if (user.get("householdId") !== TO) throw new Error("所属が切り替わっていない");
    });
  });
});
