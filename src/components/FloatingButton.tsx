import React, { useState, useRef, useEffect } from 'react';
import { useKobra } from '../context/KobraContext';

export const FloatingButton: React.FC = () => {
  const { indicatorState, indicatorEmoji, toggleMic, startListening, isListening, say } = useKobra();
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 20, y: 120 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStart = useRef<{ x: number; y: number; posX: number; posY: number }>({ x: 0, y: 0, posX: 0, posY: 0 });
  const longPressTimer = useRef<any>(null);
  const didLongPress = useRef(false);

  const getBgColor = () => {
    switch (indicatorState) {
      case 'listen':
        return 'bg-yellow-400 text-black border-yellow-200 animate-listen-pulse';
      case 'speak':
        return 'bg-blue-600 text-white border-blue-400 animate-voice-pulse';
      case 'off':
        return 'bg-neutral-600 text-neutral-300 border-neutral-500';
      case 'error':
        return 'bg-red-600 text-white border-red-400';
      case 'idle':
      default:
        return 'bg-emerald-500 text-black border-emerald-300 shadow-emerald-500/30';
    }
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(false);
    didLongPress.current = false;
    dragStart.current = {
      x: e.clientX,
      y: e.clientY,
      posX: position.x,
      posY: position.y,
    };

    longPressTimer.current = setTimeout(() => {
      didLongPress.current = true;
      toggleMic();
    }, 800);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    const dx = e.clientX - dragStart.current.x;
    const dy = e.clientY - dragStart.current.y;
    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
      setIsDragging(true);
      if (longPressTimer.current) clearTimeout(longPressTimer.current);

      setPosition({
        x: Math.max(10, Math.min(window.innerWidth - 80, dragStart.current.posX - dx)),
        y: Math.max(10, Math.min(window.innerHeight - 80, dragStart.current.posY + dy)),
      });
    }
  };

  const handlePointerUp = () => {
    if (longPressTimer.current) clearTimeout(longPressTimer.current);
    if (!isDragging && !didLongPress.current) {
      say('Ya, saya dengarkan.');
      startListening();
    }
    setIsDragging(false);
  };

  return (
    <div
      style={{
        position: 'fixed',
        right: `${position.x}px`,
        top: `${position.y}px`,
        touchAction: 'none',
        zIndex: 9999,
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      role="button"
      tabIndex={0}
      aria-label="Tombol Kobra mengambang. Ketuk untuk bicara, tahan untuk matikan mikrofon."
      className={`w-16 h-16 rounded-full border-4 shadow-2xl flex items-center justify-center cursor-pointer select-none transition-transform active:scale-95 text-2xl font-bold ${getBgColor()}`}
    >
      <span>{indicatorEmoji}</span>
    </div>
  );
};
