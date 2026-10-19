package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * System listeners that only exist from Android 5.0. Kept out of MainActivity because a field or an anonymous
 * class of one of these types would stop the whole activity from loading on Android 4.x.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class WatchersApi21 {
    private WatchersApi21() {
    }

    /**
     * Starts listening and returns what stops it again. The callbacks run on the main thread (via
     * {@code handler}): {@code onNetworkChanged} when a connection or a VPN comes or goes, and
     * {@code onInputsChanged} when the TV registers or drops an input.
     */
    static Runnable start(Context ctx, Handler handler, Runnable onInputsChanged, Runnable onNetworkChanged) {
        // Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown.
        final TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
            @Override
            public void onInputAdded(String inputId) {
                onInputsChanged.run();
            }

            @Override
            public void onInputRemoved(String inputId) {
                onInputsChanged.run();
            }
        };
        if (tim != null) tim.registerCallback(inputCallback, handler);

        // Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown.
        // A second callback listens for any VPN coming up or going down, even one this app's traffic is excluded from.
        final ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                handler.post(onNetworkChanged);
            }

            @Override
            public void onLost(@NonNull Network network) {
                handler.post(onNetworkChanged);
            }
        };
        final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                handler.post(onNetworkChanged);
            }

            @Override
            public void onLost(@NonNull Network network) {
                handler.post(onNetworkChanged);
            }
        };
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback);
            } else {
                // Before 7.0 there is no "default network" callback: any network with internet will do for a refresh.
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
            }
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }

        return () -> {
            if (tim != null) tim.unregisterCallback(inputCallback);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkCallback);
                cm.unregisterNetworkCallback(vpnCallback);
            }
        };
    }
}
