const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const MOM_UID = "mom_uid_789";
const DAD_UID = "dad_uid_321";
const SISTER_UID = "sister_uid_654";
const STRANGER_UID = "stranger_uid_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("pairings").doc("pair_1").get());
  await assertFails(unauthDb.collection("pairInvites").doc("ABC12345").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profiles are strictly owner-isolated and reject ghost/oversized/future fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "alice_k",
      activePairId: "pair_1",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ username: "hacked" }));

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "alice2",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("1-on-1 Direct pairing allows max 2 members and strictly blocks 3rd user & invite scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const momDb = testEnv.authenticatedContext(MOM_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("pairings").doc("pair_1").set({
      ownerUid: ALICE_UID,
      roomType: "direct",
      maxMembers: 2,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "alice" },
      spaceName: "Partner",
      inviteCode: "HN7K4M2P",
      ownerUsername: "alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("pairInvites").doc("HN7K4M2P").set({
      pairId: "pair_1",
      creatorUid: ALICE_UID,
      creatorUsername: "alice",
      spaceName: "Partner",
      roomType: "direct",
      createdAt: now,
    })
  );

  await assertFails(momDb.collection("pairInvites").get());

  await assertSucceeds(bobDb.collection("pairInvites").doc("HN7K4M2P").get());
  await assertSucceeds(
    bobDb.collection("pairings").doc("pair_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      [`memberNames.${BOB_UID}`]: "bob",
      partnerUsername: "bob",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 3rd user strictly rejected on 1-on-1 room
  await assertFails(
    momDb.collection("pairings").doc("pair_1").update({
      memberUids: [ALICE_UID, BOB_UID, MOM_UID],
      [`memberNames.${MOM_UID}`]: "mom",
      partnerUsername: "mom",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(momDb.collection("pairings").doc("pair_1").get());
});

test("Google email pairing enforces email_verified == true for both direct and group invitedEmails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const dadUnverifiedDb = testEnv.authenticatedContext(DAD_UID, {
    email: "dad@example.com",
    email_verified: false,
  }).firestore();
  const dadVerifiedDb = testEnv.authenticatedContext(DAD_UID, {
    email: "dad@example.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("pairings").doc("family_email").set({
      ownerUid: ALICE_UID,
      roomType: "group",
      maxMembers: 12,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "alice" },
      spaceName: "Family",
      inviteCode: "FAM8K4PQ",
      invitedEmails: ["mom@example.com", "dad@example.com"],
      ownerUsername: "alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    dadUnverifiedDb.collection("pairings").where("invitedEmails", "array-contains", "dad@example.com").get()
  );
  await assertFails(
    dadUnverifiedDb.collection("pairings").doc("family_email").update({
      memberUids: [ALICE_UID, DAD_UID],
      [`memberNames.${DAD_UID}`]: "dad",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    dadVerifiedDb.collection("pairings").where("invitedEmails", "array-contains", "dad@example.com").get()
  );
  await assertSucceeds(
    dadVerifiedDb.collection("pairings").doc("family_email").update({
      memberUids: [ALICE_UID, DAD_UID],
      [`memberNames.${DAD_UID}`]: "dad",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Private Family group room allows multiple members to join, broadcast statuses, and leave cleanly while blocking outsiders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const momDb = testEnv.authenticatedContext(MOM_UID).firestore();
  const dadDb = testEnv.authenticatedContext(DAD_UID).firestore();
  const sisterDb = testEnv.authenticatedContext(SISTER_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date(Date.now() - 3000);

  // Alice creates a private Family group room
  await assertSucceeds(
    aliceDb.collection("pairings").doc("room_family").set({
      ownerUid: ALICE_UID,
      roomType: "group",
      maxMembers: 12,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "alice" },
      spaceName: "Family",
      inviteCode: "FAM12345",
      ownerUsername: "alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("pairInvites").doc("FAM12345").set({
      pairId: "room_family",
      creatorUid: ALICE_UID,
      creatorUsername: "alice",
      spaceName: "Family",
      roomType: "group",
      createdAt: now,
    })
  );

  // Mom, Dad, and Sister all join using the private code
  await assertSucceeds(
    momDb.collection("pairings").doc("room_family").update({
      memberUids: [ALICE_UID, MOM_UID],
      [`memberNames.${MOM_UID}`]: "mom",
      updatedAt: new Date(Date.now() - 2500),
    })
  );
  await assertSucceeds(
    dadDb.collection("pairings").doc("room_family").update({
      memberUids: [ALICE_UID, MOM_UID, DAD_UID],
      [`memberNames.${DAD_UID}`]: "dad",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertSucceeds(
    sisterDb.collection("pairings").doc("room_family").update({
      memberUids: [ALICE_UID, MOM_UID, DAD_UID, SISTER_UID],
      [`memberNames.${SISTER_UID}`]: "sister",
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Owner can invite additional emails and rename room; member can update their own username
  await assertSucceeds(
    aliceDb.collection("pairings").doc("room_family").update({
      invitedEmails: ["grandma@example.com"],
      updatedAt: new Date(Date.now() - 1200),
    })
  );
  await assertSucceeds(
    momDb.collection("pairings").doc("room_family").update({
      [`memberNames.${MOM_UID}`]: "mama",
      updatedAt: new Date(Date.now() - 1100),
    })
  );

  // Non-owner cannot delete the group invite code or delete the whole group room or hijack invitedEmails
  await assertFails(momDb.collection("pairInvites").doc("FAM12345").delete());
  await assertFails(momDb.collection("pairings").doc("room_family").delete());
  await assertFails(
    momDb.collection("pairings").doc("room_family").update({
      invitedEmails: ["evil@example.com"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice broadcasts a single status ping to the entire Family room
  const allFamily = [ALICE_UID, MOM_UID, DAD_UID, SISTER_UID];
  await assertSucceeds(
    aliceDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).set({
      userId: ALICE_UID,
      pairId: "room_family",
      memberUids: allFamily,
      senderName: "alice",
      activeButtonId: "btn_home",
      buttonLabel: "Home",
      message: "Just got home safely!",
      iconKey: "home",
      pingCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Mom, Dad, and Sister can ALL read Alice's live status
  await assertSucceeds(
    momDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).get()
  );
  await assertSucceeds(
    dadDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).get()
  );
  await assertSucceeds(
    sisterDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).get()
  );

  // Outsider (Stranger) CANNOT read the Family room or any status inside it
  await assertFails(strangerDb.collection("pairings").doc("room_family").get());
  await assertFails(
    strangerDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).get()
  );

  // Sister cleanly leaves the Family room and immediately loses access
  await assertSucceeds(
    sisterDb.collection("pairings").doc("room_family").update({
      memberUids: [ALICE_UID, MOM_UID, DAD_UID],
      memberNames: {
        [ALICE_UID]: "alice",
        [MOM_UID]: "mama",
        [DAD_UID]: "dad",
      },
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(sisterDb.collection("pairings").doc("room_family").get());
  await assertFails(
    sisterDb.collection("pairings").doc("room_family").collection("statuses").doc(ALICE_UID).get()
  );
});

test("Strict cross-room isolation: Family room members cannot see Alice's private Partner room, and Partner cannot see Family room", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const momDb = testEnv.authenticatedContext(MOM_UID).firestore();
  const dadDb = testEnv.authenticatedContext(DAD_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    // Room 1: Alice + Bob (Private 1-on-1 Partner Room)
    await ctx.firestore().collection("pairings").doc("space_partner").set({
      ownerUid: ALICE_UID,
      roomType: "direct",
      maxMembers: 2,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "alice", [BOB_UID]: "bob" },
      spaceName: "Bob",
      inviteCode: "PARTNER1",
      ownerUsername: "alice",
      partnerUsername: "bob",
      createdAt: now,
      updatedAt: now,
    });
    // Room 2: Alice + Mom + Dad (Private Family Group Room)
    await ctx.firestore().collection("pairings").doc("space_family").set({
      ownerUid: ALICE_UID,
      roomType: "group",
      maxMembers: 12,
      memberUids: [ALICE_UID, MOM_UID, DAD_UID],
      memberNames: { [ALICE_UID]: "alice", [MOM_UID]: "mom", [DAD_UID]: "dad" },
      spaceName: "Family",
      inviteCode: "FAMILY02",
      ownerUsername: "alice",
      createdAt: now,
      updatedAt: now,
    });
  });

  // Alice adds private button & status in Partner room
  await assertSucceeds(
    aliceDb.collection("pairings").doc("space_partner").collection("buttons").doc("btn_gym").set({
      ownerUid: ALICE_UID,
      pairId: "space_partner",
      memberUids: [ALICE_UID, BOB_UID],
      label: "Gym",
      message: "Just got to the gym!",
      iconKey: "gym",
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("pairings").doc("space_partner").collection("statuses").doc(ALICE_UID).set({
      userId: ALICE_UID,
      pairId: "space_partner",
      memberUids: [ALICE_UID, BOB_UID],
      senderName: "alice",
      activeButtonId: "btn_gym",
      buttonLabel: "Gym",
      message: "Just got to the gym!",
      iconKey: "gym",
      pingCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice adds separate status in Family room
  await assertSucceeds(
    aliceDb.collection("pairings").doc("space_family").collection("statuses").doc(ALICE_UID).set({
      userId: ALICE_UID,
      pairId: "space_family",
      memberUids: [ALICE_UID, MOM_UID, DAD_UID],
      senderName: "alice",
      activeButtonId: "btn_college",
      buttonLabel: "College",
      message: "Reached college safely!",
      iconKey: "college",
      pingCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob reads Partner room cleanly, CANNOT read Family room
  await assertSucceeds(
    bobDb.collection("pairings").doc("space_partner").collection("buttons")
      .where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("pairings").doc("space_partner").collection("statuses").doc(ALICE_UID).get()
  );
  await assertFails(bobDb.collection("pairings").doc("space_family").get());
  await assertFails(
    bobDb.collection("pairings").doc("space_family").collection("statuses").doc(ALICE_UID).get()
  );

  // Neither Mom nor Dad can access Alice's private Partner room with Bob
  for (const familyDb of [momDb, dadDb]) {
    await assertSucceeds(
      familyDb.collection("pairings").doc("space_family").collection("statuses").doc(ALICE_UID).get()
    );
    await assertFails(familyDb.collection("pairings").doc("space_partner").get());
    await assertFails(
      familyDb.collection("pairings").doc("space_partner").collection("statuses").doc(ALICE_UID).get()
    );
    await assertFails(
      familyDb.collection("pairings").doc("space_partner").collection("buttons").get()
    );
    await assertFails(
      familyDb.collection("pairings").doc("space_partner").collection("buttons")
        .where("memberUids", "array-contains", BOB_UID).get()
    );
  }
  const momEmptySnap = await assertSucceeds(
    momDb.collection("pairings").doc("space_partner").collection("buttons")
      .where("memberUids", "array-contains", MOM_UID).get()
  );
  if (!momEmptySnap.empty) {
    throw new Error("Expected Mom to see 0 documents in Alice+Bob space");
  }
});
