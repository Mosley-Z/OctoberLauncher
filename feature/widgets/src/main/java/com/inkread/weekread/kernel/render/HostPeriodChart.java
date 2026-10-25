package com.inkread.weekread.kernel.render;

/** Keeps the shipped kernel's heat thresholds authoritative for the host layout. */
public final class HostPeriodChart {
    private HostPeriodChart() {}

    public static int heatLevel(int seconds) {
        return CardRenderer.heatLevel(seconds);
    }
}
