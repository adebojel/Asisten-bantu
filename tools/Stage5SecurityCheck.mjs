#!/usr/bin/env node
/**
 * Stage 5 offline/privacy guard.
 * Scans executable Android source/configuration, not README or historical notes.
 * This is a regression tripwire, not a substitute for a manual network/data-flow audit.
 */
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const scanRoots = [
  "app/src/main/java",
  "app/src/main/kotlin",
  "app/src/main/res",
  "app/src/main/assets",
  "src",
  "app/build.gradle.kts",
  "build.gradle",
];
const extensions = new Set([".java", ".kt", ".xml", ".json", ".properties", ".kts", ".gradle", ".js", ".mjs", ".ts", ".tsx", ".jsx"]);
const forbiddenHosts = [
  /(?:^|\.)generativelanguage\.googleapis\.com$/i,
  /(?:^|\.)openrouter\.ai$/i,
  /(?:^|\.)api\.openai\.com$/i,
  /(?:^|\.)api\.anthropic\.com$/i,
  /(?:^|\.)api\.groq\.com$/i,
  /(?:^|\.)api\.mistral\.ai$/i,
];
const secretPatterns = [
  /\bAIza[0-9A-Za-z_-]{30,}\b/,
  /\bsk-[A-Za-z0-9_-]{20,}\b/,
  /\bgh[pousr]_[A-Za-z0-9]{30,}\b/,
  /\bxox[baprs]-[A-Za-z0-9-]{20,}\b/,
];
const failures = [];
const scanned = [];

