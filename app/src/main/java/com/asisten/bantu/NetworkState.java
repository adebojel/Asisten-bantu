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

    /** Memanggil listener hanya ketika status berubah (bukan saat pertama kali dicek). */
    public static void watch(Context c, Listener listener) {
        ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        final Boolean[] last = {null};
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                report(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), listener, last);
            }
            @Override public void onLost(Network network) {
                report(false, listener, last);
            }
        });
    }

    private static void report(boolean online, Listener listener, Boolean[] last) {
        if (last[0] != null && last[0] == online) return;
        boolean first = last[0] == null;
        last[0] = online;
        if (!first) listener.onChange(online);
    }
}
