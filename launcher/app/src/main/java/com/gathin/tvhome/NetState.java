package com.gathin.tvhome;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * What the network pill needs to know: is there internet, over which link, and is a VPN up.
 * Android 5.0 can see every network; before that only the active one, so a VPN cannot be told apart.
 */
final class NetState {
    boolean vpn;
    boolean online;
    boolean wifi;
    boolean ethernet;

    static NetState read(ConnectivityManager cm) {
        NetState s = new NetState();
        if (cm == null) return s;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetStateApi21.fill(cm, s);
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                s.online = true;
                s.ethernet = info.getType() == ConnectivityManager.TYPE_ETHERNET;
                s.wifi = info.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return s;
    }
}
