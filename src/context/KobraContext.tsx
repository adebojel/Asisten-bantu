import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import {
  InstalledApp,
  SosContact,
  KobraSettings,
  NotificationItem,
  ChatMessage,
  IndicatorState,
  PendingConfirmation,
  VisionMode,
} from '../types';
import { speaker } from '../services/speaker';
import { getOfflineAnswer } from '../utils/offlineBrain';
import { findApps, isClearMatch, normalize } from '../utils/appMatcher';
import { toInternational } from '../utils/phoneFormat';
import { deviceSensors } from '../services/deviceSensors';
import { askOpenRouterAi, askN8nWebhook, DEFAULT_TEXT_MODELS } from '../services/aiClient';

export const DEFAULT_INSTALLED_APPS: InstalledApp[] = [
  { label: 'WhatsApp', pkg: 'com.whatsapp', category: 'komunikasi', actionUrl: 'https://web.whatsapp.com' },
  { label: 'Telepon', pkg: 'com.android.dialer', category: 'komunikasi' },
  { label: 'Pesan SMS', pkg: 'com.google.android.apps.messaging', category: 'komunikasi' },
  { label: 'Telegram', pkg: 'org.telegram.messenger', category: 'komunikasi', actionUrl: 'https://web.telegram.org' },
  { label: 'Kamera', pkg: 'com.android.camera', category: 'alat' },
  { label: 'Google Maps', pkg: 'com.google.android.apps.maps', category: 'alat', actionUrl: 'https://maps.google.com' },
  { label: 'YouTube', pkg: 'com.google.android.youtube', category: 'hiburan', actionUrl: 'https://youtube.com' },
  { label: 'Gmail', pkg: 'com.google.android.gm', category: 'produktivitas', actionUrl: 'https://mail.google.com' },
  { label: 'Galeri Foto', pkg: 'com.google.android.apps.photos', category: 'alat' },
  { label: 'Jam & Alarm', pkg: 'com.google.android.deskclock', category: 'alat' },
  { label: 'Kalender', pkg: 'com.google.android.calendar', category: 'produktivitas' },
  { label: 'Pengaturan', pkg: 'com.android.settings', category: 'sistem' },
  { label: 'Kalkulator', pkg: 'com.google.android.calculator', category: 'alat' },
  { label: 'Chrome', pkg: 'com.android.chrome', category: 'produktivitas', actionUrl: 'https://google.com' },
  { label: 'Spotify', pkg: 'com.spotify.music', category: 'hiburan', actionUrl: 'https://spotify.com' },
  { label: 'Catatan', pkg: 'com.google.android.keep', category: 'produktivitas' },
  { label: 'Berkas', pkg: 'com.google.android.documentsui', category: 'alat' },
  { label: 'Instagram', pkg: 'com.instagram.android', category: 'komunikasi', actionUrl: 'https://instagram.com' },
  { label: 'Facebook', pkg: 'com.facebook.katana', category: 'komunikasi', actionUrl: 'https://facebook.com' },
  { label: 'Play Store', pkg: 'com.android.vending', category: 'sistem' },
];

const DEFAULT_NOTIFICATIONS: NotificationItem[] = [
  { id: '1', sender: 'Ibu', app: 'WhatsApp', title: 'Pesan Baru', message: 'Nanti pulang jam berapa nak?', time: '10:15', read: false },
  { id: '2', sender: 'Sistem', app: 'Baterai', title: 'Peringatan Baterai', message: 'Baterai tersisa 85 persen.', time: '09:30', read: false },
  { id: '3', sender: 'Budi Darurat', app: 'SMS', title: 'SMS Masuk', message: 'Posisi sudah aman.', time: '08:00', read: true },
];

