class DeviceSensors {
  private static instance: DeviceSensors;
  private audioCtx: AudioContext | null = null;
  private torchStream: MediaStream | null = null;
  private sirenOsc: OscillatorNode | null = null;
  private sirenGain: GainNode | null = null;

  private constructor() {}

  public static getInstance(): DeviceSensors {
    if (!DeviceSensors.instance) {
      DeviceSensors.instance = new DeviceSensors();
    }
    return DeviceSensors.instance;
  }

  private initAudio() {
    if (!this.audioCtx && typeof window !== 'undefined') {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioContextClass) {
        this.audioCtx = new AudioContextClass();
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  public beepDistance(distanceCm: number) {
    try {
      this.initAudio();
      if (!this.audioCtx) return;

      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();

      const clamped = Math.max(20, Math.min(200, distanceCm));
      const freq = 1300 - ((clamped - 20) / 180) * 1000;

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, this.audioCtx.currentTime);

      gain.gain.setValueAtTime(0.15, this.audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + 0.12);

      osc.connect(gain);
      gain.connect(this.audioCtx.destination);

      osc.start();
      osc.stop(this.audioCtx.currentTime + 0.12);

      if (distanceCm < 60 && typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        navigator.vibrate(80);
      }
    } catch (e) {
      console.warn('Audio beep error', e);
    }
  }

  public startEmergencySiren() {
    try {
      this.initAudio();
      if (!this.audioCtx) return;
      this.stopEmergencySiren();

      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();
      osc.type = 'sawtooth';

      const now = this.audioCtx.currentTime;
      osc.frequency.setValueAtTime(600, now);
      for (let i = 0; i < 10; i++) {
        osc.frequency.linearRampToValueAtTime(950, now + (i * 0.8) + 0.4);
        osc.frequency.linearRampToValueAtTime(550, now + (i * 0.8) + 0.8);
      }

      gain.gain.setValueAtTime(0.25, now);
      osc.connect(gain);
      gain.connect(this.audioCtx.destination);

      osc.start();
      this.sirenOsc = osc;
      this.sirenGain = gain;

      if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        navigator.vibrate([300, 150, 300, 150, 500]);
      }
    } catch (e) {
      console.warn('Emergency siren error', e);
    }
  }

  public stopEmergencySiren() {
    if (this.sirenOsc) {
      try {
        this.sirenOsc.stop();
        this.sirenOsc.disconnect();
      } catch (ignored) {}
      this.sirenOsc = null;
    }
    if (this.sirenGain) {
      try {
        this.sirenGain.disconnect();
      } catch (ignored) {}
      this.sirenGain = null;
    }
  }

  public vibrate(pattern: number | number[] = 100) {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate(pattern);
    }
  }

  public async getBatteryLevel(): Promise<number> {
    try {
      if (typeof navigator !== 'undefined' && 'getBattery' in navigator) {
        const battery: any = await (navigator as any).getBattery();
        return Math.round(battery.level * 100);
      }
    } catch (ignored) {}
    return 85;
  }

  public async toggleTorch(turnOn: boolean): Promise<boolean> {
    try {
      if (turnOn) {
        if (!this.torchStream) {
          this.torchStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' }
          });
        }
        const track = this.torchStream.getVideoTracks()[0];
        if (track && 'applyConstraints' in track) {
          await (track as any).applyConstraints({
            advanced: [{ torch: true }]
          });
          return true;
        }
      } else {
        if (this.torchStream) {
          const track = this.torchStream.getVideoTracks()[0];
          if (track) {
            try {
              await (track as any).applyConstraints({
                advanced: [{ torch: false }]
              });
            } catch (ignored) {}
            track.stop();
          }
          this.torchStream = null;
          return true;
        }
      }
    } catch (e) {
      console.warn('Torch control not supported or denied', e);
    }
    return false;
  }
}

export const deviceSensors = DeviceSensors.getInstance();
