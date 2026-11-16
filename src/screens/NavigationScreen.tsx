import React, { useEffect, useState } from 'react';
import { useKobra } from '../context/KobraContext';
import { Compass, Play, Square, AlertTriangle, ArrowLeft, Volume2 } from 'lucide-react';

export const NavigationScreen: React.FC = () => {
  const { setCurrentScreen, say } = useKobra();
  const [isActive, setIsActive] = useState<boolean>(false);
  const [compassHeading, setCompassHeading] = useState<number | null>(null);

  // Device orientation is a compass signal, never a distance or obstacle sensor.
  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      if (typeof e.alpha === 'number' && Number.isFinite(e.alpha)) {
        setCompassHeading(Math.round(((e.alpha % 360) + 360) % 360));
      }
    };
    if (typeof window !== 'undefined' && 'DeviceOrientationEvent' in window) {
      window.addEventListener('deviceorientation', handleOrientation);
    }
    return () => window.removeEventListener('deviceorientation', handleOrientation);
  }, []);

  const getDirectionName = (deg: number) => {
    if (deg >= 337.5 || deg < 22.5) return 'Utara';
    if (deg < 67.5) return 'Timur Laut';
    if (deg < 112.5) return 'Timur';
    if (deg < 157.5) return 'Tenggara';
    if (deg < 202.5) return 'Selatan';
    if (deg < 247.5) return 'Barat Daya';
    if (deg < 292.5) return 'Barat';
    return 'Barat Laut';
  };
  const stopNavigation = () => {
    setIsActive(false);
    say('Mode kompas dihentikan.');
  };
  const directionText = compassHeading === null
    ? 'Arah kompas belum tersedia'
    : `${getDirectionName(compassHeading)} (${compassHeading} derajat)`;

  return (
    <div className="space-y-6 pb-24">
      <div className="flex items-center justify-between">
        <button onClick={() => { stopNavigation(); setCurrentScreen('launcher'); }}
          className="p-3 bg-neutral-900 border-2 border-neutral-700 rounded-2xl flex items-center gap-2 font-bold text-white focus-accessible">
          <ArrowLeft className="w-5 h-5" />Kembali
        </button>
        <div className="flex items-center gap-2 bg-neutral-900 px-4 py-2 rounded-2xl border-2 border-neutral-700 text-sm font-bold">
          <Compass className="w-5 h-5 text-yellow-400" /><span>{directionText}</span>
        </div>
      </div>
      <div className="p-8 rounded-3xl border-4 border-amber-500 bg-amber-950/20 text-center space-y-4">
        <div className="flex justify-center"><AlertTriangle className="w-24 h-24 text-amber-400 stroke-[2.5]" /></div>
        <div>
          <div className="text-5xl font-black tracking-wider text-white">—</div>
          <div className="text-xl font-bold uppercase tracking-widest mt-2 text-amber-300">JARAK HALANGAN TIDAK TERSEDIA</div>
        </div>
        <p className="text-sm text-neutral-200 max-w-sm mx-auto">
          {isActive
            ? 'Kompas hanya menunjukkan orientasi. Sensor jarak belum tersedia dan jalur tidak dapat dinyatakan aman.'
            : 'Mode ini belum memiliki sensor jarak terverifikasi. Jangan gunakan sebagai pengganti tongkat putih, anjing pemandu, atau pendamping.'}
        </p>
      </div>
      <button onClick={() => {
        if (isActive) stopNavigation();
        else {
          setIsActive(true);
          say('Kompas aktif. Pengukuran jarak dan deteksi halangan belum tersedia. Tetap gunakan tongkat putih atau pendamping.');
        }
      }}
        className={`w-full py-6 rounded-3xl border-4 font-black text-2xl tracking-wider flex items-center justify-center gap-3 transition-transform active:scale-95 focus-accessible shadow-2xl ${
          isActive ? 'bg-red-600 hover:bg-red-500 text-white border-red-400' : 'bg-amber-500 hover:bg-amber-400 text-black border-amber-300'
        }`}
        aria-label={isActive ? 'Hentikan mode kompas' : 'Mulai mode kompas'}>
        {isActive ? <><Square className="w-8 h-8 fill-current" />BERHENTI (STOP)</> : <><Play className="w-8 h-8 fill-current" />MULAI MODE KOMPAS</>}
      </button>
      <button onClick={() => say(`Arah kompas: ${directionText}. Pengukuran jarak halangan tidak tersedia. Jangan menganggap jalur aman berdasarkan fitur ini.`)}
        className="w-full py-4 bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border-2 border-neutral-700 rounded-2xl flex items-center justify-center gap-2 font-bold text-base focus-accessible">
        <Volume2 className="w-5 h-5 text-amber-400" />Bacakan Kompas & Status Sensor
      </button>
    </div>
  );
};
