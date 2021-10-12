package com.rnwireguard;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import android.app.Activity;

public interface RNWireguardCallbacks {
    ReactApplicationContext getContext();
    Activity getActivity();
    Promise getConnectPromise();
    public void emit(String type, WritableMap event);
}