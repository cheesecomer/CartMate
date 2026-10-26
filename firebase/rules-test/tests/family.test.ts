import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
  type Firestore,
} from "firebase/firestore";
import { describe, it } from "vitest";
import { as, HID, OTHER_HID, seedHousehold, seedInvite, signedOut } from "./setup";

const CODE = "7KQ2M9XPHA";

function member(role: "owner" | "member", joinedVia: string | null) {
  return { role, displayName: "名前", photoUrl: null, joinedAt: serverTimestamp(), joinedVia };
}

function user(hid: string | null) {
  return { householdId: hid, updatedAt: serverTimestamp() };
}

/** アプリの FirestoreHouseholdRepository.createHousehold と同じ書き込み */
function createHousehold(db: Firestore, uid: string, hid: string) {
  return writeBatch(db)
    .set(doc(db, `households/${hid}`), { name: "太郎さんの家族", createdBy: uid, createdAt: serverTimestamp() })
    .set(doc(db, `households/${hid}/members/${uid}`), member("owner", null))
    .set(doc(db, `users/${uid}`), user(hid))
    .commit();
}

/** アプリの FirestoreHouseholdRepository.join と同じ書き込み */
function join(db: Firestore, uid: string, hid: string, code: string) {
  return writeBatch(db)
    .set(doc(db, `households/${hid}/members/${uid}`), member("member", code))
    .update(doc(db, `invites/${code}`), { usedCount: increment(1) })
    .set(doc(db, `users/${uid}`), user(hid))
    .commit();
}

describe("家族の作成", () => {
  it("家族・オーナーの members・users を1つのバッチで作れる", async () => {
    await assertSucceeds(createHousehold(as("taro"), "taro", HID));
  });

  it("ログインしていなければ作れない", async () => {
    await assertFails(createHousehold(signedOut(), "taro", HID));
  });

  it("オーナーの members なしでは家族を作れない", async () => {
    const db = as("taro");
    await assertFails(setDoc(doc(db, `households/${HID}`), { name: "x", createdBy: "taro", createdAt: serverTimestamp() }));
  });

  it("すでにある家族のオーナーにはなれない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(createHousehold(as("jiro"), "jiro", HID));
  });

  it("家族名は40文字まで", async () => {
    const db = as("taro");
    const batch = writeBatch(db)
      .set(doc(db, `households/${HID}`), { name: "あ".repeat(41), createdBy: "taro", createdAt: serverTimestamp() })
      .set(doc(db, `households/${HID}/members/taro`), member("owner", null))
      .set(doc(db, `users/taro`), user(HID));
    await assertFails(batch.commit());
  });
});

describe("users", () => {
  it("自分の users だけを読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(getDoc(doc(as("taro"), "users/taro")));
    await assertFails(getDoc(doc(as("taro"), "users/hanako")));
  });

  it("メンバーでない家族を所属にできない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("jiro"), "users/jiro"), user(HID)));
  });

  it("所属を消せる（離脱・外されたあと）", async () => {
    await assertSucceeds(setDoc(doc(as("jiro"), "users/jiro"), user(null)));
  });
});

describe("招待", () => {
  function invite(hid: string, createdBy: string, expiresInHours = 23.8) {
    return {
      householdId: hid,
      createdBy,
      inviterName: "山田 太郎",
      createdAt: serverTimestamp(),
      expiresAt: Timestamp.fromMillis(Date.now() + expiresInHours * 60 * 60 * 1000),
      maxUses: 1,
      usedCount: 0,
    };
  }

  it("メンバーは招待を発行できる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(setDoc(doc(as("hanako"), `invites/${CODE}`), invite(HID, "hanako")));
  });

  it("メンバーでない家族の招待は発行できない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("jiro"), `invites/${CODE}`), invite(HID, "jiro")));
  });

  it("有効期限は24時間まで", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(setDoc(doc(as("taro"), `invites/${CODE}`), invite(HID, "taro", 25)));
  });

  it("発行した人の名前がない招待は発行できない", async () => {
    await seedHousehold(HID, "taro");
    const { inviterName: _, ...withoutName } = invite(HID, "taro");
    await assertFails(setDoc(doc(as("taro"), `invites/${CODE}`), withoutName));
  });

  it("ログインしていればコードで招待を読める（参加前の確認のため）", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    await assertSucceeds(getDoc(doc(as("jiro"), `invites/${CODE}`)));
    await assertFails(getDoc(doc(signedOut(), `invites/${CODE}`)));
  });

  it("招待の一覧はその家族のメンバーだけが読める", async () => {
    await seedHousehold(HID, "taro");
    await seedHousehold(OTHER_HID, "jiro");
    await seedInvite(CODE, HID);
    const invitesOf = (db: Firestore) => getDocs(query(collection(db, "invites"), where("householdId", "==", HID)));
    await assertSucceeds(invitesOf(as("taro")));
    await assertFails(invitesOf(as("jiro")));
  });

  it("メンバーは招待を取り消せる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await seedInvite(CODE, HID);
    await assertSucceeds(deleteDoc(doc(as("hanako"), `invites/${CODE}`)));
  });

  it("メンバーでなければ招待を取り消せない", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    await assertFails(deleteDoc(doc(as("jiro"), `invites/${CODE}`)));
  });
});

