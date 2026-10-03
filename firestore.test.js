const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read user documents or items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("items").get());
});

test("Authenticated user: cannot read another user's profile or items", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      displayName: "Bob",
      createdAt: new Date(),
      lastActive: new Date(),
    });
    await context.firestore().collection("users").doc(BOB_UID).collection("items").doc("item1").set({
      id: "item1",
      userId: BOB_UID,
      title: "Bob's Test Item",
      status: "PENDING",
      priority: "HIGH",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("items").doc("item1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("items").get());
});

test("Authenticated user: can read and create their own items with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("items").doc("item_alice_1").set({
      id: "item_alice_1",
      userId: ALICE_UID,
      title: "Alice's Unit Test",
      description: "Testing local verification",
      status: "PENDING",
      priority: "MEDIUM",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("items").doc("item_alice_1").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("items").get()
  );
});

test("Authenticated user: fails creating item with invalid status or mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Invalid status
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("items").doc("item_invalid").set({
      id: "item_invalid",
      userId: ALICE_UID,
      title: "Alice's Unit Test",
      status: "UNKNOWN_STATUS",
      priority: "MEDIUM",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Mismatched userId in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("items").doc("item_mismatch").set({
      id: "item_mismatch",
      userId: BOB_UID,
      title: "Mismatch Test",
      status: "PENDING",
      priority: "LOW",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
