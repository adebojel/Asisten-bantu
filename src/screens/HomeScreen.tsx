import React from 'react';
import { useKobra } from '../context/KobraContext';
import {
  ShieldCheck,
  Eye,
  Bell,
  Cpu,
  HeartPulse,
  BatteryCharging,
  Radio,
  LayoutGrid,
  Mic,
  Volume2,
  Compass,
  MessageSquare,
} from 'lucide-react';

export const HomeScreen: React.FC = () => {
  const {
    setCurrentScreen,
    say,
    micOn,
    toggleMic,
    notifications,
    settings,
    sosContacts,
    startListening,
  } = useKobra();

  const handleSpeakStatus = () => {
    const unread = notifications.filter(n => !n.read).length;
    say(
      `Status Kobra OS 7.1. Mikrofon ${micOn ? 'aktif' : 'mati'}. ` +
      `Kontak darurat utama ${sosContacts[0]?.name || 'belum diisi'}. ` +
      `Ada ${unread} notifikasi belum dibaca. Bilang Kobra bantuan untuk panduan.`
    );
  };

  const buttons = [
    {
      num: 1,
      title: 'Izin Sistem & Sensor',
      desc: 'Mikrofon, kamera, dan lokasi untuk asisten suara & mata kedua.',
      icon: <ShieldCheck className="w-8 h-8 text-emerald-400" />,
      action: async () => {
        try {
          await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
          say('Izin mikrofon dan kamera sudah diberikan.');
        } catch (e) {
          say('Meminta izin mikrofon dan kamera.');
        }
      },
    },
    {
      num: 2,
      title: 'Aksesibilitas & Kontras',
      desc: 'Pengaturan visual kontras tinggi dan ukuran huruf besar.',
      icon: <Eye className="w-8 h-8 text-yellow-400" />,
      action: () => {
        say('Aksesibilitas kontras tinggi aktif untuk kemudahan penglihatan.');
      },
    },
    {
      num: 3,
      title: 'Akses Notifikasi & SMS',
      desc: `Pembacaan notifikasi otomatis (${settings.readNotifications ? 'Aktif' : 'Mati'}).`,
      icon: <Bell className="w-8 h-8 text-blue-400" />,
      action: () => {
        setCurrentScreen('notifications');
        say('Membuka daftar notifikasi dan SMS.');
      },
    },
    {
      num: 4,
      title: 'Pengaturan AI & n8n',
      desc: 'Konfigurasi OpenRouter API key, model AI, dan n8n webhook.',
      icon: <Cpu className="w-8 h-8 text-purple-400" />,
      action: () => {
        setCurrentScreen('settings');
        say('Membuka pengaturan AI dan otomatisasi.');
      },
    },
    {
      num: 5,
      title: 'Kontak Darurat SOS',
      desc: `Terdaftar: ${sosContacts[0]?.name || 'Belum ada'} (${sosContacts[0]?.phone || '-'}).`,
      icon: <HeartPulse className="w-8 h-8 text-red-500" />,
      action: () => {
        setCurrentScreen('sos');
        say('Membuka konfigurasi kontak darurat SOS.');
      },
    },
    {
      num: 6,
      title: 'Status Hemat Baterai',
      desc: 'Optimalisasi daya latar belakang untuk respon suara cepat.',
      icon: <BatteryCharging className="w-8 h-8 text-green-400" />,
      action: () => {
        say('Optimalisasi baterai aktif. Kobra berjalan hemat daya di latar.');
      },
    },
    {
      num: 7,
      title: 'Layanan Siaga Latar Belakang',
      desc: micOn ? 'Mikrofon siaga mendengarkan kata Kobra.' : 'Mikrofon dalam keadaan mati.',
      icon: <Radio className="w-8 h-8 text-cyan-400" />,
      action: () => {
        toggleMic();
      },
    },
    {
      num: 8,
      title: 'Layar Utama / Launcher Kobra',
      desc: 'Beralih ke antarmuka ubin taktil tombol besar ramah tunanetra.',
      icon: <LayoutGrid className="w-8 h-8 text-amber-400" />,
      action: () => {
        setCurrentScreen('launcher');
        say('Beralih ke layar utama Kobra.');
      },
    },
  ];

  return (
    <div className="space-y-6 pb-24">
      {/* Header Info */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <span className="text-3xl">🐍</span>
              <h1 className="text-2xl font-black text-white tracking-wide">Kobra OS 7.1</h1>
              <span className="text-xs uppercase bg-emerald-950 text-emerald-400 px-2.5 py-1 rounded-full border border-emerald-800 font-bold">
                Asisten Bantu
              </span>
            </div>
            <p className="text-sm text-neutral-400 mt-1">
              Asisten Suara & Mata Kedua untuk Tunanetra. Katakan <strong className="text-yellow-400 font-bold">"Kobra"</strong> lalu perintah.
            </p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleSpeakStatus}
              className="px-4 py-2 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 font-bold rounded-xl flex items-center gap-2 text-sm border border-neutral-700 focus-accessible"
              aria-label="Bacakan status sistem Kobra"
            >
              <Volume2 className="w-4 h-4 text-emerald-400" />
              Cek Status
            </button>
            <button
              onClick={() => {
                say('Ya, saya dengarkan.');
                startListening();
              }}
              className="px-4 py-2 bg-yellow-400 hover:bg-yellow-300 text-black font-black rounded-xl flex items-center gap-2 text-sm focus-accessible shadow-md"
              aria-label="Mulai bicara ke Kobra"
            >
              <Mic className="w-4 h-4" />
              Bicara
            </button>
          </div>
        </div>
      </div>

      {/* Quick Launch Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <button
          onClick={() => {
            setCurrentScreen('vision');
            say('Membuka Mata Kedua.');
          }}
          className="p-4 bg-neutral-900 hover:bg-neutral-800 border-2 border-blue-500/50 rounded-2xl flex flex-col items-center text-center gap-2 focus-accessible"
        >
          <Eye className="w-8 h-8 text-blue-400" />
          <span className="font-bold text-white text-base">Mata Kedua</span>
          <span className="text-xs text-neutral-400">KTP, Warna, Wajah</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('navigation');
            say('Membuka Mode Tongkat.');
          }}
          className="p-4 bg-neutral-900 hover:bg-neutral-800 border-2 border-yellow-500/50 rounded-2xl flex flex-col items-center text-center gap-2 focus-accessible"
        >
          <Compass className="w-8 h-8 text-yellow-400" />
          <span className="font-bold text-white text-base">Mode Tongkat</span>
          <span className="text-xs text-neutral-400">Deteksi Halangan</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('aichat');
            say('Membuka Teman Ngobrol AI.');
          }}
          className="p-4 bg-neutral-900 hover:bg-neutral-800 border-2 border-purple-500/50 rounded-2xl flex flex-col items-center text-center gap-2 focus-accessible"
        >
          <MessageSquare className="w-8 h-8 text-purple-400" />
          <span className="font-bold text-white text-base">Teman Ngobrol</span>
          <span className="text-xs text-neutral-400">Offline & AI</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('launcher');
            say('Membuka Layar Utama Kobra.');
          }}
          className="p-4 bg-neutral-900 hover:bg-neutral-800 border-2 border-emerald-500/50 rounded-2xl flex flex-col items-center text-center gap-2 focus-accessible"
        >
          <LayoutGrid className="w-8 h-8 text-emerald-400" />
          <span className="font-bold text-white text-base">Layar Utama</span>
          <span className="text-xs text-neutral-400">Tombol Taktil Besar</span>
        </button>
      </div>

      {/* 8-Step Setup List from MainActivity.java */}
      <div>
        <h2 className="text-lg font-black text-neutral-300 uppercase tracking-wider mb-3">
          8 Tombol Persiapan & Status Sistem
        </h2>
        <div className="space-y-3">
          {buttons.map(b => (
            <button
              key={b.num}
              onClick={b.action}
              className="w-full text-left p-4 bg-neutral-900/90 hover:bg-neutral-800 border-2 border-neutral-800 hover:border-neutral-700 rounded-2xl flex items-center justify-between gap-4 transition-all focus-accessible group"
            >
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-xl bg-neutral-800 border border-neutral-700 flex items-center justify-center font-black text-lg text-yellow-400 group-hover:scale-105 transition-transform">
                  {b.num}
                </div>
                <div>
                  <h3 className="font-bold text-lg text-white group-hover:text-yellow-300 transition-colors">
                    {b.title}
                  </h3>
                  <p className="text-sm text-neutral-400">{b.desc}</p>
                </div>
              </div>
              <div className="shrink-0">{b.icon}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
