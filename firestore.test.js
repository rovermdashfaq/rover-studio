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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/bulletin_presets`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/layer_assets`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/nameplate_presets`).doc("np_1").set({
      userId: ALICE_UID,
      nameText: "Test",
      unitText: "Unit",
      accentColorHex: "#FF2B4A",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can CRUD layer_assets; rejects mismatched slot, extra id, and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/layer_assets`).doc("layer1").set({
      userId: ALICE_UID,
      slot: "layer1",
      label: "Layer 1 Overlay",
      sourceType: "url",
      content: "https://example.com/layer1.png",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/layer_assets`).doc("layer1").update({
      label: "Updated Layer 1",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List works even on owned subcollection
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/layer_assets`).get());

  // Cross-user denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/layer_assets`).doc("layer1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/layer_assets`).get());

  // Mismatched slotId vs slot rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/layer_assets`).doc("layer2").set({
      userId: ALICE_UID,
      slot: "layer1",
      label: "Mismatch",
      sourceType: "url",
      content: "https://example.com/layer1.png",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/layer_assets`).doc("layer3").set({
      id: "layer3",
      userId: ALICE_UID,
      slot: "layer3",
      label: "Layer 3",
      sourceType: "url",
      content: "https://example.com/layer3.png",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can CRUD bulletin_presets with 2K QHD and 60/120fps; rejects invalid bounds or immutable createdAt edit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bulletin_presets`).doc("preset_1").set({
      userId: ALICE_UID,
      name: "Thursday Bulletin",
      title1: "বাংলাদেশ স্কাউটস রোভার অঞ্চলের নতুন আপডেট",
      title2: "বিস্তারিত জানতে চোখ রাখুন আমাদের পরবর্তী বুলেটিনে",
      startTime: "07:00:00",
      ampm: "PM",
      durationSeconds: 30,
      scrollSpeed: 2.5,
      logoWidth: 200,
      logoHeight: 200,
      targetFps: 60,
      resolution: "2K_QHD",
      chromaHex: "#0047BB",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bulletin_presets`).doc("preset_1").update({
      targetFps: 120,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/bulletin_presets`).doc("preset_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/bulletin_presets`).doc("preset_future").set({
      userId: ALICE_UID,
      name: "Future",
      title1: "Title 1",
      title2: "Title 2",
      startTime: "07:00:00",
      ampm: "PM",
      durationSeconds: 30,
      scrollSpeed: 2.5,
      logoWidth: 200,
      logoHeight: 200,
      targetFps: 60,
      resolution: "2K_QHD",
      chromaHex: "#0047BB",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Owner can CRUD nameplate_presets, credit_projects, and quick_links; cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/nameplate_presets`).doc("np_1").set({
      userId: ALICE_UID,
      nameText: "আশফাকুর রহমান তানিম",
      unitText: "পিআর ও আইসিটি টাস্কফোর্স",
      accentColorHex: "#FF2B4A",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/credit_projects`).doc("cr_1").set({
      userId: ALICE_UID,
      projectTitle: "Episode 12 Credits",
      sections: [
        {
          title: "পরিকল্পনায়",
          namesText: "রাকিবুল হাসান রাফি, আশিকুর রহমান",
          titleColorHex: "#DC2626",
          nameColorHex: "#111827",
          titleSize: 48,
          nameSize: 40,
        },
      ],
      createdAt: past,
      updatedAt: past,
    })
  );

  // QuickLink with optional thumbnailUrl omitted
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/quick_links`).doc("lk_1").set({
      userId: ALICE_UID,
      title: "Rover Portfolio",
      url: "https://rovermdashfaq.github.io/portfolio/",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user cannot read or write
  await assertFails(bobDb.collection(`users/${ALICE_UID}/nameplate_presets`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/credit_projects`).doc("cr_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/quick_links`).doc("lk_1").delete());
});
