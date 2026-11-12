import React, { useState, useEffect } from 'react';
import { useKobra } from '../context/KobraContext';
import {
  Phone,
  MessageCircle,
  Eye,
  Compass,
  Bot,
  Settings,
  Clock,
  Battery,
  AlertOctagon,
  Search,
  ExternalLink,
} from 'lucide-react';
import { deviceSensors } from '../services/deviceSensors';

export const LauncherScreen: React.FC = () => {
  const { setCurrentScreen, say, triggerSos, installedApps, openAppByLabel } = useKobra();
  const [batteryLevel, setBatteryLevel] = useState<number>(85);
  const [currentTime, setCurrentTime] = useState<string>('');
  const [currentDate, setCurrentDate] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' }));
      setCurrentDate(now.toLocaleDateString('id-ID', { weekday: 'long', day: 'numeric', month: 'short' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);

    deviceSensors.getBatteryLevel().then(lvl => setBatteryLevel(lvl));

    return () => clearInterval(interval);
  }, []);

  const handleTimeClick = () => {
    say(`Sekarang pukul ${currentTime}, hari ${currentDate}. Baterai ${batteryLevel} persen.`);
  };

  const tiles = [
    {
      id: 'phone',
      label: 'TELEPON',
      sub: 'Panggil Kontak',
      icon: <Phone className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-emerald-600 hover:bg-emerald-500 text-white border-emerald-400',
      action: () => {
        say('Membuka telepon. Sebutkan nama kontak yang ingin dihubungi.');
        window.location.href = 'tel:';
      },
    },
    {
      id: 'wa',
      label: 'WHATSAPP',
      sub: 'Kirim Pesan',
      icon: <MessageCircle className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-green-600 hover:bg-green-500 text-white border-green-400',
      action: () => {
        say('Membuka WhatsApp.');
        window.open('https://web.whatsapp.com', '_blank');
      },
    },
    {
      id: 'vision',
      label: 'MATA KEDUA',
      sub: 'KTP, Warna, Uang',
      icon: <Eye className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-blue-600 hover:bg-blue-500 text-white border-blue-400',
      action: () => {
        setCurrentScreen('vision');
        say('Membuka Mata Kedua.');
      },
    },
    {
      id: 'stick',
      label: 'MODE TONGKAT',
      sub: 'Deteksi Halangan',
      icon: <Compass className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-yellow-500 hover:bg-yellow-400 text-black border-yellow-300',
      action: () => {
        setCurrentScreen('navigation');
        say('Membuka Mode Tongkat.');
      },
    },
    {
      id: 'chat',
      label: 'TEMAN NGOBROL',
      sub: 'AI & P3K Offline',
      icon: <Bot className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-purple-600 hover:bg-purple-500 text-white border-purple-400',
      action: () => {
        setCurrentScreen('aichat');
        say('Membuka Teman Ngobrol AI.');
      },
    },
    {
      id: 'settings',
      label: 'PENGATURAN',
      sub: 'Suara & AI Key',
      icon: <Settings className="w-12 h-12 stroke-[2.5]" />,
      bg: 'bg-neutral-800 hover:bg-neutral-700 text-white border-neutral-600',
      action: () => {
        setCurrentScreen('settings');
        say('Membuka Pengaturan Kobra.');
      },
    },
  ];

  const filteredApps = searchQuery
    ? installedApps.filter(a => a.label.toLowerCase().includes(searchQuery.toLowerCase()))
    : [];

  return (
    <div className="space-y-6 pb-24">
      {/* Top Accessible Clock & Battery Bar */}
      <button
        onClick={handleTimeClick}
        className="w-full p-6 bg-neutral-900 border-4 border-yellow-400 rounded-3xl flex items-center justify-between text-left focus-accessible shadow-xl group"
        aria-label={`Jam ${currentTime}, Baterai ${batteryLevel} persen. Ketuk untuk dibacakan.`}
      >
        <div className="flex items-center gap-4">
          <Clock className="w-12 h-12 text-yellow-400" />
          <div>
            <div className="text-4xl font-black text-white tracking-wider">{currentTime}</div>
            <div className="text-sm font-bold text-neutral-300 uppercase tracking-widest">{currentDate}</div>
          </div>
        </div>
        <div className="flex items-center gap-3 bg-neutral-800 px-4 py-3 rounded-2xl border-2 border-neutral-700">
          <Battery className="w-8 h-8 text-emerald-400" />
          <span className="text-2xl font-black text-white">{batteryLevel}%</span>
        </div>
      </button>

      {/* Large Accessible Tiles Grid */}
      <div className="grid grid-cols-2 gap-4">
        {tiles.map(tile => (
          <button
            key={tile.id}
            onClick={tile.action}
            className={`p-6 rounded-3xl border-4 flex flex-col items-center justify-center text-center gap-3 transition-transform active:scale-95 focus-accessible shadow-xl min-h-[160px] ${tile.bg}`}
            aria-label={`${tile.label}. ${tile.sub}.`}
          >
            {tile.icon}
            <div>
              <div className="text-xl font-black tracking-wide leading-tight">{tile.label}</div>
              <div className="text-xs opacity-90 font-bold mt-1 tracking-wider">{tile.sub}</div>
            </div>
          </button>
        ))}
      </div>

      {/* SOS Big Button */}
      <button
        onClick={triggerSos}
        className="w-full py-6 px-6 bg-red-600 hover:bg-red-500 active:scale-95 text-white border-4 border-red-400 rounded-3xl flex items-center justify-center gap-4 font-black text-2xl tracking-widest shadow-2xl focus-accessible transition-transform animate-pulse"
        aria-label="TOMBOL SOS DARURAT. Ketuk untuk mengirim sinyal darurat dan membunyikan sirine."
      >
        <AlertOctagon className="w-10 h-10 stroke-[3]" />
        TOLONG (SOS DARURAT)
      </button>

      {/* App Finder & All Apps */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <h3 className="text-lg font-black text-white uppercase tracking-wider flex items-center gap-2">
          <Search className="w-5 h-5 text-yellow-400" />
          Cari & Buka Aplikasi
        </h3>
        <div className="relative">
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Ketik nama aplikasi..."
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl px-4 py-3 text-white font-bold text-lg focus-accessible placeholder:text-neutral-500"
          />
        </div>

        {searchQuery && (
          <div className="grid grid-cols-1 gap-2 pt-2">
            {filteredApps.length === 0 ? (
              <p className="text-sm text-neutral-400 py-2">Tidak ditemukan aplikasi yang cocok.</p>
            ) : (
              filteredApps.map(app => (
                <button
                  key={app.pkg}
                  onClick={() => openAppByLabel(app.label)}
                  className="p-3 bg-neutral-800 hover:bg-neutral-700 border border-neutral-700 rounded-xl flex items-center justify-between text-left focus-accessible"
                >
                  <span className="font-bold text-white text-base">{app.label}</span>
                  <ExternalLink className="w-4 h-4 text-neutral-400" />
                </button>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};
