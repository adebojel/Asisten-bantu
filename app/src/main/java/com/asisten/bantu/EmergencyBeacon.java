package com.asisten.bantu;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Pengelola sirine bahaya keras, kedip senter Morse SOS, pencari HP (Find My Phone),
 * dan siaran darurat tanpa sinyal seluler ("DARURAT-KOBRA-SOS").
 */
public final class EmergencyBeacon {

    private static final String TAG = "KobraBeacon";
    public static final String EMERGENCY_BROADCAST_NAME = "DARURAT-KOBRA-SOS";

    private static volatile boolean sirenActive = false;
    private static volatile boolean findPhoneActive = false;
    private static Thread sirenThread;
    private static Thread torchThread;
    private static AudioTrack activeTrack;
    private static String savedBtName;
    private static BluetoothLeAdvertiser activeAdvertiser;
    private static AdvertiseCallback activeAdvCallback;
    private static WifiP2pManager.Channel wifiChannel;
    private static int savedAlarmVol = -1;
    private static int savedMusicVol = -1;

    private EmergencyBeacon() {}

    public static boolean isSirenActive() {
        return sirenActive;
    }

    /**
     * Menyalakan sirine bahaya (volume 100%, AudioTrack USAGE_ALARM),
     * kedip senter Morse SOS (... --- ...), getaran Morse, dan siaran darurat offline.
     */
    public static void startDangerSiren(Context context, String torchId) {
        stopAll(context, torchId);
        Context app = context.getApplicationContext();
        sirenActive = true;

        boostAlarmAndMediaVolume(app);
        HapticFeedback.vibrateSosMorse(app, true);
        startEmergencyBroadcast(app);

        sirenThread = new Thread(() -> runAlarmToneLoop(true), "KobraSirenAudio");
        sirenThread.start();

        if (torchId != null) {
            torchThread = new Thread(() -> runMorseTorchLoop(app, torchId), "KobraMorseTorch");
            torchThread.start();
        }
    }

