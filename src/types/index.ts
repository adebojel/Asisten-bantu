export interface InstalledApp {
  label: string;
  pkg: string;
  category: 'komunikasi' | 'sistem' | 'produktivitas' | 'hiburan' | 'alat';
  actionUrl?: string;
}

export interface SosContact {
  name: string;
  phone: string;
  relation: string;
}

export interface KobraSettings {
  apiKey: string;
  model: string;
  n8nUrl: string;
  speechRate: number;
  speechPitch: number;
  readNotifications: boolean;
  highContrast: boolean;
  largeFont: boolean;
  hapticFeedback: boolean;
}

export interface NotificationItem {
  id: string;
  sender: string;
  app: string;
  title: string;
  message: string;
  time: string;
  read: boolean;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'kobra';
  text: string;
  timestamp: Date;
  source?: 'offline_brain' | 'n8n' | 'openrouter' | 'system';
}

export type VisionMode = 'ktp' | 'money' | 'color' | 'face' | 'describe';

export type IndicatorState = 'idle' | 'listen' | 'speak' | 'off' | 'error';

export interface PendingConfirmation {
  kind: 'sos' | 'call' | 'msg_body' | 'msg_confirm' | 'sms_body' | 'sms_confirm' | 'app_pick';
  a: string;
  b: string;
  c?: string;
  timestamp: number;
  candidates?: { label: string; pkg: string }[];
}
