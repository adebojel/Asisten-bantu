type SpeechCallback = () => void;

class SpeakerService {
  private static instance: SpeakerService;
  private synth: SpeechSynthesis | null = null;
  private speaking: boolean = false;
  private listeners: { onStart: SpeechCallback[]; onEnd: SpeechCallback[] } = {
    onStart: [],
    onEnd: [],
  };
  private rate: number = 0.95;
  private pitch: number = 1.0;
  private indonesianVoice: SpeechSynthesisVoice | null = null;

  private constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
      this.initVoices();
      if (this.synth.onvoiceschanged !== undefined) {
        this.synth.onvoiceschanged = () => this.initVoices();
      }
    }
  }

  public static getInstance(): SpeakerService {
    if (!SpeakerService.instance) {
      SpeakerService.instance = new SpeakerService();
    }
    return SpeakerService.instance;
  }

  private initVoices() {
    if (!this.synth) return;
    const voices = this.synth.getVoices();
    this.indonesianVoice =
      voices.find(v => v.lang.toLowerCase().startsWith('id')) ||
      voices.find(v => v.lang.toLowerCase().includes('id-id')) ||
      voices.find(v => v.name.toLowerCase().includes('indonesia')) ||
      null;
  }

  public setRate(rate: number) {
    this.rate = Math.max(0.5, Math.min(2.0, rate));
  }

  public setPitch(pitch: number) {
    this.pitch = Math.max(0.5, Math.min(1.5, pitch));
  }

  public isSpeaking(): boolean {
    return this.speaking || (this.synth ? this.synth.speaking : false);
  }

  public addListener(onStart?: SpeechCallback, onEnd?: SpeechCallback) {
    if (onStart) this.listeners.onStart.push(onStart);
    if (onEnd) this.listeners.onEnd.push(onEnd);
  }

  public removeListener(onStart?: SpeechCallback, onEnd?: SpeechCallback) {
    if (onStart) this.listeners.onStart = this.listeners.onStart.filter(l => l !== onStart);
    if (onEnd) this.listeners.onEnd = this.listeners.onEnd.filter(l => l !== onEnd);
  }

  private notifyStart() {
    this.speaking = true;
    for (const fn of this.listeners.onStart) {
      try { fn(); } catch (e) { console.error(e); }
    }
  }

  private notifyEnd() {
    this.speaking = false;
    for (const fn of this.listeners.onEnd) {
      try { fn(); } catch (e) { console.error(e); }
    }
  }

  public stop() {
    if (this.synth) {
      try {
        this.synth.cancel();
      } catch (e) {
        console.warn('SpeechSynthesis cancel error', e);
      }
    }
    this.speaking = false;
    this.notifyEnd();
  }

  public speak(text: string, callback?: () => void) {
    if (!text || !text.trim()) return;
    if (!this.synth) {
      if (callback) callback();
      return;
    }

    const cleanText = text
      .replace(/[*#_`~[\]]/g, '')
      .replace(/https?:\/\/\S+/g, 'tautan internet')
      .trim();

    this.stop();
    this.notifyStart();

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = 'id-ID';
    utterance.rate = this.rate;
    utterance.pitch = this.pitch;
    if (this.indonesianVoice) {
      utterance.voice = this.indonesianVoice;
    }

    utterance.onstart = () => {
      this.speaking = true;
    };

    utterance.onend = () => {
      this.speaking = false;
      this.notifyEnd();
      if (callback) callback();
    };

    utterance.onerror = () => {
      this.speaking = false;
      this.notifyEnd();
      if (callback) callback();
    };

    try {
      this.synth.speak(utterance);
    } catch (e) {
      this.notifyEnd();
      if (callback) callback();
    }
  }
}

export const speaker = SpeakerService.getInstance();