    /**
     * Mode Find My Phone: membunyikan nada panggil keras melalui channel alarm
     * meskipun HP dalam mode senyap, disertai kedip senter singkat dan getaran.
     */
    public static void ringFindMyPhone(Context context, String torchId, Speaker speaker) {
        if (sirenActive) return;
        stopFindPhone(context, torchId);
        Context app = context.getApplicationContext();
        findPhoneActive = true;

        boostAlarmAndMediaVolume(app);
        HapticFeedback.vibrateWarning(app);

        Thread ringThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            playFindPhoneChime();
            while (findPhoneActive && System.currentTimeMillis() - start < 4500L) {
                try { Thread.sleep(150); } catch (InterruptedException ignored) { break; }
            }
            findPhoneActive = false;
            restoreVolumes(app);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (speaker != null) {
                    speaker.speak("Saya di sini! HP Anda ada di sini!");
                }
            });
        }, "KobraFindPhone");
        ringThread.start();

        if (torchId != null) {
            new Thread(() -> blinkTorchTimes(app, torchId, 4), "KobraFindPhoneTorch").start();
        }
    }

    public static void stopFindPhone(Context context, String torchId) {
        findPhoneActive = false;
        stopAudioTrack();
        setTorchSafe(context, torchId, false);
    }

    public static void stopAll(Context context, String torchId) {
        sirenActive = false;
        findPhoneActive = false;
        stopAudioTrack();
        if (sirenThread != null) {
            sirenThread.interrupt();
            sirenThread = null;
        }
        if (torchThread != null) {
            torchThread.interrupt();
            torchThread = null;
        }
        Context app = context.getApplicationContext();
        HapticFeedback.stop(app);
        setTorchSafe(app, torchId, false);
        stopEmergencyBroadcast(app);
        restoreVolumes(app);
    }

    /**
     * Mengaktifkan siaran identifikasi darurat Bluetooth & Wi-Fi Direct "DARURAT-KOBRA-SOS"
     * saat sinyal seluler tidak tersedia atau dalam kondisi darurat.
     */
    public static void startEmergencyBroadcast(Context context) {
        Context app = context.getApplicationContext();
        startBluetoothBroadcast(app);
        startWifiDirectBeacon(app);
    }

    public static void stopEmergencyBroadcast(Context context) {
        Context app = context.getApplicationContext();
        try {
            if (activeAdvertiser != null && activeAdvCallback != null) {
                boolean hasAdv = Build.VERSION.SDK_INT < 31 || Perms.has(app, Manifest.permission.BLUETOOTH_ADVERTISE);
                if (hasAdv) {
                    activeAdvertiser.stopAdvertising(activeAdvCallback);
                }
            }
        } catch (Throwable ignored) {}
        activeAdvertiser = null;
        activeAdvCallback = null;

        try {
            if (savedBtName != null) {
                boolean hasConn = Build.VERSION.SDK_INT < 31 || Perms.has(app, Manifest.permission.BLUETOOTH_CONNECT);
                BluetoothAdapter adapter = getBluetoothAdapter(app);
                if (adapter != null && hasConn) {
                    adapter.setName(savedBtName);
                }
                savedBtName = null;
            }
        } catch (Throwable ignored) {}

        try {
            if (wifiChannel != null) {
                WifiP2pManager manager = (WifiP2pManager) app.getSystemService(Context.WIFI_P2P_SERVICE);
                if (manager != null) {
                    manager.removeGroup(wifiChannel, null);
                }
                wifiChannel = null;
            }
        } catch (Throwable ignored) {}
    }

    private static void startBluetoothBroadcast(Context app) {
        try {
            BluetoothAdapter adapter = getBluetoothAdapter(app);
            if (adapter == null || !adapter.isEnabled()) return;

            boolean hasConn = Build.VERSION.SDK_INT < 31 || Perms.has(app, Manifest.permission.BLUETOOTH_CONNECT);
            if (hasConn) {
                String currentName = adapter.getName();
                if (currentName != null && !EMERGENCY_BROADCAST_NAME.equals(currentName)) {
                    savedBtName = currentName;
                }
                adapter.setName(EMERGENCY_BROADCAST_NAME);
            }

            boolean hasAdv = Build.VERSION.SDK_INT < 31 || Perms.has(app, Manifest.permission.BLUETOOTH_ADVERTISE);
            BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser != null && hasAdv) {
                AdvertiseSettings settings = new AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .build();
                AdvertiseData data = new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .build();
                activeAdvCallback = new AdvertiseCallback() {
                    @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        Log.i(TAG, "Siaran BLE darurat aktif: " + EMERGENCY_BROADCAST_NAME);
                    }
                    @Override public void onStartFailure(int errorCode) {
                        Log.w(TAG, "Siaran BLE darurat gagal kode: " + errorCode);
                    }
                };
                activeAdvertiser = advertiser;
                advertiser.startAdvertising(settings, data, activeAdvCallback);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Izin Bluetooth darurat belum diberikan", se);
        } catch (Throwable t) {
            Log.w(TAG, "Gagal memulai siaran Bluetooth darurat", t);
        }
    }

    private static void startWifiDirectBeacon(Context app) {
        try {
            boolean hasWifiPerm = Build.VERSION.SDK_INT >= 33
                    ? Perms.has(app, Manifest.permission.NEARBY_WIFI_DEVICES)
                    : Perms.has(app, Manifest.permission.ACCESS_FINE_LOCATION);
            if (!hasWifiPerm) return;

            WifiP2pManager manager = (WifiP2pManager) app.getSystemService(Context.WIFI_P2P_SERVICE);
            if (manager == null) return;
            wifiChannel = manager.initialize(app, Looper.getMainLooper(), null);
            if (wifiChannel != null) {
                manager.createGroup(wifiChannel, new WifiP2pManager.ActionListener() {
                    @Override public void onSuccess() {
                        Log.i(TAG, "Wi-Fi Direct darurat aktif.");
                    }
                    @Override public void onFailure(int reason) {
                        Log.w(TAG, "Wi-Fi Direct darurat gagal kode: " + reason);
                    }
                });
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Izin Wi-Fi Direct darurat belum diberikan", se);
        } catch (Throwable t) {
            Log.w(TAG, "Gagal mengaktifkan Wi-Fi Direct darurat", t);
        }
    }

    private static BluetoothAdapter getBluetoothAdapter(Context app) {
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
    }

    private static void boostAlarmAndMediaVolume(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (savedMusicVol < 0) savedMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (savedAlarmVol < 0) savedAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) {
            Log.w(TAG, "Tidak bisa menaikkan volume penuh", e);
        }
    }

    private static void restoreVolumes(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (savedMusicVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0);
                savedMusicVol = -1;
            }
            if (savedAlarmVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0);
                savedAlarmVol = -1;
            }
        } catch (Exception ignored) {}
    }

    private static void runAlarmToneLoop(boolean isSiren) {
        final int sampleRate = 16000;
        final int chunkSamples = sampleRate / 10; // 100ms chunks
        short[] pcm = new short[chunkSamples];
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack track = new AudioTrack(attrs, format, Math.max(minBuf, chunkSamples * 2), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            activeTrack = track;
            track.play();

            double phase = 0.0;
            int step = 0;
            while (sirenActive && !Thread.currentThread().isInterrupted()) {
                double freq = (step % 8 < 4) ? 1480.0 : 960.0;
                double inc = 2.0 * Math.PI * freq / sampleRate;
                for (int i = 0; i < chunkSamples; i++) {
                    pcm[i] = (short) (Math.sin(phase) * 31000.0);
                    phase += inc;
                }
                if (!sirenActive) break;
                track.write(pcm, 0, chunkSamples);
                step++;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Sirine audio berhenti", t);
        } finally {
            stopAudioTrack();
        }
    }

    private static void playFindPhoneChime() {
        final int sampleRate = 16000;
        final int chunkSamples = sampleRate / 10;
        short[] pcm = new short[chunkSamples];
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack track = new AudioTrack(attrs, format, Math.max(minBuf, chunkSamples * 2), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            activeTrack = track;
            track.play();

            double phase = 0.0;
            double[] notes = {880.0, 1174.66, 1567.98, 0.0, 880.0, 1174.66, 1567.98, 0.0};
            for (int r = 0; r < 4 && findPhoneActive; r++) {
                for (double freq : notes) {
                    if (!findPhoneActive) break;
                    double inc = 2.0 * Math.PI * freq / sampleRate;
                    for (int i = 0; i < chunkSamples; i++) {
                        pcm[i] = freq == 0.0 ? 0 : (short) (Math.sin(phase) * 30000.0);
                        phase += inc;
                    }
                    track.write(pcm, 0, chunkSamples);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Find My Phone audio selesai/gagal", t);
        } finally {
            stopAudioTrack();
        }
    }

    private static synchronized void stopAudioTrack() {
        if (activeTrack != null) {
            try {
                activeTrack.pause();
                activeTrack.flush();
                activeTrack.stop();
                activeTrack.release();
            } catch (Throwable ignored) {}
            activeTrack = null;
        }
    }

    private static void runMorseTorchLoop(Context app, String torchId) {
        // Pola Morse SOS: ... --- ...
        int[] onDurations = {180, 180, 180, 500, 500, 500, 180, 180, 180};
        int[] offDurations = {150, 150, 320, 150, 150, 320, 150, 150, 900};
        try {
            while (sirenActive && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < onDurations.length && sirenActive; i++) {
                    setTorchSafe(app, torchId, true);
                    Thread.sleep(onDurations[i]);
                    setTorchSafe(app, torchId, false);
                    Thread.sleep(offDurations[i]);
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            setTorchSafe(app, torchId, false);
        }
    }

    private static void blinkTorchTimes(Context app, String torchId, int count) {
        try {
            for (int i = 0; i < count && findPhoneActive; i++) {
                setTorchSafe(app, torchId, true);
                Thread.sleep(180);
                setTorchSafe(app, torchId, false);
                Thread.sleep(160);
            }
        } catch (InterruptedException ignored) {
        } finally {
            setTorchSafe(app, torchId, false);
        }
    }

    private static void setTorchSafe(Context context, String torchId, boolean on) {
        if (torchId == null || context == null) return;
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) cm.setTorchMode(torchId, on);
        } catch (Exception ignored) {}
    }
}
