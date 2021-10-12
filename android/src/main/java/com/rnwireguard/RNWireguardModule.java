package com.rnwireguard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rnwireguard.WGVpnService.LocalBinder;

import java.util.HashMap;
import java.util.Map;

public class RNWireguardModule extends ReactContextBaseJavaModule implements RNWireguardCallbacks {
	private final ReactApplicationContext reactContext;
	private String config;
	private Promise connectPromise;
	private String sessionName;
	private String pkg;
	private ActivityEventListener listener;
	private WGVpnServiceCallbacks vpnService;
	private ServiceConnection connection;

	private final LifecycleEventListener lifecycle = new BaseLifecycleEventListener() {
		@Override
		public void onHostDestroy() {
			//emit(Constants.EventTypeRegular, Constants.EventDestroyed);
		}

		@Override
		public void onHostResume() {
			//emit(Constants.EventTypeRegular, Constants.EventResumed);
		}

		@Override
		public void onHostPause() {
			//emit(Constants.EventTypeRegular, Constants.EventPaused);
		}
	};

	public RNWireguardModule(final ReactApplicationContext reactContext) {
		super(reactContext);
		this.reactContext = reactContext;
		pkg = reactContext.getPackageName();
		connection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {
				Log.i("WG_INFO", "VPN_SERVICE_UNBIND");
			}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				LocalBinder local = (LocalBinder) service;
				vpnService = local.getCallbacks();
				local.setCallbacks(RNWireguardModule.this);
			}
		};

		reactContext.addLifecycleEventListener(lifecycle);
		reactContext.bindService(new Intent(reactContext, WGVpnService.class),
				connection, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT | Context.BIND_IMPORTANT);
	}

	// Callback for binder
	public void emit(String type, WritableMap event) {
		reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
				.emit(type, event);
	}

	// Callback for binder
	public ReactApplicationContext getContext() {
		return reactContext;
	}

	// Callback for binder
	public Activity getActivity() {
		return getCurrentActivity();
	}

	// Callback for binder
	public Promise getConnectPromise() {
		return connectPromise;
	}

	private void startVpnService() throws Exception {
		Intent intent = new Intent(reactContext, WGVpnService.class);
		intent.putExtra(pkg + ".CONFIG", config);
		intent.putExtra(pkg + ".SESSION", sessionName);
		if (Build.VERSION.SDK_INT >= 26) {
			reactContext.startForegroundService(intent);
		} else {
			reactContext.startService(intent);
		}
	}

	@Override
	public String getName() {
		return "RNWireguard";
	}

	@Override
	public Map<String, Object> getConstants() {
		final Map<String, Object> constants = new HashMap<>();
		constants.put(Constants.EventTypeException, Constants.EventTypeException);
		constants.put(Constants.EventTypeRegular, Constants.EventTypeRegular);
		constants.put(Constants.EventRevoked, Constants.EventRevoked);
		constants.put(Constants.EventDestroyed, Constants.EventDestroyed);
		constants.put(Constants.EventResumed, Constants.EventResumed);
		constants.put(Constants.onStateChange,Constants.onStateChange);
		return constants;
	}

	@ReactMethod
	public void connect(
			String confStr, String name,Promise promise) {
		try {
			sessionName = name;
			config = confStr;
			connectPromise = promise;
			startVpnService();
		} catch (Exception e) {
			connectPromise = null;
			promise.reject(e);
		}
	}

	@ReactMethod
	private void prepare(Promise promise) {
		Intent intent = VpnService.prepare(reactContext);

		if (intent == null) {
			promise.resolve(true);
			return;
		}

		if (listener != null) {
			reactContext.removeActivityEventListener(listener);
			listener = null;
		}
		ActivityEventListener listener = new ActivityEventListener() {
			@Override
			public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
				if (resultCode == activity.RESULT_OK) {
					promise.resolve(true);
					return;
				}
				promise.resolve(false);
			}
			@Override
			public void onNewIntent(Intent intent) {}
		};
		reactContext.addActivityEventListener(listener);
		reactContext.startActivityForResult(intent, 1, null);

	}

	@ReactMethod
	public void disconnect(Promise promise) {
		if (vpnService != null) {
			vpnService.stop();
			promise.resolve(true);
		} else {
			promise.resolve(false);
		}
	}

	@ReactMethod
	public void status(String name, Promise promise) {
		if (vpnService != null) {
			promise.resolve(vpnService.getStatus(name));
		} else {
			promise.resolve(false);
		}
	}

	@ReactMethod
	public void version(Promise promise) {
		try {
			promise.resolve(vpnService.version());
		} catch (Exception e){

		}

	}
}