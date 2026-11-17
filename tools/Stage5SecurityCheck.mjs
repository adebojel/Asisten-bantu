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
  if (/SpeechRecognizer\\.createSpeechRecognizer\\s*\\(/.test(code)) {
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
