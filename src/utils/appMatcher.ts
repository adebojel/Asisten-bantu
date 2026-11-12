import { InstalledApp } from '../types';

export interface AppMatch {
  app: InstalledApp;
  score: number;
}

const MIN_SCORE = 40;
const ALIASES: Record<string, string[]> = {
  "wa": ["whatsapp"],
  "w a": ["whatsapp"],
  "ig": ["instagram"],
  "fb": ["facebook"],
  "yt": ["youtube"],
  "tele": ["telegram"],
  "tg": ["telegram"],
  "surel": ["gmail"],
  "email": ["gmail", "email"],
  "peta": ["maps", "google maps"],
  "galeri": ["galeri", "gallery", "foto", "photos"],
  "foto": ["foto", "photos", "galeri", "gallery"],
  "kamera": ["kamera", "camera"],
  "kalender": ["kalender", "calendar"],
  "jam": ["jam", "clock"],
  "alarm": ["jam", "clock"],
  "kontak": ["kontak", "contacts"],
  "telepon": ["telepon", "phone", "dialer"],
  "telpon": ["telepon", "phone", "dialer"],
  "pesan": ["pesan", "messages"],
  "sms": ["pesan", "messages"],
  "pengaturan": ["pengaturan", "settings", "setelan"],
  "setelan": ["pengaturan", "settings", "setelan"],
  "kalkulator": ["kalkulator", "calculator"],
  "toko aplikasi": ["play store"],
  "playstore": ["play store"],
  "browser": ["chrome", "browser", "internet"],
  "internet": ["chrome", "browser", "internet"],
  "peramban": ["chrome", "browser", "internet"],
  "musik": ["musik", "music", "spotify"],
  "catatan": ["catatan", "notes", "keep"],
  "file": ["file", "files", "berkas"],
  "berkas": ["file", "files", "berkas"],
  "unduhan": ["unduhan", "downloads"],
  "perekam suara": ["perekam suara", "recorder", "voice recorder"],
  "senter": ["senter", "flashlight"],
};

export function normalize(s: string | null | undefined): string {
  if (!s) return "";
  return s.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function distance(a: string, b: string): number {
  const prev: number[] = new Array(b.length + 1);
  const cur: number[] = new Array(b.length + 1);
  for (let j = 0; j <= b.length; j++) prev[j] = j;

  for (let i = 1; i <= a.length; i++) {
    cur[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
      cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
    }
    for (let j = 0; j <= b.length; j++) prev[j] = cur[j];
  }
  return prev[b.length];
}

function isClose(a: string, b: string): boolean {
  const longest = Math.max(a.length, b.length);
  const allowed = longest >= 7 ? 2 : (longest >= 5 ? 1 : 0);
  return allowed > 0 && distance(a, b) <= allowed;
}

function scoreTerm(app: InstalledApp, t: string): number {
  const l = normalize(app.label);
  if (!l || !t) return 0;
  let score = 0;
  if (l === t) score = 100;
  else if (l.startsWith(t + " ")) score = 80;
  else if (t.length >= 3 && l.startsWith(t)) score = 75;
  else if ((" " + l + " ").includes(" " + t + " ")) score = 70;
  else if (l.length >= 4 && (" " + t + " ").includes(" " + l + " ")) score = 65;
  else if (t.length >= 3 && l.includes(t)) score = 55;

  if (score < 50 && t.length >= 3) {
    const compact = t.replace(/\s+/g, "");
    if (compact.length >= 4 && app.pkg && app.pkg.toLowerCase().includes(compact)) {
      score = Math.max(score, 50);
    }
  }

  if (score < 50 && t.length >= 4) {
    if (isClose(l, t)) score = Math.max(score, 50);
    else if (l.includes(" ")) {
      for (const w of l.split(" ")) {
        if (w.length >= 4 && isClose(w, t)) score = Math.max(score, 40);
      }
    }
  }
  return score;
}

export function findApps(apps: InstalledApp[], query: string, max: number = 5): AppMatch[] {
  const q = normalize(query);
  const out: AppMatch[] = [];
  if (!q) return out;

  const terms: string[] = [q];
  const al = ALIASES[q];
  if (al) {
    for (const a of al) {
      if (!terms.includes(a)) terms.push(a);
    }
  }

  for (const app of apps) {
    let best = 0;
    for (let i = 0; i < terms.length; i++) {
      let s = scoreTerm(app, terms[i]);
      if (i > 0) s = Math.floor(s * 0.9);
      best = Math.max(best, s);
    }
    if (best >= MIN_SCORE) {
      out.push({ app, score: best });
    }
  }

  out.sort((a, b) => {
    if (a.score !== b.score) return b.score - a.score;
    if (a.app.label.length !== b.app.label.length) return a.app.label.length - b.app.label.length;
    return a.app.label.localeCompare(b.app.label);
  });

  return out.slice(0, max);
}

export function isClearMatch(m: AppMatch[]): boolean {
  if (!m || m.length === 0) return false;
  if (m[0].score < 45) return false;
  return m.length === 1 || m[0].score - m[1].score >= 10;
}
