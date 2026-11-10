import React from 'react';
import { useKobra } from '../context/KobraContext';
import { AlertTriangle, Check, X, PhoneCall, Send, ShieldAlert } from 'lucide-react';

export const ConfirmationModal: React.FC = () => {
  const { pending, confirmPending } = useKobra();

  if (!pending) return null;

  const renderContent = () => {
    switch (pending.kind) {
      case 'sos':
        return {
          icon: <ShieldAlert className="w-16 h-16 text-red-500 animate-bounce" />,
          title: 'KONFIRMASI SOS DARURAT',
          desc: 'Kobra akan membunyikan sirine dan mengirim pesan darurat dengan koordinat GPS ke kontak keluarga Anda.',
          prompt: 'Katakan "IYA" untuk mengirim, atau "BATAL" untuk membatalkan.',
          isDangerous: true,
        };
      case 'call':
        return {
          icon: <PhoneCall className="w-16 h-16 text-emerald-400" />,
          title: `PANGGIL TELEPON KE ${pending.a}?`,
          desc: `Nomor tujuan: ${pending.b || 'Kontak tersimpan'}`,
          prompt: 'Katakan "IYA" untuk memanggil, atau "BATAL" untuk berhenti.',
          isDangerous: false,
        };
      case 'msg_confirm':
        return {
          icon: <Send className="w-16 h-16 text-emerald-400" />,
          title: `KIRIM WHATSAPP KE ${pending.a}?`,
          desc: `Isi pesan: "${pending.b}"`,
          prompt: 'Katakan "IYA" untuk mengirim pesan sekarang.',
          isDangerous: false,
        };
      case 'app_pick':
        return {
          icon: <AlertTriangle className="w-16 h-16 text-yellow-400" />,
          title: 'PILIH APLIKASI',
          desc: pending.candidates?.map((c, i) => `${i + 1}. ${c.label}`).join(' • ') || 'Sebutkan nomor aplikasi.',
          prompt: 'Katakan "Satu", "Dua", atau nomor yang Anda inginkan.',
          isDangerous: false,
        };
      default:
        return {
          icon: <AlertTriangle className="w-16 h-16 text-yellow-400" />,
          title: 'KONFIRMASI PERINTAH',
          desc: pending.a || 'Perintah ini butuh persetujuan Anda.',
          prompt: 'Katakan "IYA" atau "BATAL".',
          isDangerous: false,
        };
    }
  };

  const c = renderContent();

  return (
    <div className="fixed inset-0 bg-black/90 z-50 flex items-center justify-center p-4 backdrop-blur-sm animate-in fade-in duration-200">
      <div className={`w-full max-w-lg p-6 rounded-3xl border-4 ${c.isDangerous ? 'border-red-500 bg-red-950/40' : 'border-yellow-400 bg-neutral-900'} text-center shadow-2xl`}>
        <div className="flex justify-center mb-4">{c.icon}</div>
        <h2 className="text-2xl font-black tracking-wide mb-3 text-white uppercase">{c.title}</h2>
        <p className="text-lg text-neutral-200 mb-4 leading-relaxed font-medium">{c.desc}</p>
        <div className="bg-neutral-800/80 p-4 rounded-2xl mb-6 border border-neutral-700">
          <p className="text-yellow-300 font-bold text-lg">{c.prompt}</p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <button
            onClick={() => confirmPending(true)}
            className="flex items-center justify-center gap-2 py-4 px-6 rounded-2xl bg-emerald-500 hover:bg-emerald-400 active:scale-95 text-black font-black text-xl shadow-lg transition-transform focus-accessible"
            aria-label="Iya, konfirmasi"
          >
            <Check className="w-7 h-7 stroke-[3]" />
            IYA
          </button>
          <button
            onClick={() => confirmPending(false)}
            className="flex items-center justify-center gap-2 py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 active:scale-95 text-white font-black text-xl shadow-lg transition-transform focus-accessible"
            aria-label="Batal"
          >
            <X className="w-7 h-7 stroke-[3]" />
            BATAL
          </button>
        </div>
      </div>
    </div>
  );
};
