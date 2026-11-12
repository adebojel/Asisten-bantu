import React, { useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { Settings, Save, ArrowLeft, Key, Cpu, Radio, Volume2, Eye } from 'lucide-react';
import { DEFAULT_TEXT_MODELS } from '../services/aiClient';

export const SettingsScreen: React.FC = () => {
  const { setCurrentScreen, settings, updateSettings, say } = useKobra();

  const [apiKey, setApiKey] = useState(settings.apiKey);
  const [model, setModel] = useState(settings.model);
  const [n8nUrl, setN8nUrl] = useState(settings.n8nUrl);
  const [speechRate, setSpeechRate] = useState(settings.speechRate);
  const [speechPitch, setSpeechPitch] = useState(settings.speechPitch);
  const [readNotifications, setReadNotifications] = useState(settings.readNotifications);

  const handleSave = () => {
    updateSettings({
      apiKey: apiKey.trim(),
      model: model.trim(),
      n8nUrl: n8nUrl.trim(),
      speechRate,
      speechPitch,
      readNotifications,
    });
    say('Pengaturan Kobra berhasil disimpan.');
  };

  const handleTestVoice = () => {
    say('Ini adalah contoh uji suara Kobra OS dengan kecepatan yang Anda pilih.');
  };

  return (
    <div className="space-y-6 pb-24">
      {/* Top Header */}
      <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
        <button
          onClick={() => {
            setCurrentScreen('home');
            say('Kembali ke menu beranda.');
          }}
          className="p-3 bg-neutral-900 border-2 border-neutral-700 rounded-2xl flex items-center gap-2 font-bold text-white focus-accessible"
        >
          <ArrowLeft className="w-5 h-5" />
          Kembali
        </button>

        <h2 className="text-lg font-black text-white uppercase tracking-wider flex items-center gap-2">
          <Settings className="w-6 h-6 text-yellow-400" />
          Pengaturan Kobra
        </h2>

        <button
          onClick={handleSave}
          className="px-4 py-2.5 bg-yellow-400 hover:bg-yellow-300 text-black font-black rounded-xl flex items-center gap-2 text-sm focus-accessible shadow-md active:scale-95 transition-transform"
        >
          <Save className="w-4 h-4" />
          Simpan
        </button>
      </div>

      {/* AI Settings Section */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <div className="flex items-center gap-3">
          <Key className="w-6 h-6 text-purple-400" />
          <h3 className="text-lg font-black text-white uppercase tracking-wide">Pengaturan AI Online (OpenRouter)</h3>
        </div>
        <p className="text-sm text-neutral-400">
          Daftar gratis di openrouter.ai untuk mendapatkan API key. Fitur offline dasar tetap berjalan tanpa key.
        </p>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5">OpenRouter API Key</label>
          <input
            type="password"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder="sk-or-v1-..."
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-mono text-sm focus-accessible"
          />
        </div>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5 flex items-center gap-2">
            <Cpu className="w-4 h-4 text-yellow-400" />
            Model AI Teks
          </label>
          <select
            value={model}
            onChange={e => setModel(e.target.value)}
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-bold text-sm focus-accessible"
          >
            {DEFAULT_TEXT_MODELS.map(m => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* n8n Webhook Section */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <div className="flex items-center gap-3">
          <Radio className="w-6 h-6 text-cyan-400" />
          <h3 className="text-lg font-black text-white uppercase tracking-wide">Webhook Otomatisasi (n8n)</h3>
        </div>
        <p className="text-sm text-neutral-400">
          Opsional: Kirim ucapan ke webhook n8n untuk integrasi IoT, smart home, atau workflow khusus.
        </p>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5">URL Webhook n8n</label>
          <input
            type="url"
            value={n8nUrl}
            onChange={e => setN8nUrl(e.target.value)}
            placeholder="https://n8n.example.com/webhook/kobra"
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-mono text-sm focus-accessible"
          />
        </div>
      </div>

      {/* Voice & Speech Rate Section */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Volume2 className="w-6 h-6 text-emerald-400" />
            <h3 className="text-lg font-black text-white uppercase tracking-wide">Kecepatan & Nada Suara TTS</h3>
          </div>
          <button
            onClick={handleTestVoice}
            className="px-3 py-1.5 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 font-bold rounded-xl text-xs border border-neutral-700 focus-accessible"
          >
            Uji Suara
          </button>
        </div>

        <div>
          <div className="flex justify-between text-sm font-bold mb-2">
            <span className="text-neutral-300">Kecepatan Bicara</span>
            <span className="text-yellow-400 font-mono">{speechRate.toFixed(2)}x</span>
          </div>
          <input
            type="range"
            min="0.5"
            max="1.8"
            step="0.05"
            value={speechRate}
            onChange={e => setSpeechRate(parseFloat(e.target.value))}
            className="w-full accent-yellow-400 cursor-pointer h-3 rounded-lg bg-neutral-700"
          />
        </div>

        <div>
          <div className="flex justify-between text-sm font-bold mb-2">
            <span className="text-neutral-300">Tinggi Nada (Pitch)</span>
            <span className="text-yellow-400 font-mono">{speechPitch.toFixed(2)}x</span>
          </div>
          <input
            type="range"
            min="0.6"
            max="1.4"
            step="0.05"
            value={speechPitch}
            onChange={e => setSpeechPitch(parseFloat(e.target.value))}
            className="w-full accent-yellow-400 cursor-pointer h-3 rounded-lg bg-neutral-700"
          />
        </div>

        <div className="pt-2 border-t border-neutral-800 flex items-center justify-between">
          <div>
            <div className="font-bold text-white text-base">Bacakan Notifikasi Masuk Otomatis</div>
            <div className="text-xs text-neutral-400">Membacakan pesan masuk dan panggilan keras-keras</div>
          </div>
          <input
            type="checkbox"
            checked={readNotifications}
            onChange={e => setReadNotifications(e.target.checked)}
            className="w-6 h-6 accent-yellow-400 rounded cursor-pointer"
          />
        </div>
      </div>
    </div>
  );
};