describe("参加", () => {
  it("有効な招待で参加できる", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    await assertSucceeds(join(as("hanako"), "hanako", HID, CODE));
  });

  it("期限切れの招待では参加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID, { expiresAt: Timestamp.fromMillis(Date.now() - 1000) });
    await assertFails(join(as("hanako"), "hanako", HID, CODE));
  });

  it("使用済みの招待では参加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID, { usedCount: 1 });
    await assertFails(join(as("hanako"), "hanako", HID, CODE));
  });

  it("同じ招待で2人目は参加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    await assertSucceeds(join(as("hanako"), "hanako", HID, CODE));
    await assertFails(join(as("kenta"), "kenta", HID, CODE));
  });

  it("招待を使わずに members を作れない", async () => {
    await seedHousehold(HID, "taro");
    const db = as("hanako");
    await assertFails(setDoc(doc(db, `households/${HID}/members/hanako`), member("member", null)));
  });

  it("別の家族の招待で参加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedHousehold(OTHER_HID, "jiro");
    await seedInvite(CODE, OTHER_HID);
    await assertFails(join(as("hanako"), "hanako", HID, CODE));
  });

  it("招待でオーナーとして参加できない", async () => {
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    const db = as("hanako");
    const batch = writeBatch(db)
      .set(doc(db, `households/${HID}/members/hanako`), member("owner", CODE))
      .update(doc(db, `invites/${CODE}`), { usedCount: increment(1) })
      .set(doc(db, `users/hanako`), user(HID));
    await assertFails(batch.commit());
  });

  it("自分しかいない家族から、ほかの家族の招待で参加できる（合流の最初の手順）", async () => {
    await seedHousehold(OTHER_HID, "hanako");
    await seedHousehold(HID, "taro");
    await seedInvite(CODE, HID);
    await assertSucceeds(join(as("hanako"), "hanako", HID, CODE));
  });
});

describe("メンバー", () => {
  it("メンバーだけがメンバーの一覧を読める", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(getDocs(collection(as("hanako"), `households/${HID}/members`)));
    await assertFails(getDocs(collection(as("jiro"), `households/${HID}/members`)));
  });

  it("オーナーはほかのメンバーを外せる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertSucceeds(deleteDoc(doc(as("taro"), `households/${HID}/members/hanako`)));
  });

  it("オーナー以外はほかのメンバーを外せない", async () => {
    await seedHousehold(HID, "taro", "hanako", "kenta");
    await assertFails(deleteDoc(doc(as("hanako"), `households/${HID}/members/kenta`)));
  });

  it("外されたメンバーは、メンバーの一覧も自分の members も読めない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await deleteDoc(doc(as("taro"), `households/${HID}/members/hanako`));
    await assertFails(getDoc(doc(as("hanako"), `households/${HID}/members/hanako`)));
    await assertFails(getDoc(doc(as("hanako"), `households/${HID}`)));
  });

  it("メンバーは自分で抜けられる", async () => {
    await seedHousehold(HID, "taro", "hanako");
    const db = as("hanako");
    const batch = writeBatch(db)
      .delete(doc(db, `households/${HID}/members/hanako`))
      .set(doc(db, "users/hanako"), user(null));
    await assertSucceeds(batch.commit());
  });

  it("オーナーは、抜けるのと同じバッチでほかのメンバーをオーナーにできる", async () => {
    await seedHousehold(HID, "taro", "hanako", "kenta");
    const db = as("taro");
    const batch = writeBatch(db)
      .update(doc(db, `households/${HID}/members/hanako`), { role: "owner" })
      .delete(doc(db, `households/${HID}/members/taro`))
      .set(doc(db, "users/taro"), user(null));
    await assertSucceeds(batch.commit());
  });

  it("オーナーは、抜けずにほかのメンバーをオーナーにできない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertFails(updateDoc(doc(as("taro"), `households/${HID}/members/hanako`), { role: "owner" }));
  });

  it("メンバーは自分をオーナーにできない", async () => {
    await seedHousehold(HID, "taro", "hanako");
    await assertFails(updateDoc(doc(as("hanako"), `households/${HID}/members/hanako`), { role: "owner" }));
  });

  it("最後のメンバーが抜けても家族は残る", async () => {
    await seedHousehold(HID, "taro");
    const db = as("taro");
    await assertSucceeds(
      writeBatch(db).delete(doc(db, `households/${HID}/members/taro`)).set(doc(db, "users/taro"), user(null)).commit(),
    );
    await assertFails(deleteDoc(doc(db, `households/${HID}`)));
  });
});

describe("家族のドキュメント", () => {
  it("メンバーだけが読める", async () => {
    await seedHousehold(HID, "taro");
    await assertSucceeds(getDoc(doc(as("taro"), `households/${HID}`)));
    await assertFails(getDoc(doc(as("jiro"), `households/${HID}`)));
  });

  it("名前を変えられない", async () => {
    await seedHousehold(HID, "taro");
    await assertFails(updateDoc(doc(as("taro"), `households/${HID}`), { name: "新しい名前" }));
  });
});
