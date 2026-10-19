package com.gathin.tvhome;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.RequiresApi;

/** Reads every network through NetworkCapabilities, which exists from Android 5.0. See {@link NetState}. */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class NetStateApi21 {
    private NetStateApi21() {
    }

    static void fill(ConnectivityManager cm, NetState s) {
        // All networks, not just the active one: with a VPN up the active network is the VPN itself.
        for (Network net : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) continue;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                s.vpn = true;
                continue;
            }
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            s.online = true;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.ethernet = true;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.wifi = true;
        }
    }
}
