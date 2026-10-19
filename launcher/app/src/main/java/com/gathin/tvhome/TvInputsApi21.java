package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The TV Input Framework (HDMI, AV and tuner inputs) exists from Android 5.0. It lives in this class so
 * Android 4.x never loads those types; MainActivity talks to it with plain strings and numbers, and only
 * after checking the Android version.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class TvInputsApi21 {
    private TvInputsApi21() {
    }

    /** One passthrough input (HDMI or AV), ready to show. {@code state} is a TvInputManager.INPUT_STATE_*, or -1 for unknown. */
    static final class Row {
        final String id;
        final String label;
        final boolean hdmi;
        final int state;

        Row(String id, String label, boolean hdmi, int state) {
            this.id = id;
            this.label = label;
            this.hdmi = hdmi;
            this.state = state;
        }
    }

    static final class Result {
        final List<Row> rows;
        /** The system tuner input to open Live TV on, or null when there is none. */
        final String tunerId;

        Result(List<Row> rows, String tunerId) {
            this.rows = rows;
            this.tunerId = tunerId;
        }
    }

    /** True when the TV registers at least one passthrough input (HDMI-in, AV). */
    static boolean hasPassthroughInput(Context ctx) {
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return false;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput()) return true;
        }
        return false;
    }

    static Uri passthroughUri(String inputId) {
        return TvContract.buildChannelUriForPassthroughInput(inputId);
    }

    /** The passthrough inputs, HDMI ports first, and the tuner to use for Live TV. Empty when there is no TvInputManager. */
    static Result query(Context ctx) {
        List<Row> rows = new ArrayList<>();
        String tunerId = null;
        boolean tunerIsHw = false;
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return new Result(rows, null);

        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        // HDMI ports first in port order, then AV and the rest.
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
        });
        // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
        // so drop the HDMI status then. A TV with every port really in use loses it too.
        int hdmiPorts = 0, hdmiConnected = 0;
        for (TvInputInfo info : inputs) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            hdmiPorts++;
            if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
        }
        boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
        for (TvInputInfo info : inputs) {
            if (info.getType() == TvInputInfo.TYPE_TUNER
                    && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                boolean hw = info.getId().matches(".*/HW\\d+$");
                if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                    tunerIsHw = hw;
                }
            }
            if (!info.isPassthroughInput()) continue;
            CharSequence l = info.loadLabel(ctx);
            String label = l == null ? info.getId() : l.toString();
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(info.getId());
            rows.add(new Row(info.getId(), label, hdmi, state));
        }
        return new Result(rows, tunerId);
    }
}
