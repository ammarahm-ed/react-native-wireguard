package com.rnwireguard;

import android.app.PendingIntent;

public interface WGVpnServiceCallbacks {
    void stop();
    boolean getStatus(String name);
    String version() throws Exception;
}