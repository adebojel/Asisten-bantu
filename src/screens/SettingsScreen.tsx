import React, { useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { Settings, Save, ArrowLeft, Volume2, Eye, Bell, Type, Vibrate } from 'lucide-react';

export const SettingsScreen: React.FC = () => {
  const { setCurrentScreen, settings, updateSettings, say } = useKobra();
  const [speechRate, setSpeechRate] = useState(settings.speechRate);
  const [speechPitch, setSpeechPitch] = useState(settings.speechPitch);
  const [readNotifications, setReadNotifications] = useState(settings.readNotifications);
  const [highContrast, setHighContrast] = useState(settings.highContrast);
  const [largeFont, setLargeFont] = useState(settings.largeFont);
  const [hapticFeedback, setHapticFeedback] = useState(settings.hapticFeedback);

  const handleSave = () => {
    updateSettings({ speechRate, speechPitch, readNotifications, highContrast, largeFont, hapticFeedback });
    say('Pengaturan lokal KOBRA berhasil disimpan. Tidak ada API key atau webhook cloud.');
  };

  return (
    <div className="space-y-6 pb-24">
      <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
        <button onClick={() => setCurrentScreen('home')} className="p-3 bg-neutral-900 border-2 border-neutral-700 rounded-2xl flex items-center gap-2 font-bold text-white focus-accessible">
          <ArrowLeft className="w-5 h-5" /> Kembali
        </button>
        <h2 className="text-lg font-black text-white uppercase tracking-wider flex items-center gap-2">
          <Settings className="w-6 h-6 text-yellow-400" /> Pengaturan Lokal
        </h2>
        <button onClick={handleSave} className="px-4 py-2.5 bg-yellow-400 hover:bg-yellow-300 text-black font-black rounded-xl flex items-center gap-2 text-sm focus-accessible">
          <Save className="w-4 h-4" /> Simpan
        </button>
      </div>

      <section className="p-5 bg-neutral-900 border-2 border-emerald-900 rounded-3xl space-y-2">
        <div className="flex items-center gap-3"><Settings className="w-6 h-6 text-emerald-400" /><h3 className="text-lg font-black text-white">Privasi offline-first</h3></div>
        <p className="text-sm text-neutral-300">Percakapan, perintah, dan gambar tidak dikirim ke OpenRouter, Gemini, n8n, atau layanan AI cloud. Fitur yang memerlukan internet dibuka secara terpisah dan diberi tahu kepada pengguna.</p>
      </section>

      <section className="p-5 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-5">
        <div className="flex items-center gap-3"><Volume2 className="w-6 h-6 text-emerald-400" /><h3 className="text-lg font-black text-white">Suara lokal</h3></div>
        <div>
          <div className="flex justify-between text-sm font-bold mb-2"><span className="text-neutral-300">Kecepatan bicara</span><span className="text-yellow-400 font-mono">{speechRate.toFixed(2)}x</span></div>
          <input type="range" min="0.5" max="1.8" step="0.05" value={speechRate} onChange={e => setSpeechRate(parseFloat(e.target.value))} className="w-full accent-yellow-400 cursor-pointer h-3 rounded-lg bg-neutral-700" />
        </div>
        <div>
          <div className="flex justify-between text-sm font-bold mb-2"><span className="text-neutral-300">Tinggi nada</span><span className="text-yellow-400 font-mono">{speechPitch.toFixed(2)}x</span></div>
          <input type="range" min="0.6" max="1.4" step="0.05" value={speechPitch} onChange={e => setSpeechPitch(parseFloat(e.target.value))} className="w-full accent-yellow-400 cursor-pointer h-3 rounded-lg bg-neutral-700" />
        </div>
        <button onClick={() => say('Uji suara lokal. Jika suara Bahasa Indonesia offline belum terpasang, KOBRA tidak akan memakai suara jaringan.')} className="px-4 py-2 bg-neutral-800 rounded-xl text-white font-bold border border-neutral-700 focus-accessible">Uji suara lokal</button>
      </section>

      <section className="p-5 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <h3 className="text-lg font-black text-white flex items-center gap-3"><Eye className="w-6 h-6 text-yellow-400" /> Aksesibilitas</h3>
        {[
          { label: 'Kontras tinggi', value: highContrast, set: setHighContrast, icon: <Eye className="w-5 h-5" /> },
          { label: 'Ukuran huruf besar', value: largeFont, set: setLargeFont, icon: <Type className="w-5 h-5" /> },
          { label: 'Getaran sentuh', value: hapticFeedback, set: setHapticFeedback, icon: <Vibrate className="w-5 h-5" /> },
          { label: 'Bacakan notifikasi', value: readNotifications, set: setReadNotifications, icon: <Bell className="w-5 h-5" /> },
        ].map(item => (
          <label key={item.label} className="flex items-center justify-between gap-4 p-3 bg-black/30 rounded-xl text-white font-bold">
            <span className="flex items-center gap-3">{item.icon}{item.label}</span>
            <input type="checkbox" checked={item.value} onChange={e => item.set(e.target.checked)} className="w-6 h-6 accent-yellow-400" />
          </label>
        ))}
      </section>
    </div>
  );
};