const HELP_TEXT =
  'Perintah Kobra. Sebut kata Kobra lalu: baca KTP. Baca uang. Warna apa. Ada orang berapa. ' +
  'Apa di depan. Mode tongkat. Teman ngobrol. Baca layar. Sebutkan tombol, lalu tekan nomor. ' +
  'Tekan lalu nama tombol. Scroll bawah atau atas. Geser kiri atau kanan. Kembali. Beranda. ' +
  'Panggil lalu nama. Angkat telepon. Tolak telepon. Kirim pesan ke nama. Kirim SMS ke nama. ' +
  'Baca SMS. Baca notifikasi. Baterai. Jam berapa. Timer lima menit. Alarm jam enam. ' +
  'Senter nyala. Volume naik. Mode getar. Kunci layar. Tolong untuk SOS. Talkback. ' +
  'Buka lalu nama aplikasi apa saja. Buka chat lalu nama kontak, atau Telegram ke nama. Cari aplikasi lalu nama. ' +
  'Hitung dua tambah tiga. Tanya pertolongan pertama, misalnya tersedak.';

const YES_WORDS = new Set(['iya', 'ya', 'yakin', 'oke', 'ok', 'lanjut', 'kirim', 'setuju', 'benar']);
const NO_WORDS = new Set(['tidak', 'batal', 'jangan', 'nggak', 'gak', 'enggak', 'salah']);

interface KobraContextType {
  currentScreen: 'home' | 'launcher' | 'vision' | 'navigation' | 'aichat' | 'settings' | 'sos' | 'notifications';
  setCurrentScreen: (s: 'home' | 'launcher' | 'vision' | 'navigation' | 'aichat' | 'settings' | 'sos' | 'notifications') => void;
  visionInitialMode: VisionMode;
  setVisionInitialMode: (m: VisionMode) => void;
  indicatorState: IndicatorState;
  indicatorEmoji: string;
  isListening: boolean;
  micOn: boolean;
  setMicOn: (on: boolean) => void;
  lastSpokenText: string;
  transcript: string;
  startListening: () => void;
  stopListening: () => void;
  toggleMic: () => void;
  say: (text: string) => void;
  pending: PendingConfirmation | null;
  confirmPending: (yes: boolean) => void;
  settings: KobraSettings;
  updateSettings: (partial: Partial<KobraSettings>) => void;
  sosContacts: SosContact[];
  updateSosContact: (index: number, contact: SosContact) => void;
  triggerSos: () => void;
  notifications: NotificationItem[];
  addNotification: (n: NotificationItem) => void;
  markNotificationRead: (id: string) => void;
  installedApps: InstalledApp[];
  openAppByLabel: (label: string) => void;
  chatMessages: ChatMessage[];
  sendChatMessage: (text: string) => Promise<void>;
  clearChat: () => void;
  processSpokenCommand: (raw: string) => void;
}

const KobraContext = createContext<KobraContextType | undefined>(undefined);

