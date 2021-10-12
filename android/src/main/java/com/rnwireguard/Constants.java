package com.rnwireguard;

public final class Constants {
    // Broadcast filters
    public final static String EventFilter          = "EV_FILTER";

    // Event types
    public final static String EventTypeException   = "EV_TYPE_EXCEPTION";
    public final static String EventTypeRegular     = "EV_TYPE_REGULAR";

    // Event values
    public final static String EventRevoked         = "EV_REVOKED";
    public final static String EventDestroyed       = "EV_HOST_DESTROYED";
    public final static String EventResumed         = "EV_HOST_RESUMED";
    public final static String EventPaused          = "EV_HOST_PAUSED";
    public final static String onStateChange       = "onStateChange";

    public final static String NotifChannelID       = "VPN_NOTIF_CHANNEL";
    public final static String NotifChannelName     = "VPN Connection Status";

    public final static int VpnStartIntent          = 0;
}
