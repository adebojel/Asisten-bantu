import React, { useState, useEffect, useRef } from 'react';
import { useKobra } from '../context/KobraContext';
import { Compass, Play, Square, AlertTriangle, ArrowLeft, Volume2, Shield } from 'lucide-react';
import { deviceSensors } from '../services/deviceSensors';

export const NavigationScreen: React.FC = () => {
  const { setCurrentScreen, say } = useKobra();
  const [isActive, setIsActive] = useState<boolean>(false);
  const [distanceCm, setDistanceCm] = useState<number>(140);
  const [compassHeading, setCompassHeading] = useState<number>(0);
  const timerRef = useRef<any>(null);

  // Compass orientation
  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      if (e.alpha !== null) {
        setCompassHeading(Math.round(e.alpha));
      }
    };

    if (window.DeviceOrientationEvent) {
      window.addEventListener('deviceorientation', handleOrientation);
    }
    return () => {
      window.removeEventListener('deviceorientation', handleOrientation);
    };
  }, []);

  const getDirectionName = (deg: number) => {
    if (deg >= 337.5 || deg < 22.5) return 'Utara';
    if (deg >= 22.5 && deg < 67.5) return 'Timur Laut';
    if (deg >= 67.5 && deg < 112.5) return 'Timur';
    if (deg >= 112.5 && deg < 157.5) return 'Tenggara';
    if (deg >= 157.5 && deg < 202.5) return 'Selatan';
    if (deg >= 202.5 && deg < 247.5) return 'Barat Daya';
    if (deg >= 247.5 && deg < 292.5) return 'Barat';
    return 'Barat Laut';
  };

  const startNavigation = () => {
    setIsActive(true);
    say('Mode tongkat aktif. Arahkan HP tegak lurus ke depan.');
  };

  const stopNavigation = () => {
    setIsActive(false);
    if (timerRef.current) clearInterval(timerRef.current);
    say('Mode tongkat dihentikan.');
  };

  useEffect(() => {
    if (isActive) {
      timerRef.current = setInterval(() => {
        // Dynamic simulated sensor distance
        setDistanceCm(prev => {
          const delta = (Math.random() - 0.5) * 40;
          const next = Math.max(25, Math.min(220, Math.round(prev + delta)));

          // Trigger audio beeps
          deviceSensors.beepDistance(next);

          if (next < 50 && prev >= 50) {
            say('Awas! Halangan dekat di depan!');
          }
          return next;
        });
      }, 700);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isActive, say]);

  const isHazard = distanceCm < 60;
  const isWarning = distanceCm >= 60 && distanceCm < 100;

  const getStatusColor = () => {
    if (isHazard) return 'border-red-500 bg-red-950/30 text-red-400';
    if (isWarning) return 'border-yellow-400 bg-yellow-950/30 text-yellow-300';
    return 'border-emerald-500 bg-emerald-950/30 text-emerald-400';
  };

  return (
    <div className="space-y-6 pb-24">
      {/* Top Bar */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => {
            stopNavigation();
            setCurrentScreen('launcher');
          }}
          className="p-3 bg-neutral-900 border-2 border-neutral-700 rounded-2xl flex items-center gap-2 font-bold text-white focus-accessible"
        >
          <ArrowLeft className="w-5 h-5" />
          Kembali
        </button>

        <div className="flex items-center gap-2 bg-neutral-900 px-4 py-2 rounded-2xl border-2 border-neutral-700 text-sm font-bold">
          <Compass className="w-5 h-5 text-yellow-400" />
          <span>{getDirectionName(compassHeading)} ({compassHeading}°)</span>
        </div>
      </div>

      {/* Main Radar / Proximity Display */}
      <div className={`p-8 rounded-3xl border-4 text-center space-y-4 shadow-2xl transition-colors ${getStatusColor()}`}>
        <div className="flex justify-center">
          {isHazard ? (
            <AlertTriangle className="w-24 h-24 text-red-500 animate-bounce stroke-[2.5]" />
          ) : (
            <Shield className="w-24 h-24 text-emerald-400 stroke-[2.5]" />
          )}
        </div>

        <div>
          <div className="text-6xl font-black tracking-wider text-white">
            {distanceCm} <span className="text-2xl text-neutral-400">cm</span>
          </div>
          <div className="text-xl font-bold uppercase tracking-widest mt-2">
            {isHazard ? 'HALANGAN DEKAT - BERHENTI' : isWarning ? 'PERINGATAN HALANGAN' : 'JALUR DEPAN AMAN'}
          </div>
        </div>

        <p className="text-sm text-neutral-300 max-w-sm mx-auto">
          {isActive
            ? 'Bunyi bip audio dan getaran akan semakin rapat saat halangan mendekat.'
            : 'Ketuk tombol Mulai di bawah untuk mengaktifkan pemindaian tongkat.'}
        </p>
      </div>

      {/* Control Start/Stop Button */}
      <button
        onClick={isActive ? stopNavigation : startNavigation}
        className={`w-full py-6 rounded-3xl border-4 font-black text-2xl tracking-wider flex items-center justify-center gap-3 transition-transform active:scale-95 focus-accessible shadow-2xl ${
          isActive
            ? 'bg-red-600 hover:bg-red-500 text-white border-red-400'
            : 'bg-emerald-500 hover:bg-emerald-400 text-black border-emerald-300'
        }`}
        aria-label={isActive ? 'Hentikan mode tongkat' : 'Mulai mode tongkat'}
      >
        {isActive ? (
          <>
            <Square className="w-8 h-8 fill-current" />
            BERHENTI (STOP)
          </>
        ) : (
          <>
            <Play className="w-8 h-8 fill-current" />
            MULAI MODE TONGKAT
          </>
        )}
      </button>

      {/* Read Status Button */}
      <button
        onClick={() => {
          say(`Jarak halangan di depan sekitar ${distanceCm} sentimeter. Arah kompas menghadap ${getDirectionName(compassHeading)}.`);
        }}
        className="w-full py-4 bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border-2 border-neutral-700 rounded-2xl flex items-center justify-center gap-2 font-bold text-base focus-accessible"
      >
        <Volume2 className="w-5 h-5 text-emerald-400" />
        Bacakan Jarak & Kompas
      </button>
    </div>
  );
};
