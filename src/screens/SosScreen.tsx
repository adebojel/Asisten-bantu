import React, { useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { HeartPulse, Save, ArrowLeft, AlertOctagon, Phone, UserCheck, ShieldAlert } from 'lucide-react';

export const SosScreen: React.FC = () => {
  const { setCurrentScreen, sosContacts, updateSosContact, triggerSos, say } = useKobra();

  const [contact1, setContact1] = useState(sosContacts[0] || { name: '', phone: '', relation: 'Keluarga' });
  const [contact2, setContact2] = useState(sosContacts[1] || { name: '', phone: '', relation: 'Kerabat' });

  const handleSave = () => {
    updateSosContact(0, contact1);
    updateSosContact(1, contact2);
    say('Kontak darurat SOS berhasil disimpan.');
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
          <HeartPulse className="w-6 h-6 text-red-500" />
          Kontak Darurat SOS
        </h2>

        <button
          onClick={handleSave}
          className="px-4 py-2.5 bg-yellow-400 hover:bg-yellow-300 text-black font-black rounded-xl flex items-center gap-2 text-sm focus-accessible shadow-md active:scale-95 transition-transform"
        >
          <Save className="w-4 h-4" />
          Simpan
        </button>
      </div>

      <div className="p-4 bg-red-950/40 border-2 border-red-500/60 rounded-3xl flex items-start gap-3">
        <ShieldAlert className="w-6 h-6 text-red-400 shrink-0 mt-0.5" />
        <p className="text-sm text-red-200 leading-relaxed font-medium">
          Saat Anda mengucap <strong className="text-yellow-400">"Kobra tolong"</strong> atau menekan tombol SOS,
          Kobra akan meminta konfirmasi suara lalu mengirim pesan WhatsApp/SMS darurat berisi koordinat lokasi GPS langsung Anda.
        </p>
      </div>

      {/* Contact 1 */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <div className="flex items-center gap-2 font-black text-white text-base uppercase">
          <UserCheck className="w-5 h-5 text-emerald-400" />
          Kontak Darurat Utama
        </div>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5">Nama Kontak</label>
          <input
            type="text"
            value={contact1.name}
            onChange={e => setContact1({ ...contact1, name: e.target.value })}
            placeholder="Misal: Ibu, Ayah, Suami"
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-bold text-base focus-accessible"
          />
        </div>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5 flex items-center gap-2">
            <Phone className="w-4 h-4 text-emerald-400" />
            Nomor Telepon / WhatsApp
          </label>
          <input
            type="tel"
            value={contact1.phone}
            onChange={e => setContact1({ ...contact1, phone: e.target.value })}
            placeholder="081234567890"
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-mono font-bold text-base focus-accessible"
          />
        </div>
      </div>

      {/* Contact 2 */}
      <div className="p-6 bg-neutral-900 border-2 border-neutral-800 rounded-3xl space-y-4">
        <div className="flex items-center gap-2 font-black text-white text-base uppercase">
          <UserCheck className="w-5 h-5 text-blue-400" />
          Kontak Cadangan (Opsional)
        </div>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5">Nama Kontak</label>
          <input
            type="text"
            value={contact2.name}
            onChange={e => setContact2({ ...contact2, name: e.target.value })}
            placeholder="Misal: Kakak, Sahabat, Relawan"
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-bold text-base focus-accessible"
          />
        </div>

        <div>
          <label className="block text-sm font-bold text-neutral-300 mb-1.5 flex items-center gap-2">
            <Phone className="w-4 h-4 text-blue-400" />
            Nomor Telepon / WhatsApp
          </label>
          <input
            type="tel"
            value={contact2.phone}
            onChange={e => setContact2({ ...contact2, phone: e.target.value })}
            placeholder="089876543210"
            className="w-full bg-black border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl p-3.5 text-white font-mono font-bold text-base focus-accessible"
          />
        </div>
      </div>

      {/* Emergency Trigger Test */}
      <button
        onClick={triggerSos}
        className="w-full py-5 bg-red-600 hover:bg-red-500 active:scale-95 text-white border-4 border-red-400 rounded-3xl font-black text-xl tracking-wider shadow-2xl flex items-center justify-center gap-3 transition-transform focus-accessible"
      >
        <AlertOctagon className="w-7 h-7 stroke-[2.5]" />
        UJI SINYAL SOS SEKARANG
      </button>
    </div>
  );
};
