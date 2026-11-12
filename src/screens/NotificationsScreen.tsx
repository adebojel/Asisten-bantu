import React, { useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { Bell, ArrowLeft, Volume2, MessageSquare, PlusCircle } from 'lucide-react';
import { NotificationItem } from '../types';

export const NotificationsScreen: React.FC = () => {
  const { setCurrentScreen, notifications, addNotification, markNotificationRead, say } = useKobra();

  const [simSender, setSimSender] = useState('Dokter Rina');
  const [simApp, setSimApp] = useState('WhatsApp');
  const [simMsg, setSimMsg] = useState('Jadwal kontrol mata Anda besok jam 9 pagi.');

  const handleReadSummary = () => {
    const unread = notifications.filter(n => !n.read);
    if (unread.length === 0) {
      say('Tidak ada notifikasi baru.');
      return;
    }
    const textSum = unread.map(n => `Dari ${n.sender} di ${n.app}: ${n.message}`).join('. ');
    say(`Ada ${unread.length} notifikasi baru. ${textSum}`);
  };

  const handleSimulateNew = () => {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
    const newNotif: NotificationItem = {
      id: Date.now().toString(),
      sender: simSender,
      app: simApp,
      title: 'Pesan Masuk',
      message: simMsg,
      time: timeStr,
      read: false,
    };
    addNotification(newNotif);
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
          <Bell className="w-6 h-6 text-blue-400" />
          Notifikasi & SMS
        </h2>

        <button
          onClick={handleReadSummary}
          className="px-4 py-2.5 bg-yellow-400 hover:bg-yellow-300 text-black font-black rounded-xl flex items-center gap-2 text-sm focus-accessible shadow-md active:scale-95 transition-transform"
        >
          <Volume2 className="w-4 h-4" />
          Bacakan Semua
        </button>
      </div>

      {/* Notifications List */}
      <div className="space-y-3">
        {notifications.length === 0 ? (
          <div className="p-8 text-center bg-neutral-900 rounded-3xl border-2 border-neutral-800 text-neutral-400">
            Tidak ada notifikasi yang tersimpan.
          </div>
        ) : (
          notifications.map(n => (
            <div
              key={n.id}
              onClick={() => {
                markNotificationRead(n.id);
                say(`Dari ${n.sender} di ${n.app}: ${n.message}`);
              }}
              className={`p-5 rounded-2xl border-2 text-left cursor-pointer transition-colors focus-accessible ${
                n.read
                  ? 'bg-neutral-950 border-neutral-800 text-neutral-400'
                  : 'bg-neutral-900 border-blue-500/80 text-white shadow-lg'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-black uppercase text-blue-400 tracking-wider">
                  {n.app} • {n.sender}
                </span>
                <span className="text-xs text-neutral-500 font-mono">{n.time}</span>
              </div>
              <p className="text-base font-bold leading-snug">{n.message}</p>
            </div>
          ))
        )}
      </div>

      {/* Simulated Notification Sender for Testing */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <div className="flex items-center gap-2 font-black text-white text-base uppercase">
          <PlusCircle className="w-5 h-5 text-yellow-400" />
          Simulasi Pesan Masuk
        </div>
        <p className="text-xs text-neutral-400">
          Uji coba fitur pembacaan notifikasi suara otomatis saat pesan baru tiba.
        </p>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-bold text-neutral-400 mb-1">Pengirim</label>
            <input
              type="text"
              value={simSender}
              onChange={e => setSimSender(e.target.value)}
              className="w-full bg-black border-2 border-neutral-700 rounded-xl p-2.5 text-white font-bold text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-neutral-400 mb-1">Aplikasi</label>
            <input
              type="text"
              value={simApp}
              onChange={e => setSimApp(e.target.value)}
              className="w-full bg-black border-2 border-neutral-700 rounded-xl p-2.5 text-white font-bold text-sm"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs font-bold text-neutral-400 mb-1">Isi Pesan</label>
          <input
            type="text"
            value={simMsg}
            onChange={e => setSimMsg(e.target.value)}
            className="w-full bg-black border-2 border-neutral-700 rounded-xl p-2.5 text-white font-bold text-sm"
          />
        </div>

        <button
          onClick={handleSimulateNew}
          className="w-full py-3 bg-neutral-800 hover:bg-neutral-700 active:scale-95 text-white border border-neutral-700 rounded-xl font-bold text-sm flex items-center justify-center gap-2 focus-accessible"
        >
          <MessageSquare className="w-4 h-4 text-emerald-400" />
          Kirim Notifikasi Simulasi
        </button>
      </div>
    </div>
  );
};
