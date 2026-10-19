package com.gathin.tvhome;

import android.content.Context;

/**
 * Talks to the TV maker's own android.app.TvManager (a Realtek/Haier firmware class, not part of Android), which the
 * firmware's launcher gets with getSystemService("tv"). It is read through reflection and every failure means
 * "no such TV": on any other device this simply answers false.
 */
final class RealtekTv {
    private RealtekTv() {
    }

    /** True when a video source (HDMI, antenna) is selected, i.e. its picture and sound are running under the menu. */
    static boolean liveSourcePlaying(Context ctx) {
        try {
            Object tv = ctx.getSystemService("tv");
            if (tv == null) return false;
            int type = ((Number) tv.getClass().getMethod("getCurSourceType").invoke(tv)).intValue();
            return (Boolean) tv.getClass().getMethod("isLiveSource", int.class).invoke(tv, type);
        } catch (Throwable notThisTv) {
            return false;
        }
    }
}
