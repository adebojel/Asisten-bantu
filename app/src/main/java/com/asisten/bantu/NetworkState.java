package com.asisten.bantu;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

/** Mengecek dan memantau koneksi internet. Dipakai untuk memutuskan offline atau online secara otomatis. */
public final class NetworkState {

    public interface Listener { void onChange(boolean online); }

    private NetworkState() {}

    public static boolean isOnline(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static String connectionType(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "Terputus";
        Network n = cm.getActiveNetwork();
        if (n == null) return "Terputus";
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "Terputus";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Data Seluler";
        return "Tersambung";
    }

    /** Memanggil listener hanya ketika status berubah (bukan saat pertama kali dicek). */
    public static ConnectivityManager.NetworkCallback watch(Context c, Listener listener) {
        ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        final Boolean[] last = {null};
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (online) report(true, listener, last);
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                report(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), listener, last);
            }
            @Override public void onLost(Network network) {
                report(isOnline(c), listener, last);
            }
        };
        cm.registerNetworkCallback(request, callback);
        return callback;
    }

    public static void unwatch(Context c, ConnectivityManager.NetworkCallback callback) {
        if (callback == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {}
    }

    private static void report(boolean online, Listener listener, Boolean[] last) {
        if (last[0] != null && last[0] == online) return;
        boolean first = last[0] == null;
        last[0] = online;
        if (!first) listener.onChange(online);
    }
}
