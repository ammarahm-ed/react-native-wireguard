package com.rnwireguard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.android.backend.WgQuickBackend;
//import com.wireguard.android.util.`;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class WGVpnService extends VpnService implements WGVpnServiceCallbacks {
    public String USER_AGENT;
    public Timer statTimer = new Timer();
    int retryCount = 0;
    private RNWireguardCallbacks rnmodule;
    private IBinder binder = new LocalBinder();
    private Backend backend;
    private RootShell rootShell;
    private ToolsInstaller toolsInstaller;
    private boolean didStartRootShell = false;
    private HashMap tunnels = new HashMap<String, Tunnel>();
    long initTime = Calendar.getInstance().getTimeInMillis();
    private String PAUSE_VPN = "com.rnwireguard.PAUSE_VPN";
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Returns true if connection is online. Please keep in mind that
    public boolean getStatus(String name) {
        if (tunnels.containsKey(name)) {
            try {
                Tunnel.State state = backend.getState((Tunnel) tunnels.get(name));
                return state == Tunnel.State.UP;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public void stop() {
        statTimer.cancel();
        statTimer.purge();
        Set<String> tunnelNames = backend.getRunningTunnelNames();
        for (String name : tunnelNames) {
            try {
                backend.setState(getTunnel(name), Tunnel.State.DOWN, null);
            } catch (Exception e) {
            }
        }
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(true);

        } else {
            stopForeground(true);
        }
    }

    public void statsTimer() {
        statTimer.cancel();
        statTimer.purge();
        statTimer = new Timer();
        statTimer.scheduleAtFixedRate(
                new TimerTask() {
                    public void run() {

                        notifyStateChange();
                    }
                },
                0,
                2000);
    }

    public String convertTwoDigit(int value) {
        if (value < 10) return "0" + value;
        else return value + "";
    }

    long time;
    int lastPacketReceive = 0;
    String seconds = "0", minutes, hours;
    String duration = "";

    public WritableMap notifyStateChange() {
        try {
            boolean state = backend.getState(getTunnel("Neon VPN")) == Tunnel.State.UP;
            WritableMap params = Arguments.createMap();
            params.putBoolean("state", state);
            Statistics stats = backend.getStatistics(getTunnel("Neon VPN"));
            params.putInt("download", (int) stats.totalRx());
            params.putInt("upload", (int) stats.totalTx());

            time = Calendar.getInstance().getTimeInMillis() - initTime;
            seconds = convertTwoDigit((int) (time / 1000) % 60);
            minutes = convertTwoDigit((int) ((time / (1000 * 60)) % 60));
            hours = convertTwoDigit((int) ((time / (1000 * 60 * 60)) % 24));
            duration = hours + ":" + minutes + ":" + seconds;
            params.putString("duration",duration);
            if (rnmodule != null) rnmodule.emit(Constants.onStateChange, params);
            return params;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, final int flags, final int startId) {

        if (intent != null && PAUSE_VPN.equals(intent.getAction())) {
            stop();
            return START_NOT_STICKY;
        }

        if (backend == null) {
            createBackend();
        }
        initTime = Calendar.getInstance().getTimeInMillis();
        new Thread(() -> {
            try {
                String pkg = getPackageName();
                String _config = intent.getStringExtra(pkg + ".CONFIG");
                String _session = intent.getStringExtra(pkg + ".SESSION");

                if (_config != null) {
                    startForegnd(R.drawable.ic_notification);
                    InputStream configStream = new ByteArrayInputStream(_config.getBytes(StandardCharsets.UTF_8));
                    Config configObj = Config.parse(configStream);
                    backend.setState(getTunnel(_session), Tunnel.State.UP, configObj);
                    statsTimer();
                    if (rnmodule != null && rnmodule.getConnectPromise() != null)
                        rnmodule.getConnectPromise().resolve(true);
                } else {
                    if (rnmodule != null && rnmodule.getConnectPromise() != null)
                        rnmodule.getConnectPromise().resolve(false);
                    stopSelf(startId);
                }
            } catch (Exception e) {
                if (e.getMessage() == "UNABLE TO START VPN" && retryCount == 0) {
                    retryCount = retryCount + 1;
                    onStartCommand(intent, flags, startId);
                } else {
                    if (rnmodule != null && rnmodule.getConnectPromise() != null)
                        rnmodule.getConnectPromise().reject(e);
                    onRevoke();
                }
            }
        }).start();
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public Tunnel getTunnel(String name) {
        if (tunnels.containsKey(name)) {
            return (Tunnel) tunnels.get(name);
        }

        tunnels.put(name, new Tunnel() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public void onStateChange(State newState) {
                notifyStateChange();
            }
        });

        return (Tunnel) tunnels.get(name);
    }

    private void createBackend() {
        if (backend != null) return;
        String abi = "unknown ABI";
        if (Build.SUPPORTED_ABIS.length != 0) {
            abi = Build.SUPPORTED_ABIS[0];
        }

//        USER_AGENT = String.format(
//                Locale.ENGLISH,
//                "WireGuard/%s (Android %d; %s; %s; %s %s; %s)",
//                Build.VERSION_NAME,
//                Build.VERSION.SDK_INT,
//                Build.BOARD,
//                abi,
//                Build.MANUFACTURER,
//                Build.MODEL,
//                Build.FINGERPRINT
//        );

        backend = null;
        // Fallback to wg-go if wg-quick is not available
        if (backend == null) {
            backend = new GoBackend(this);
        }
    }

    public String version() throws Exception {
        if (backend == null) return "";
        return backend.getVersion();
    }


    // Creates the clickable notification (needs to be called right after service is started)
    private void startForegnd(int icon) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    Constants.NotifChannelID,
                    Constants.NotifChannelName,
                    NotificationManager.IMPORTANCE_HIGH);
            chan.enableVibration(true);
            ((NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(chan);
        }
        Intent pauseVPN = new Intent(this, WGVpnService.class);
        pauseVPN.setAction(PAUSE_VPN);
        PendingIntent pauseVPNPending = PendingIntent.getService(this, 0, pauseVPN, 0);
        NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(
                this, Constants.NotifChannelID)
                .setSmallIcon(icon)
                .setContentTitle("Your internet is private")
                .setContentText("Tap to change your connection")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_media_close_clear_cancel,"Stop",pauseVPNPending)
                .setOngoing(true);

        if (rnmodule != null) {
            PendingIntent close = PendingIntent.getActivity(rnmodule.getContext(), 0,
                    new Intent(rnmodule.getContext(), rnmodule.getActivity().getClass()),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            nBuilder.setContentIntent(close);

        }
        startForeground(1, nBuilder.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createBackend();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stop();
        super.onRevoke();
    }

    public class LocalBinder extends Binder {
        // Check Android source code for details (core/java/android/net/VpnService.java)
        // It needs to be there otherwise when VPN connection is closed from Android system menus
        // onRevoke won't be called thus app won't have the knowledge that the tunnel should be closed.
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke();
                return true;
            }
            return false;
        }

        public WGVpnServiceCallbacks getCallbacks() {
            return WGVpnService.this;
        }

        public void setCallbacks(RNWireguardCallbacks cb) {
            rnmodule = cb;
        }
    }


}