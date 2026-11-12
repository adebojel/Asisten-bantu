import React, { useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { Send, Mic, ArrowLeft, Volume2, Trash2, Bot, User } from 'lucide-react';

export const AiChatScreen: React.FC = () => {
  const { setCurrentScreen, chatMessages, sendChatMessage, clearChat, say, startListening } = useKobra();
  const [inputText, setInputText] = useState('');

  const handleSend = () => {
    if (!inputText.trim()) return;
    const msg = inputText.trim();
    setInputText('');
    sendChatMessage(msg);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSend();
  };

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] pb-16">
      {/* Top Header */}
      <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
        <button
          onClick={() => {
            setCurrentScreen('launcher');
            say('Kembali ke layar utama.');
          }}
          className="p-3 bg-neutral-900 border-2 border-neutral-700 rounded-2xl flex items-center gap-2 font-bold text-white focus-accessible"
        >
          <ArrowLeft className="w-5 h-5" />
          Kembali
        </button>

        <h2 className="text-lg font-black text-white uppercase tracking-wider flex items-center gap-2">
          <Bot className="w-6 h-6 text-purple-400" />
          Teman Ngobrol
        </h2>

        <button
          onClick={() => {
            clearChat();
            say('Percakapan dihapus.');
          }}
          className="p-3 bg-neutral-900 hover:bg-neutral-800 border-2 border-neutral-700 rounded-2xl text-neutral-400 hover:text-red-400 focus-accessible"
          aria-label="Bersihkan chat"
        >
          <Trash2 className="w-5 h-5" />
        </button>
      </div>

      {/* Messages Feed */}
      <div className="flex-1 overflow-y-auto py-4 space-y-4 pr-1">
        {chatMessages.map(m => {
          const isKobra = m.sender === 'kobra';
          return (
            <div
              key={m.id}
              className={`flex items-start gap-3 ${isKobra ? 'justify-start' : 'justify-end'}`}
            >
              {isKobra && (
                <div className="w-10 h-10 rounded-2xl bg-purple-950 border-2 border-purple-600 flex items-center justify-center shrink-0">
                  <span className="text-xl">🐍</span>
                </div>
              )}

              <div
                className={`max-w-[85%] p-4 rounded-3xl border-2 text-base font-medium shadow-md ${
                  isKobra
                    ? 'bg-neutral-900 border-neutral-700 text-white rounded-tl-sm'
                    : 'bg-yellow-400 border-yellow-300 text-black rounded-tr-sm font-bold'
                }`}
              >
                <p className="leading-relaxed whitespace-pre-wrap">{m.text}</p>
                {isKobra && (
                  <div className="mt-2 flex items-center justify-between pt-2 border-t border-neutral-800 text-xs text-neutral-400 font-bold">
                    <span>
                      {m.source === 'offline_brain'
                        ? '⚡ Otak Offline'
                        : m.source === 'n8n'
                        ? '🔄 Webhook n8n'
                        : m.source === 'openrouter'
                        ? '🌐 OpenRouter AI'
                        : 'Sistem Kobra'}
                    </span>
                    <button
                      onClick={() => say(m.text)}
                      className="p-1 hover:text-emerald-400 flex items-center gap-1 focus-accessible"
                      aria-label="Ulangi ucapan ini"
                    >
                      <Volume2 className="w-4 h-4 text-emerald-400" />
                      Bacakan
                    </button>
                  </div>
                )}
              </div>

              {!isKobra && (
                <div className="w-10 h-10 rounded-2xl bg-yellow-500/20 border-2 border-yellow-400 flex items-center justify-center shrink-0">
                  <User className="w-5 h-5 text-yellow-300" />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Bottom Speech & Text Input */}
      <div className="pt-3 border-t border-neutral-800 flex items-center gap-2">
        <button
          onClick={() => {
            say('Ya, saya dengarkan.');
            startListening();
          }}
          className="p-4 bg-yellow-400 hover:bg-yellow-300 active:scale-95 text-black rounded-2xl font-black flex items-center justify-center border-2 border-yellow-200 focus-accessible shadow-lg"
          aria-label="Bicara sekarang"
        >
          <Mic className="w-6 h-6" />
        </button>

        <input
          type="text"
          value={inputText}
          onChange={e => setInputText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Tanya atau ceritakan apa saja..."
          className="flex-1 bg-neutral-900 border-2 border-neutral-700 focus:border-yellow-400 rounded-2xl px-4 py-3.5 text-white font-bold text-base focus-accessible placeholder:text-neutral-500"
        />

        <button
          onClick={handleSend}
          className="p-4 bg-emerald-500 hover:bg-emerald-400 active:scale-95 text-black rounded-2xl font-black flex items-center justify-center border-2 border-emerald-300 focus-accessible shadow-lg"
          aria-label="Kirim pesan"
        >
          <Send className="w-6 h-6" />
        </button>
      </div>
    </div>
  );
};