export const KobraProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentScreen, setCurrentScreen] = useState<'home' | 'launcher' | 'vision' | 'navigation' | 'aichat' | 'settings' | 'sos' | 'notifications'>('home');
  const [visionInitialMode, setVisionInitialMode] = useState<VisionMode>('ktp');

  const [indicatorState, setIndicatorState] = useState<IndicatorState>('idle');
  const [indicatorEmoji, setIndicatorEmoji] = useState<string>('🐍');
  const [isListening, setIsListening] = useState<boolean>(false);
  const [micOn, setMicOn] = useState<boolean>(true);
  const [lastSpokenText, setLastSpokenText] = useState<string>('');
  const [transcript, setTranscript] = useState<string>('');
  const [pending, setPending] = useState<PendingConfirmation | null>(null);

  const [settings, setSettings] = useState<KobraSettings>(() => {
    try {
      const saved = localStorage.getItem('kobra_settings');
      if (saved) return JSON.parse(saved);
    } catch (ignored) {}
    return {
      apiKey: '',
      model: DEFAULT_TEXT_MODELS[0],
      n8nUrl: '',
      speechRate: 0.95,
      speechPitch: 1.0,
      readNotifications: true,
      highContrast: true,
      largeFont: true,
      hapticFeedback: true,
    };
  });

  const [sosContacts, setSosContacts] = useState<SosContact[]>(() => {
    try {
      const saved = localStorage.getItem('kobra_sos');
      if (saved) return JSON.parse(saved);
    } catch (ignored) {}
    return [
      { name: 'Ibu', phone: '081234567890', relation: 'Keluarga' },
      { name: 'Budi (Pendamping)', phone: '089876543210', relation: 'Relawan' },
    ];
  });

  const [notifications, setNotifications] = useState<NotificationItem[]>(DEFAULT_NOTIFICATIONS);
  const [installedApps] = useState<InstalledApp[]>(DEFAULT_INSTALLED_APPS);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'kobra',
      text: 'Halo! Saya Kobra, asisten suara dan mata kedua Anda. Ada yang bisa saya bantu hari ini?',
      timestamp: new Date(),
      source: 'system',
    },
  ]);

  const recognitionRef = useRef<any>(null);
  const conversationUntilRef = useRef<number>(0);

  useEffect(() => {
    localStorage.setItem('kobra_settings', JSON.stringify(settings));
    speaker.setRate(settings.speechRate);
    speaker.setPitch(settings.speechPitch);
  }, [settings]);

  useEffect(() => {
    localStorage.setItem('kobra_sos', JSON.stringify(sosContacts));
  }, [sosContacts]);

  useEffect(() => {
    const onStart = () => {
      setIndicatorState('speak');
      setIndicatorEmoji('🔊');
    };
    const onEnd = () => {
      if (micOn) {
        setIndicatorState('idle');
        setIndicatorEmoji('🐍');
      } else {
        setIndicatorState('off');
        setIndicatorEmoji('🔇');
      }
    };

    speaker.addListener(onStart, onEnd);
    return () => speaker.removeListener(onStart, onEnd);
  }, [micOn]);

  const say = useCallback((text: string) => {
    setLastSpokenText(text);
    speaker.speak(text);
  }, []);

  const triggerSos = useCallback(() => {
    say('PERINGATAN SOS. Mengaktifkan sirine darurat dan mengirim lokasi ke kontak darurat.');
    deviceSensors.startEmergencySiren();

    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        pos => {
          const { latitude, longitude } = pos.coords;
          const mapUrl = `https://maps.google.com/?q=${latitude},${longitude}`;
          const contact = sosContacts[0] || { name: 'Keluarga', phone: '112' };
          const msg = `TOLONG! Sinyal Darurat SOS Kobra OS dari ${contact.name}. Lokasi saya: ${mapUrl}`;
          const intl = toInternational(contact.phone);
          const waUrl = `https://wa.me/${intl}?text=${encodeURIComponent(msg)}`;
          say(`Lokasi didapatkan: garis lintang ${latitude.toFixed(4)}, bujur ${longitude.toFixed(4)}. Tautan dikirim.`);
          window.open(waUrl, '_blank');
        },
        () => {
          say('Gagal membaca GPS. Mengirim pesan darurat tanpa koordinat pasti.');
          const contact = sosContacts[0] || { name: 'Keluarga', phone: '112' };
          const msg = `TOLONG! Sinyal Darurat SOS Kobra OS. Harap hubungi saya sekarang.`;
          window.open(`https://wa.me/${toInternational(contact.phone)}?text=${encodeURIComponent(msg)}`, '_blank');
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    }

    setTimeout(() => {
      deviceSensors.stopEmergencySiren();
    }, 8000);
  }, [say, sosContacts]);

  const openAppByLabel = useCallback((label: string) => {
    const match = installedApps.find(a => normalize(a.label) === normalize(label));
    if (match) {
      if (match.actionUrl) {
        window.open(match.actionUrl, '_blank');
        say(`Membuka ${match.label}.`);
      } else if (match.label === 'Kamera') {
        setCurrentScreen('vision');
        say('Membuka Kamera dan Mata Kedua.');
      } else if (match.label === 'Pengaturan') {
        setCurrentScreen('settings');
        say('Membuka Pengaturan Kobra.');
      } else {
        say(`Membuka aplikasi ${match.label}.`);
      }
    } else {
      say(`Aplikasi ${label} tidak ditemukan.`);
    }
  }, [installedApps, say]);

  const processSpokenCommand = useCallback(async (raw: string) => {
    const text = raw.toLowerCase().trim();
    if (!text) return;
    setTranscript(raw);

    if (pending) {
      const p = pending;
      setPending(null);
      const isExpired = Date.now() - p.timestamp > 30000;
      if (!isExpired) {
        const words = text.split(/\s+/);
        const yes = words.some(w => YES_WORDS.has(w));
        const no = words.some(w => NO_WORDS.has(w));

        if (p.kind === 'sos') {
          if (yes && !no) triggerSos();
          else say('SOS dibatalkan.');
          return;
        } else if (p.kind === 'call') {
          if (yes && !no) {
            say(`Mencoba memanggil ${p.a}.`);
            const phone = p.b ? toInternational(p.b) : '';
            if (phone) window.location.href = `tel:${phone}`;
          } else {
            say('Panggilan dibatalkan.');
          }
          return;
        } else if (p.kind === 'msg_body') {
          if (no) {
            say('Pesan dibatalkan.');
          } else {
            setPending({ kind: 'msg_confirm', a: p.a, b: text, timestamp: Date.now() });
            say(`Pesan untuk ${p.a}: ${text}. Bilang iya untuk mengirim.`);
          }
          return;
        } else if (p.kind === 'msg_confirm') {
          if (yes && !no) {
            say(`Mengirim pesan WhatsApp ke ${p.a}.`);
            const contact = sosContacts.find(c => normalize(c.name).includes(normalize(p.a)));
            const phone = contact ? toInternational(contact.phone) : '';
            window.open(`https://wa.me/${phone}?text=${encodeURIComponent(p.b)}`, '_blank');
          } else {
            say('Pesan dibatalkan.');
          }
          return;
        } else if (p.kind === 'app_pick') {
          const numWords: Record<string, number> = { 'satu': 1, '1': 1, 'dua': 2, '2': 2, 'tiga': 3, '3': 3, 'empat': 4, '4': 4 };
          let chosen = 0;
          for (const w of words) {
            if (numWords[w]) { chosen = numWords[w]; break; }
          }
          if (chosen > 0 && p.candidates && chosen <= p.candidates.length) {
            const pick = p.candidates[chosen - 1];
            openAppByLabel(pick.label);
          } else {
            say('Pilihan tidak dikenali. Pembukaan aplikasi dibatalkan.');
          }
          return;
        }
      }
    }

    let cmd = text;
    const wakes = ['kobra os', 'kobra', 'cobra', 'kobro', 'kobr'];
    let matchedWake = false;
    for (const w of wakes) {
      const idx = text.indexOf(w);
      if (idx >= 0) {
        cmd = text.substring(idx + w.length).trim();
        matchedWake = true;
        break;
      }
    }

    if (!matchedWake) {
      if (currentScreen === 'aichat') {
        sendChatMessage(text);
        return;
      }
      if (Date.now() > conversationUntilRef.current) {
        return;
      }
    }

    conversationUntilRef.current = Date.now() + 15000;

    const has = (...keys: string[]) => keys.some(k => cmd.includes(k));
    const word = (w: string) => cmd.split(/\s+/).includes(w);

    if (!cmd) {
      say('Ya, saya dengarkan.');
      return;
    }

    if (has('bantuan', 'bisa apa', 'daftar perintah')) {
      say(HELP_TEXT);
      return;
    }
    if (word('diam') || has('berhenti bicara', 'hentikan suara')) {
      speaker.stop();
      return;
    }
    if (has('ulangi')) {
      say(lastSpokenText || 'Belum ada ucapan sebelumnya.');
      return;
    }
    if (has('matikan mikrofon')) {
      setMicOn(false);
      say('Mikrofon dimatikan.');
      return;
    }
    if (has('nyalakan mikrofon', 'hidupkan mikrofon')) {
      setMicOn(true);
      say('Mikrofon aktif.');
      return;
    }

    if (has('baca ktp', 'baca kk', 'baca dokumen', 'ktp saya')) {
      setVisionInitialMode('ktp');
      setCurrentScreen('vision');
      say('Membuka baca KTP. Arahkan kamera ke dokumen.');
      return;
    }
    if (has('baca uang', 'uang berapa', 'duit')) {
      setVisionInitialMode('money');
      setCurrentScreen('vision');
      say('Membuka baca uang rupiah.');
      return;
    }
    if (has('warna')) {
      setVisionInitialMode('color');
      setCurrentScreen('vision');
      say('Membuka pendeteksi warna.');
      return;
    }
    if (has('ada orang', 'deteksi orang', 'berapa orang')) {
      setVisionInitialMode('face');
      setCurrentScreen('vision');
      say('Membuka deteksi orang dan wajah.');
      return;
    }
    if (has('apa di depan', 'lihat apa', 'deskripsikan', 'ada apa di depan')) {
      setVisionInitialMode('describe');
      setCurrentScreen('vision');
      say('Membuka deskripsi pandangan depan.');
      return;
    }

    if (has('tongkat', 'halangan', 'mode tongkat')) {
      setCurrentScreen('navigation');
      say('Mode tongkat dibuka. Arahkan kamera ke depan.');
      return;
    }
    if (has('ngobrol', 'curhat', 'teman bicara', 'teman ngobrol')) {
      setCurrentScreen('aichat');
      say('Membuka teman ngobrol.');
      return;
    }

    if (cmd === 'tolong' || word('sos') || has('darurat', 'minta tolong')) {
      setPending({ kind: 'sos', a: '', b: '', timestamp: Date.now() });
      say('Saya akan kirim SOS dengan lokasi ke kontak darurat. Bilang iya untuk lanjut.');
      return;
    }

    if (has('beranda', 'layar utama', 'menu utama')) {
      setCurrentScreen('launcher');
      say('Ke layar utama Kobra.');
      return;
    }
    if (has('hub', 'pengaturan awal', 'halaman awal', 'setup')) {
      setCurrentScreen('home');
      say('Ke beranda pengaturan sistem.');
      return;
    }
    if (has('kembali')) {
      setCurrentScreen('launcher');
      say('Kembali.');
      return;
    }
    if (has('baca notifikasi', 'notifikasi apa', 'ada notifikasi')) {
      setCurrentScreen('notifications');
      const unread = notifications.filter(n => !n.read);
      if (unread.length === 0) {
        say('Tidak ada notifikasi baru.');
      } else {
        const textSum = unread.slice(0, 3).map(n => `Dari ${n.sender} di ${n.app}: ${n.message}`).join('. ');
        say(`Ada ${unread.length} notifikasi baru. ${textSum}`);
      }
      return;
    }
    if (has('baca sms', 'sms terbaru')) {
      const sms = notifications.filter(n => n.app.toLowerCase().includes('sms'));
      if (sms.length > 0) {
        say(`SMS terbaru dari ${sms[0].sender}: ${sms[0].message}`);
      } else {
        say('Tidak ada SMS terbaru.');
      }
      return;
    }

    if (has('baterai')) {
      const lvl = await deviceSensors.getBatteryLevel();
      say(`Baterai tersisa ${lvl} persen.`);
      return;
    }
    if (has('jam berapa', 'pukul berapa') || word('jam')) {
      const now = new Date();
      const timeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
      say(`Sekarang pukul ${timeStr}.`);
      return;
    }
    if (has('tanggal', 'hari apa')) {
      const now = new Date();
      const dateStr = now.toLocaleDateString('id-ID', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
      say(`Hari ini ${dateStr}.`);
      return;
    }
    if (has('senter')) {
      const turnOn = !has('matikan', 'mati');
      await deviceSensors.toggleTorch(turnOn);
      say(turnOn ? 'Senter menyala.' : 'Senter dimatikan.');
      return;
    }

    if (has('panggil ', 'telepon ', 'telpon ')) {
      const target = cmd.replace(/panggil|telepon|telpon/g, '').trim();
      const contact = sosContacts.find(c => normalize(c.name).includes(normalize(target)));
      if (contact) {
        setPending({ kind: 'call', a: contact.name, b: contact.phone, timestamp: Date.now() });
        say(`Panggil ${contact.name}? Bilang iya untuk menghubungi.`);
      } else {
        say(`Kontak ${target} tidak ditemukan di daftar.`);
      }
      return;
    }

    if (has('kirim pesan', 'kirim wa', 'kirim whatsapp')) {
      const parts = cmd.split(' ke ');
      const target = parts.length > 1 ? parts[1].trim() : '';
      if (target) {
        setPending({ kind: 'msg_body', a: target, b: '', timestamp: Date.now() });
        say(`Sebutkan isi pesan yang ingin dikirim ke ${target}.`);
      } else {
        say('Kirim pesan ke siapa? Sebutkan nama kontak.');
      }
      return;
    }

    if (cmd.startsWith('buka ')) {
      const query = cmd.substring(5).trim();
      const matches = findApps(installedApps, query);
      if (matches.length === 0) {
        say(`Aplikasi ${query} tidak ditemukan.`);
      } else if (isClearMatch(matches)) {
        openAppByLabel(matches[0].app.label);
      } else {
        const candidates = matches.slice(0, 3).map(m => m.app);
        setPending({
          kind: 'app_pick',
          a: query,
          b: '',
          candidates,
          timestamp: Date.now(),
        });
        const listStr = candidates.map((c, i) => `nomor ${i + 1} untuk ${c.label}`).join(', ');
        say(`Ada beberapa aplikasi yang mirip: ${listStr}. Sebutkan nomornya.`);
      }
      return;
    }

    if (cmd.startsWith('cari ') || cmd.startsWith('google ')) {
      const q = cmd.replace(/^(cari|google)\s+/, '').trim();
      say(`Mencari ${q} di web.`);
      window.open(`https://www.google.com/search?q=${encodeURIComponent(q)}`, '_blank');
      return;
    }
    if (cmd.startsWith('putar ') || has('youtube')) {
      const q = cmd.replace(/putar|youtube|di/g, '').trim();
      say(`Memutar ${q} di YouTube.`);
      window.open(`https://www.youtube.com/results?search_query=${encodeURIComponent(q)}`, '_blank');
      return;
    }

    const local = getOfflineAnswer(cmd);
    if (local) {
      say(local);
      return;
    }

    if (!navigator.onLine) {
      say('Perintah itu butuh internet, dan saat ini internet terputus. Coba lagi saat tersambung.');
      return;
    }

    if (settings.n8nUrl) {
      try {
        say('Sebentar, saya teruskan ke otomatisasi.');
        const n8nReply = await askN8nWebhook(settings.n8nUrl, cmd);
        say(n8nReply);
        return;
      } catch (ignored) {}
    }

    if (settings.apiKey) {
      try {
        say('Sebentar, saya tanyakan ke AI.');
        const aiReply = await askOpenRouterAi(settings.apiKey, cmd, settings.model);
        say(aiReply);
        return;
      } catch (ignored) {
        say('Maaf, layanan AI sedang sibuk. Coba beberapa saat lagi.');
        return;
      }
    }

    say('Perintah tidak dikenali. Katakan Kobra bantuan untuk melihat apa saja yang bisa saya bantu.');
  }, [pending, currentScreen, say, lastSpokenText, notifications, installedApps, openAppByLabel, sosContacts, settings, triggerSos]);

  const startListening = useCallback(() => {
    if (!micOn || speaker.isSpeaking()) return;

    const SpeechRecognitionClass = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognitionClass) {
      setIndicatorState('error');
      setIndicatorEmoji('🎤✖');
      return;
    }

    try {
      if (recognitionRef.current) {
        recognitionRef.current.abort();
      }

      const rec = new SpeechRecognitionClass();
      rec.lang = 'id-ID';
      rec.continuous = true;
      rec.interimResults = false;

      rec.onstart = () => {
        setIsListening(true);
        setIndicatorState('listen');
        setIndicatorEmoji('🎤');
      };

      rec.onresult = (event: any) => {
        const last = event.results.length - 1;
        const spoken = event.results[last][0].transcript;
        if (spoken) {
          processSpokenCommand(spoken);
        }
      };

      rec.onerror = (e: any) => {
        if (e.error === 'not-allowed') {
          setIndicatorState('error');
          setIndicatorEmoji('🎤✖');
        }
      };

      rec.onend = () => {
        setIsListening(false);
        if (micOn && !speaker.isSpeaking()) {
          setIndicatorState('idle');
          setIndicatorEmoji('🐍');
        }
      };

      recognitionRef.current = rec;
      rec.start();
    } catch (err) {
      console.warn('Failed to start speech recognition', err);
    }
  }, [micOn, processSpokenCommand]);

  const stopListening = useCallback(() => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort();
      } catch (ignored) {}
    }
    setIsListening(false);
  }, []);

  const toggleMic = useCallback(() => {
    const next = !micOn;
    setMicOn(next);
    if (!next) {
      stopListening();
      setIndicatorState('off');
      setIndicatorEmoji('🔇');
      say('Mikrofon dimatikan.');
    } else {
      setIndicatorState('idle');
      setIndicatorEmoji('🐍');
      say('Mikrofon aktif.');
      startListening();
    }
  }, [micOn, stopListening, startListening, say]);

  const confirmPending = useCallback((yes: boolean) => {
    if (pending) {
      processSpokenCommand(yes ? 'iya' : 'batal');
    }
  }, [pending, processSpokenCommand]);

  const updateSettings = useCallback((partial: Partial<KobraSettings>) => {
    setSettings(prev => ({ ...prev, ...partial }));
  }, []);

  const updateSosContact = useCallback((index: number, contact: SosContact) => {
    setSosContacts(prev => {
      const next = [...prev];
      next[index] = contact;
      return next;
    });
  }, []);

  const addNotification = useCallback((n: NotificationItem) => {
    setNotifications(prev => [n, ...prev]);
    if (settings.readNotifications) {
      say(`Notifikasi baru dari ${n.sender} di ${n.app}: ${n.message}`);
    }
  }, [settings.readNotifications, say]);

  const markNotificationRead = useCallback((id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
  }, []);

  const sendChatMessage = useCallback(async (text: string) => {
    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      sender: 'user',
      text,
      timestamp: new Date(),
    };
    setChatMessages(prev => [...prev, userMsg]);

    const offlineAns = getOfflineAnswer(text);
    if (offlineAns) {
      const replyMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'kobra',
        text: offlineAns,
        timestamp: new Date(),
        source: 'offline_brain',
      };
      setChatMessages(prev => [...prev, replyMsg]);
      say(offlineAns);
      return;
    }

    if (!navigator.onLine) {
      const offlineNotice = 'Internet sedang terputus, hanya fitur offline dasar yang tersedia.';
      setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: 'kobra', text: offlineNotice, timestamp: new Date(), source: 'system' }]);
      say(offlineNotice);
      return;
    }

    if (settings.n8nUrl) {
      try {
        const reply = await askN8nWebhook(settings.n8nUrl, text);
        setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: 'kobra', text: reply, timestamp: new Date(), source: 'n8n' }]);
        say(reply);
        return;
      } catch (ignored) {}
    }

    if (settings.apiKey) {
      try {
        const reply = await askOpenRouterAi(settings.apiKey, text, settings.model);
        setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: 'kobra', text: reply, timestamp: new Date(), source: 'openrouter' }]);
        say(reply);
        return;
      } catch (e: any) {
        const errMsg = 'Maaf, gagal menghubungi AI. Periksa API key atau koneksi Anda.';
        setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: 'kobra', text: errMsg, timestamp: new Date(), source: 'system' }]);
        say(errMsg);
        return;
      }
    }

    const friendlyFallback = `Saya mendengarkan: "${text}". Hubungkan API Key OpenRouter di menu Pengaturan AI untuk percakapan bebas tanpa batas.`;
    setChatMessages(prev => [...prev, { id: Date.now().toString(), sender: 'kobra', text: friendlyFallback, timestamp: new Date(), source: 'system' }]);
    say(friendlyFallback);
  }, [say, settings]);

  const clearChat = useCallback(() => {
    setChatMessages([]);
  }, []);

  return (
    <KobraContext.Provider
      value={{
        currentScreen,
        setCurrentScreen,
        visionInitialMode,
        setVisionInitialMode,
        indicatorState,
        indicatorEmoji,
        isListening,
        micOn,
        setMicOn,
        lastSpokenText,
        transcript,
        startListening,
        stopListening,
        toggleMic,
        say,
        pending,
        confirmPending,
        settings,
        updateSettings,
        sosContacts,
        updateSosContact,
        triggerSos,
        notifications,
        addNotification,
        markNotificationRead,
        installedApps,
        openAppByLabel,
        chatMessages,
        sendChatMessage,
        clearChat,
        processSpokenCommand,
      }}
    >
      {children}
    </KobraContext.Provider>
  );
};

export const useKobra = () => {
  const context = useContext(KobraContext);
  if (!context) throw new Error('useKobra must be used within KobraProvider');
  return context;
};
