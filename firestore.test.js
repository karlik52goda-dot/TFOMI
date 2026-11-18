const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_alice";
const GUEST_UID = "guest_bob";
const OUTSIDER_UID = "outsider_charlie";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayload(roomCode, hostUid, hostName, ts) {
  return {
    roomCode,
    hostUid,
    hostName,
    guestUid: "",
    guestName: "",
    mode: "coop",
    status: "waiting",
    floor: 1,
    seed: 123456,
    winnerUid: "",
    memberUids: [hostUid],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPlayerPayload(uid, roomId, memberUids, displayName, skinKey, ts) {
  return {
    uid,
    roomId,
    memberUids,
    displayName,
    skinKey,
    roomGx: 0,
    roomGy: 0,
    x: 120.0,
    y: 420.0,
    vx: 0.0,
    vy: 0.0,
    hp: 6,
    maxHp: 6,
    shield: 2,
    score: 0,
    facingRight: true,
    alive: true,
    shotSeq: 0,
    shotX: 0.0,
    shotY: 0.0,
    shotVx: 0.0,
    shotVy: 0.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user cannot read or write rooms or players", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("rooms").where("status", "==", "waiting").get());
  await assertFails(unauthDb.collection("rooms").doc("TF1234").get());
  await assertFails(
    unauthDb.collection("rooms").doc("TF1234").set(validRoomPayload("TF1234", HOST_UID, "Alice", ts))
  );
});

test("Host creates waiting room and player doc atomically; ghost fields and @DocumentId id rejected", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  const batch = hostDb.batch();
  const roomRef = hostDb.collection("rooms").doc("TF1001");
  const playerRef = roomRef.collection("players").doc(HOST_UID);
  batch.set(roomRef, validRoomPayload("TF1001", HOST_UID, "Alice", ts));
  batch.set(playerRef, validPlayerPayload(HOST_UID, "TF1001", [HOST_UID], "Alice", "admin", ts));
  await assertSucceeds(batch.commit());

  // Reject ghost field / @DocumentId id field
  await assertFails(
    hostDb.collection("rooms").doc("TF1002").set({
      ...validRoomPayload("TF1002", HOST_UID, "Alice", ts),
      id: "TF1002",
    })
  );
  await assertFails(
    hostDb.collection("rooms").doc("TF1003").set({
      ...validRoomPayload("TF1003", HOST_UID, "Alice", ts),
      isAdmin: true,
    })
  );
  // Reject future timestamp (> request.time)
  await assertFails(
    hostDb.collection("rooms").doc("TF1004").set(
      validRoomPayload("TF1004", HOST_UID, "Alice", new Date(Date.now() + 60000))
    )
  );
});

test("Guest joins waiting room atomically, queries work, and cross-player tampering is blocked", async () => {
  const ts = new Date(Date.now() - 5000);
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const guestDb = testEnv.authenticatedContext(GUEST_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Host creates room
  const hostBatch = hostDb.batch();
  const hostRoomRef = hostDb.collection("rooms").doc("TF2002");
  hostBatch.set(hostRoomRef, validRoomPayload("TF2002", HOST_UID, "Alice", ts));
  hostBatch.set(
    hostRoomRef.collection("players").doc(HOST_UID),
    validPlayerPayload(HOST_UID, "TF2002", [HOST_UID], "Alice", "oliver_200", ts)
  );
  await assertSucceeds(hostBatch.commit());

  // Guest lists waiting rooms (query aligned with rules)
  await assertSucceeds(
    guestDb.collection("rooms").where("status", "==", "waiting").get()
  );
  // Unfiltered rooms query must fail!
  await assertFails(guestDb.collection("rooms").get());

  // Guest joins atomically
  const joinTs = new Date(Date.now() - 1000);
  const joinBatch = guestDb.batch();
  const guestRoomRef = guestDb.collection("rooms").doc("TF2002");
  joinBatch.update(guestRoomRef, {
    guestUid: GUEST_UID,
    guestName: "Bob",
    memberUids: [HOST_UID, GUEST_UID],
    updatedAt: joinTs,
  });
  joinBatch.set(
    guestRoomRef.collection("players").doc(GUEST_UID),
    validPlayerPayload(GUEST_UID, "TF2002", [HOST_UID, GUEST_UID], "Bob", "chekushka", joinTs)
  );
  joinBatch.update(guestRoomRef.collection("players").doc(HOST_UID), {
    memberUids: [HOST_UID, GUEST_UID],
    updatedAt: joinTs,
  });
  await assertSucceeds(joinBatch.commit());

  // Both host and guest can list players using memberUids filter
  await assertSucceeds(
    guestDb.collection("rooms").doc("TF2002").collection("players").where("memberUids", "array-contains", GUEST_UID).get()
  );
  await assertSucceeds(
    hostDb.collection("rooms").doc("TF2002").collection("players").where("memberUids", "array-contains", HOST_UID).get()
  );

  // Guest cannot mutate host's hp/score
  await assertFails(
    guestDb.collection("rooms").doc("TF2002").collection("players").doc(HOST_UID).update({
      hp: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Host starts match
  await assertSucceeds(
    hostRoomRef.update({
      status: "playing",
      seed: 987654,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Outsider cannot read or list active 'playing' room or its players
  await assertFails(outsiderDb.collection("rooms").doc("TF2002").get());
  await assertFails(outsiderDb.collection("rooms").doc("TF2002").collection("players").doc(HOST_UID).get());

  // Player updates own live gameplay position & shot
  await assertSucceeds(
    guestDb.collection("rooms").doc("TF2002").collection("players").doc(GUEST_UID).update({
      x: 340.5,
      y: 280.0,
      vx: 180.0,
      vy: -220.0,
      hp: 5,
      shotSeq: 1,
      shotX: 356.0,
      shotY: 294.0,
      shotVx: 640.0,
      shotVy: 0.0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Finish match + Terminal State Locking check
  await assertSucceeds(
    guestDb.collection("rooms").doc("TF2002").update({
      status: "finished",
      winnerUid: HOST_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Subsequent updates to finished room must fail
  await assertFails(
    hostRoomRef.update({
      status: "playing",
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
