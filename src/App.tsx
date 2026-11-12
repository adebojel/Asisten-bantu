import React from 'react';
import { useKobra } from './context/KobraContext';
import { FloatingButton } from './components/FloatingButton';
import { ConfirmationModal } from './components/ConfirmationModal';
import { HomeScreen } from './screens/HomeScreen';
import { LauncherScreen } from './screens/LauncherScreen';
import { VisionScreen } from './screens/VisionScreen';
import { NavigationScreen } from './screens/NavigationScreen';
import { AiChatScreen } from './screens/AiChatScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { SosScreen } from './screens/SosScreen';
import { NotificationsScreen } from './screens/NotificationsScreen';
import {
  Home,
  LayoutGrid,
  Eye,
  Compass,
  MessageSquare,
  Mic,
  MicOff,
  Bell,
  Volume2,
} from 'lucide-react';

export const AppContent: React.FC = () => {
  const {
    currentScreen,
    setCurrentScreen,
    micOn,
    toggleMic,
    indicatorState,
    indicatorEmoji,
    transcript,
    say,
    notifications,
  } = useKobra();

  const unreadCount = notifications.filter(n => !n.read).length;

  const renderScreen = () => {
    switch (currentScreen) {
      case 'launcher':
        return <LauncherScreen />;
      case 'vision':
        return <VisionScreen />;
      case 'navigation':
        return <NavigationScreen />;
      case 'aichat':
        return <AiChatScreen />;
      case 'settings':
        return <SettingsScreen />;
      case 'sos':
        return <SosScreen />;
      case 'notifications':
        return <NotificationsScreen />;
      case 'home':
      default:
        return <HomeScreen />;
    }
  };

  return (
    <div className="min-h-screen bg-black text-white flex flex-col max-w-xl mx-auto border-x border-neutral-900 shadow-2xl relative">
      {/* Top Accessibility Bar */}
      <header className="sticky top-0 z-40 bg-black/95 backdrop-blur-md px-4 py-3 border-b-2 border-neutral-800 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setCurrentScreen('home')}
            className="flex items-center gap-2 focus-accessible rounded-xl p-1"
          >
            <span className="text-2xl">{indicatorEmoji}</span>
            <span className="font-black text-lg tracking-wider text-white">KOBRA OS</span>
          </button>
          <span className="text-xs px-2 py-0.5 rounded-full font-bold uppercase bg-neutral-800 text-neutral-300 border border-neutral-700">
            {indicatorState === 'listen' ? 'Mendengarkan' : indicatorState === 'speak' ? 'Bicara' : 'Siaga'}
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setCurrentScreen('notifications')}
            className="p-2.5 rounded-xl bg-neutral-900 border border-neutral-800 text-neutral-200 hover:text-white relative focus-accessible"
            aria-label={`Notifikasi. Ada ${unreadCount} belum dibaca.`}
          >
            <Bell className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-red-600 text-white text-xs font-black flex items-center justify-center">
                {unreadCount}
              </span>
            )}
          </button>

          <button
            onClick={toggleMic}
            className={`p-2.5 rounded-xl border flex items-center justify-center focus-accessible ${
              micOn
                ? 'bg-emerald-950/80 border-emerald-500 text-emerald-400'
                : 'bg-neutral-900 border-neutral-700 text-neutral-400'
            }`}
            aria-label={micOn ? 'Mikrofon aktif, ketuk untuk mematikan' : 'Mikrofon mati, ketuk untuk menyalakan'}
          >
            {micOn ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
          </button>
        </div>
      </header>

      {/* Spoken Transcript Live Bar */}
      {transcript && (
        <div className="bg-neutral-900 px-4 py-2 border-b border-neutral-800 flex items-center justify-between text-xs text-neutral-300 font-medium">
          <div className="flex items-center gap-2 truncate">
            <Volume2 className="w-4 h-4 text-yellow-400 shrink-0" />
            <span className="truncate">"{transcript}"</span>
          </div>
          <button
            onClick={() => say(transcript)}
            className="text-yellow-400 hover:underline shrink-0 text-xs font-bold pl-2"
          >
            Ulangi
          </button>
        </div>
      )}

      {/* Main Screen Body */}
      <main className="flex-1 p-4 overflow-y-auto">
        {renderScreen()}
      </main>

      {/* Floating Action Button */}
      <FloatingButton />

      {/* Voice Confirmation Modal */}
      <ConfirmationModal />

      {/* Bottom Nav Bar */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 bg-black/95 backdrop-blur-md border-t-2 border-neutral-800 py-2 px-3 max-w-xl mx-auto flex items-center justify-around">
        <button
          onClick={() => {
            setCurrentScreen('home');
            say('Ke Beranda.');
          }}
          className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl focus-accessible ${
            currentScreen === 'home' ? 'text-yellow-400 font-black' : 'text-neutral-400 hover:text-white'
          }`}
          aria-label="Beranda"
        >
          <Home className="w-6 h-6" />
          <span className="text-[11px] font-bold">Beranda</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('launcher');
            say('Ke Layar Utama Kobra.');
          }}
          className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl focus-accessible ${
            currentScreen === 'launcher' ? 'text-yellow-400 font-black' : 'text-neutral-400 hover:text-white'
          }`}
          aria-label="Layar Utama"
        >
          <LayoutGrid className="w-6 h-6" />
          <span className="text-[11px] font-bold">Layar Utama</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('vision');
            say('Ke Mata Kedua.');
          }}
          className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl focus-accessible ${
            currentScreen === 'vision' ? 'text-yellow-400 font-black' : 'text-neutral-400 hover:text-white'
          }`}
          aria-label="Mata Kedua"
        >
          <Eye className="w-6 h-6" />
          <span className="text-[11px] font-bold">Mata</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('navigation');
            say('Ke Mode Tongkat.');
          }}
          className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl focus-accessible ${
            currentScreen === 'navigation' ? 'text-yellow-400 font-black' : 'text-neutral-400 hover:text-white'
          }`}
          aria-label="Mode Tongkat"
        >
          <Compass className="w-6 h-6" />
          <span className="text-[11px] font-bold">Tongkat</span>
        </button>

        <button
          onClick={() => {
            setCurrentScreen('aichat');
            say('Ke Teman Ngobrol AI.');
          }}
          className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl focus-accessible ${
            currentScreen === 'aichat' ? 'text-yellow-400 font-black' : 'text-neutral-400 hover:text-white'
          }`}
          aria-label="Teman Ngobrol"
        >
          <MessageSquare className="w-6 h-6" />
          <span className="text-[11px] font-bold">Ngobrol</span>
        </button>
      </nav>
    </div>
  );
};

export default function App() {
  return <AppContent />;
}