function walk(entry) {
  const absolute = path.join(root, entry);
  if (!fs.existsSync(absolute)) return;
  const stat = fs.statSync(absolute);
  if (stat.isDirectory()) {
    for (const name of fs.readdirSync(absolute).sort()) walk(path.join(entry, name));
    return;
  }
  if (!extensions.has(path.extname(entry))) return;
  scanned.push(entry);
  const raw = fs.readFileSync(absolute, "utf8");
  // Remove comments before endpoint checks so documentation/comments do not trigger.
  const code = raw
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/(^|\s)\/\/.*$/gm, "$1")
    .replace(/<!--([\s\S]*?)-->/g, " ");
  for (const match of code.matchAll(/https?:\/\/([^\s"'<>]+)/gi)) {
    let host = "";
    try { host = new URL(match[0]).hostname; } catch { continue; }
    if (forbiddenHosts.some((pattern) => pattern.test(host))) {
      failures.push(`${entry}: prohibited AI-cloud endpoint ${host}`);
    }
  }
  for (const pattern of secretPatterns) {
    if (pattern.test(code)) failures.push(`${entry}: possible hard-coded credential pattern ${pattern}`);
  }
  if (/SpeechRecognizer\.createSpeechRecognizer\s*\(/.test(code)) {
    failures.push(`${entry}: network-capable system SpeechRecognizer is forbidden; use on-device recognition only.`);
  }
}

for (const entry of scanRoots) walk(entry);

const manifestPath = path.join(root, "app/src/main/AndroidManifest.xml");
if (fs.existsSync(manifestPath)) {
  const manifest = fs.readFileSync(manifestPath, "utf8");
  const required = [
    ['android:allowBackup="false"', "application backup must remain disabled"],
    ['android:usesCleartextTraffic="false"', "cleartext traffic must remain disabled"],
    ['android.permission.BIND_ACCESSIBILITY_SERVICE', "accessibility service must be protected by the platform bind permission"],
  ];
  for (const [needle, message] of required) {
    if (!manifest.includes(needle)) failures.push(`app/src/main/AndroidManifest.xml: ${message}`);
  }
}

const memoryStore = fs.readFileSync(path.join(root, "app/src/main/java/com/asisten/bantu/LocalMemoryStore.kt"), "utf8");
const encryptedMemory = fs.readFileSync(path.join(root, "app/src/main/java/com/asisten/bantu/EncryptedLocalMemoryStore.kt"), "utf8");
const modelRuntime = fs.readFileSync(path.join(root, "app/src/main/java/com/asisten/bantu/LocalModelRuntimeManager.kt"), "utf8");
if (!memoryStore.includes("EncryptedLocalMemoryStore.read") || !memoryStore.includes("EncryptedLocalMemoryStore.write")) {
  failures.push("Local AI memory must use the encrypted storage adapter.");
}
if (!encryptedMemory.includes("AES/GCM/NoPadding") || !encryptedMemory.includes("AndroidKeyStore")
    || !encryptedMemory.includes("setRandomizedEncryptionRequired(true)")) {
  failures.push("Local memory must use randomized AES-GCM with an Android Keystore key.");
}
if (!modelRuntime.includes("IDLE_UNLOAD_MS = 120_000L") || !modelRuntime.includes("closeExecutor.execute")) {
  failures.push("LiteRT runtime must unload after idle and close away from the UI thread.");
}


const safetyContextPath = path.join(root, "src/context/KobraContext.tsx");
const visionUiPath = path.join(root, "src/screens/VisionScreen.tsx");
const sensorUiPath = path.join(root, "src/services/deviceSensors.ts");
if (fs.existsSync(safetyContextPath)) {
  const safetyContext = fs.readFileSync(safetyContextPath, "utf8");
  if (!safetyContext.includes("const [sosContacts, setSosContacts] = useState<SosContact[]>([]);")) {
    failures.push("SOS contacts must default to an empty in-memory list until the user configures real recipients.");
  }
  if (!/if \(!contact\)/.test(safetyContext)) {
    failures.push("SOS must stop and request a real configured contact when none exists.");
  }
}
if (fs.existsSync(visionUiPath)) {
  const visionUi = fs.readFileSync(visionUiPath, "utf8");
  if (/Math\.random\s*\(/.test(visionUi) || /Di depan Anda terlihat ruangan/.test(visionUi)) {
    failures.push("Vision UI must not fabricate person counts or scene descriptions.");
  }
}
if (fs.existsSync(sensorUiPath)) {
  const sensorUi = fs.readFileSync(sensorUiPath, "utf8");
  if (/return 85\s*;/.test(sensorUi)) {
    failures.push("Battery status must not use a fabricated fallback percentage.");
  }
}

const frontendContextPath = path.join(root, "src/context/KobraContext.tsx");
const frontendSpeakerPath = path.join(root, "src/services/speaker.ts");
const retiredAiClientPath = path.join(root, "src/services/aiClient.ts");
if (fs.existsSync(retiredAiClientPath)) {
  failures.push("Retired frontend AI client must be removed; browser UI must not retain cloud AI/webhook transports.");
}
if (fs.existsSync(frontendContextPath)) {
  const frontendContext = fs.readFileSync(frontendContextPath, "utf8");
  if (/webkitSpeechRecognition|\\bSpeechRecognition\\b/.test(frontendContext)) {
    failures.push("Browser Web Speech API is not guaranteed to be local; frontend speech input must use a verified on-device bridge.");
  }
}
if (fs.existsSync(frontendSpeakerPath)) {
  const frontendSpeaker = fs.readFileSync(frontendSpeakerPath, "utf8");
  if (!frontendSpeaker.includes("v.localService")) {
    failures.push("Browser TTS must select localService voices only.");
  }
  if (!frontendSpeaker.includes("isOfflineIndonesianVoiceReady()")) {
    failures.push("Browser TTS must refuse speech when no offline Indonesian voice is available.");
  }
}

const accessibilityServicePath = path.join(root, "app/src/main/java/com/asisten/bantu/AsistenAccessibilityService.java");
if (fs.existsSync(accessibilityServicePath)) {
  const accessibilityService = fs.readFileSync(accessibilityServicePath, "utf8");
  const sosMethodStart = accessibilityService.indexOf("public void triggerSos()");
  const sosMethodEnd = accessibilityService.indexOf("private void openMedicalCardAndRead()", sosMethodStart);
  const sosMethod = sosMethodStart >= 0 && sosMethodEnd > sosMethodStart
    ? accessibilityService.slice(sosMethodStart, sosMethodEnd) : "";
  if (!sosMethod.includes("config.isSosStorageReady()")) {
    failures.push("SOS must fail closed if encrypted emergency contacts cannot be read.");
  }
  if (!sosMethod.includes("SystemClock.elapsedRealtime()")
      || !sosMethod.includes("getElapsedRealtimeNanos()")
      || !sosMethod.includes("SosLocationFreshness.isFresh")
      || !sosMethod.includes("SosLocationFreshness.ageMinutes")) {
    failures.push("SOS coordinates must use monotonic timestamps and explicitly classify stale/invalid fixes.");
  }
  if (sosMethod.includes("SOS dikirim ke")
      || !sosMethod.includes("status terkirim atau diterima belum terverifikasi")
      || !sosMethod.includes("Izin SMS tidak aktif, SMS tidak dikirim.")) {
    failures.push("SOS must not claim SMS delivery from API submission and must report missing SMS permission.");
  }
}
const sosFreshnessPath = path.join(root, "app/src/main/java/com/asisten/bantu/SosLocationFreshness.java");
const sosFreshnessTestPath = path.join(root, "app/src/test/java/com/asisten/bantu/SosLocationFreshnessTest.java");
if (!fs.existsSync(sosFreshnessPath) || !fs.existsSync(sosFreshnessTestPath)) {
  failures.push("SOS location freshness policy and its unit tests must remain present.");
}
const browserSosContextPath = path.join(root, "src/context/KobraContext.tsx");
if (fs.existsSync(browserSosContextPath)) {
  const browserSosContext = fs.readFileSync(browserSosContextPath, "utf8");
  if (browserSosContext.includes("localStorage.getItem('kobra_sos')")
      || browserSosContext.includes("localStorage.setItem('kobra_sos'")) {
    failures.push("Browser UI must not persist SOS phone numbers in plaintext localStorage.");
  }
}

const encryptedSosPath = path.join(root, "app/src/main/java/com/asisten/bantu/EncryptedSosStore.kt");
const kobraConfigPath = path.join(root, "app/src/main/java/com/asisten/bantu/KobraConfig.java");
if (fs.existsSync(encryptedSosPath) && fs.existsSync(kobraConfigPath)) {
  const encryptedSos = fs.readFileSync(encryptedSosPath, "utf8");
  const kobraConfig = fs.readFileSync(kobraConfigPath, "utf8");
  if (!encryptedSos.includes("AES/GCM/NoPadding") || !encryptedSos.includes("AndroidKeyStore")) {
    failures.push("SOS contact storage must use Android Keystore AES-GCM encryption.");
  }
  if (!kobraConfig.includes("EncryptedSosStore.get(appContext, key)")
      || !kobraConfig.includes("EncryptedSosStore.setAll(appContext, values)")
      || kobraConfig.includes("sos.getString(")
      || kobraConfig.includes("sos.edit().putString(")) {
    failures.push("KobraConfig must route SOS contacts through encrypted storage, not plaintext SharedPreferences.");
  }
} else {
  failures.push("Encrypted SOS storage implementation is missing.");
}

const speakerPath = path.join(root, "app/src/main/java/com/asisten/bantu/Speaker.java");
const unifiedTtsPath = path.join(root, "app/src/main/java/com/asisten/bantu/UnifiedAccessibilityEngine.kt");
const voiceProfilesPath = path.join(root, "app/src/main/java/com/asisten/bantu/VoiceProfileManager.kt");
if (fs.existsSync(speakerPath)) {
  const speaker = fs.readFileSync(speakerPath, "utf8");
  if (!speaker.includes("if (!offlineIndonesianVoiceReady)")) {
    failures.push("Primary TTS must refuse speech when offline Indonesian voice is unavailable.");
  }
  if (speaker.includes("tts.setLanguage(Locale.getDefault())")) {
    failures.push("Primary TTS must not fall back to the system default voice.");
  }
} else {
  failures.push("Primary Speaker source is missing; offline TTS policy could not be verified.");
}
if (fs.existsSync(unifiedTtsPath)) {
  const unifiedTts = fs.readFileSync(unifiedTtsPath, "utf8");
  if (!unifiedTts.includes("VoiceProfileManager.hasOfflineIndonesianVoice(tts)")) {
    failures.push("Accessibility TTS must verify an offline Indonesian voice before speaking.");
  }
  if (unifiedTts.includes("tts.setLanguage(Locale.getDefault())")) {
    failures.push("Accessibility TTS must not fall back to the system default voice.");
  }
} else {
  failures.push("Unified accessibility TTS source is missing; offline TTS policy could not be verified.");
}
if (fs.existsSync(voiceProfilesPath)) {
  const profiles = fs.readFileSync(voiceProfilesPath, "utf8");
  if (!profiles.includes("!voice.isNetworkConnectionRequired")) {
    failures.push("TTS voice selection must exclude voices requiring network access.");
  }
} else {
  failures.push("Voice profile source is missing; offline voice selection could not be verified.");
}

const voiceServicePath = path.join(root, "app/src/main/java/com/asisten/bantu/AsistenAccessibilityService.java");
if (fs.existsSync(voiceServicePath)) {
  const voiceService = fs.readFileSync(voiceServicePath, "utf8");
  if (!voiceService.includes("SpeechRecognizer.createOnDeviceSpeechRecognizer")) {
    failures.push("Voice service must use Android on-device speech recognition.");
  }
  if (!voiceService.includes("RecognizerIntent.EXTRA_PREFER_OFFLINE, true")) {
    failures.push("Voice recognition must explicitly prefer offline processing.");
  }
  if (voiceService.includes("preferSystemRecognizer")) {
    failures.push("Voice service must not contain a system/network recognizer fallback.");
  }
} else {
  failures.push("Voice service source is missing; offline ASR policy could not be verified.");
}

if (scanned.length === 0) failures.push("No Android source/configuration files were scanned.");
if (failures.length) {
  console.error("KOBRA Stage 5 privacy/security guard FAILED:");
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}
console.log(`KOBRA Stage 5 privacy/security guard passed (${scanned.length} files scanned).`);
console.log("Note: static scanning cannot prove that all runtime data stays local.");
