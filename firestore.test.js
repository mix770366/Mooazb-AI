const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
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

test("Unauthenticated context is denied on all user-scoped collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("workflows").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("ideWorkspaces").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("ideTasks").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      userId: ALICE_UID,
      title: "Hack",
      mode: "WORKSPACE",
      reasoningLevel: "HIGH",
      estimatedContextTokens: 0,
      contextSummary: "",
      languageCode: "ar",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Sessions & Messages: valid Kotlin-mirrored create, update, parent gate, and cross-user denial", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  const sessionRef = alice.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      title: "تحليل بنية النظام",
      mode: "WORKSPACE",
      reasoningLevel: "HIGH",
      estimatedContextTokens: 1200,
      contextSummary: "app.kt attached",
      languageCode: "ar",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    sessionRef.update({
      reasoningLevel: "XHIGH",
      estimatedContextTokens: 4800,
      updatedAt: pastDate(500),
    })
  );

  // Reject shadow field or @DocumentId 'id' leak
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_bad").set({
      id: "sess_bad",
      userId: ALICE_UID,
      title: "Bad",
      mode: "WORKSPACE",
      reasoningLevel: "HIGH",
      estimatedContextTokens: 0,
      contextSummary: "",
      languageCode: "ar",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Message requires parent session existence
  await assertSucceeds(
    sessionRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "USER",
      content: "اشرح هذا الكود",
      reasoningLevel: "HIGH",
      attachmentNames: ["MainActivity.kt"],
      latencyMs: 0,
      tokenEstimate: 120,
      createdAt: pastDate(500),
    })
  );

  // Orphaned message without parent session fails
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("sessions").doc("missing_sess").collection("messages").doc("msg_2").set({
      userId: ALICE_UID,
      role: "USER",
      content: "Orphan",
      reasoningLevel: "LOW",
      attachmentNames: [],
      latencyMs: 0,
      tokenEstimate: 10,
      createdAt: pastDate(500),
    })
  );

  // Bob cannot read or write Alice's sessions
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").get());
});

test("AgentWorkflows: native steps list validation and security status bounds", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_1").set({
      userId: ALICE_UID,
      goal: "تنظيم مهام الفريق وأتمتة المراجعة",
      presetType: "AUTOMATION",
      reasoningLevel: "HIGH",
      securityStatus: "SAFE",
      injectionRiskScore: 4,
      securityNotes: "No indirect prompt injection patterns detected.",
      steps: [
        {
          stepIndex: 1,
          title: "Parse inputs",
          agentRole: "TriageAgent",
          permissionScope: "READ_ONLY",
          status: "COMPLETED",
          output: "Parsed 3 tasks",
        },
      ],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Invalid securityStatus rejected
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("workflows").doc("wf_bad").set({
      userId: ALICE_UID,
      goal: "Invalid",
      presetType: "CUSTOM",
      reasoningLevel: "LOW",
      securityStatus: "BYPASSED",
      injectionRiskScore: 0,
      securityNotes: "",
      steps: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("IDE Workspaces & Tasks: pairing, parent existence, diff approval, and terminal lock", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  const wsRef = alice.collection("users").doc(ALICE_UID).collection("ideWorkspaces").doc("ws_vscode_1");
  await assertSucceeds(
    wsRef.set({
      userId: ALICE_UID,
      ideType: "VS_CODE",
      projectName: "coremind-api",
      branchName: "main",
      pairingCode: "CM-8842",
      bridgeStatus: "CONNECTED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const taskRef = alice.collection("users").doc(ALICE_UID).collection("ideTasks").doc("task_1");
  await assertSucceeds(
    taskRef.set({
      userId: ALICE_UID,
      workspaceId: "ws_vscode_1",
      ideType: "VS_CODE",
      taskType: "PATCH_REVIEW",
      title: "Fix NullPointerException in AuthParser",
      filePath: "src/auth/AuthParser.kt",
      language: "kotlin",
      codeSnippet: "fun parse(x: String?) = x!!.trim()",
      stackTrace: "NullPointerException at AuthParser.kt:12",
      diagnosis: "Unsafe non-null assertion on nullable input.",
      unifiedDiff: "--- a/AuthParser.kt\n+++ b/AuthParser.kt\n-fun parse(x: String?) = x!!.trim()\n+fun parse(x: String?) = x?.trim().orEmpty()",
      riskLevel: "LOW",
      reasoningLevel: "HIGH",
      securityStatus: "SAFE",
      status: "PATCH_READY",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Approve patch (PATCH_READY -> APPROVED)
  await assertSucceeds(
    taskRef.update({
      status: "APPROVED",
      updatedAt: pastDate(500),
    })
  );

  // Mark applied (APPROVED -> APPLIED)
  await assertSucceeds(
    taskRef.update({
      status: "APPLIED",
      updatedAt: pastDate(200),
    })
  );

  // Terminal lock: APPLIED cannot transition back
  await assertFails(
    taskRef.update({
      status: "REJECTED",
      updatedAt: pastDate(100),
    })
  );

  // Orphaned IDE task without existing workspace fails
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("ideTasks").doc("task_orphan").set({
      userId: ALICE_UID,
      workspaceId: "ws_nonexistent",
      ideType: "PYCHARM",
      taskType: "DIAGNOSTIC",
      title: "Orphan",
      filePath: "main.py",
      language: "python",
      codeSnippet: "",
      stackTrace: "",
      diagnosis: "",
      unifiedDiff: "",
      riskLevel: "LOW",
      reasoningLevel: "LOW",
      securityStatus: "SAFE",
      status: "PENDING_ANALYSIS",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user access denied
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("ideTasks").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("ideWorkspaces").get());
});

test("UserProfile: owner can persist and update preferredReasoningLevel; invalid tiers and cross-user access denied", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  const aliceProfile = alice.collection("users").doc(ALICE_UID);

  // Unauthenticated read/write denied
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      preferredReasoningLevel: "HIGH",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner creates profile with XHIGH tier
  await assertSucceeds(
    aliceProfile.set({
      userId: ALICE_UID,
      preferredReasoningLevel: "XHIGH",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner reads own profile
  await assertSucceeds(aliceProfile.get());

  // Owner updates preferredReasoningLevel to LOW
  await assertSucceeds(
    aliceProfile.update({
      preferredReasoningLevel: "LOW",
      updatedAt: pastDate(500),
    })
  );

  // Invalid reasoning level is rejected
  await assertFails(
    aliceProfile.update({
      preferredReasoningLevel: "TURBO",
      updatedAt: pastDate(200),
    })
  );

  // Cannot mutate userId
  await assertFails(
    aliceProfile.update({
      userId: BOB_UID,
      preferredReasoningLevel: "HIGH",
      updatedAt: pastDate(200),
    })
  );

  // Bob cannot read, list, or write Alice's profile
  await assertFails(bob.collection("users").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({
      preferredReasoningLevel: "MEDIUM",
      updatedAt: pastDate(100),
    })
  );
});

