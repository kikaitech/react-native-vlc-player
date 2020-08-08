package com.ghondar.vlc;

import android.content.res.Configuration;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.IllegalViewOperationException;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

public class VlcOrientationModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private final ReactApplicationContext ctx;

    final OrientationEventListener mOrientationEventListener;
    private Integer mVlcOrientationValue;
    private String mVlcOrientation;
    private String mSpecificVlcOrientation;
    final private String[] mVlcOrientations;

    private boolean mHostActive = false;

    public static final String LANDSCAPE = "LANDSCAPE";
    public static final String LANDSCAPE_LEFT = "LANDSCAPE-LEFT";
    public static final String LANDSCAPE_RIGHT = "LANDSCAPE-RIGHT";
    public static final String PORTRAIT = "PORTRAIT";
    public static final String PORTRAIT_UPSIDEDOWN = "PORTRAITUPSIDEDOWN";
    public static final String ORIENTATION_UNKNOWN = "UNKNOWN";

    private static final int ACTIVE_SECTOR_SIZE = 45;
    private final String[] ORIENTATIONS_PORTRAIT_DEVICE = {PORTRAIT, LANDSCAPE_RIGHT, PORTRAIT_UPSIDEDOWN, LANDSCAPE_LEFT};
    private final String[] ORIENTATIONS_LANDSCAPE_DEVICE = {LANDSCAPE_LEFT, PORTRAIT, LANDSCAPE_RIGHT, PORTRAIT_UPSIDEDOWN};

    private static final int UI_FLAG_HIDE_NAV_BAR = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    public VlcOrientationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.ctx = reactContext;

        mVlcOrientations = isLandscapeDevice() ? ORIENTATIONS_LANDSCAPE_DEVICE : ORIENTATIONS_PORTRAIT_DEVICE;

        mOrientationEventListener = new OrientationEventListener(reactContext, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientationValue) {
                Log.d("data:", "" + orientationValue);

                if (!mHostActive || isDeviceVlcOrientationLocked() || !ctx.hasActiveCatalystInstance())
                    return;

                mVlcOrientationValue = orientationValue;


                Log.d("data:", "" + orientationValue);

                if (mVlcOrientation != null && mSpecificVlcOrientation != null) {
                    final int halfSector = ACTIVE_SECTOR_SIZE / 2;
                    if ((orientationValue % 90) > halfSector
                            && (orientationValue % 90) < (90 - halfSector)) {
                        return;
                    }
                }

                final String orientation = getVlcOrientationStringWhenChanging(orientationValue);
                final String specificVlcOrientation = getSpecificVlcOrientationString(orientationValue);

                final DeviceEventManagerModule.RCTDeviceEventEmitter deviceEventEmitter = ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);


                Log.d("data:", "" + orientation);

                if (!orientation.equals(mVlcOrientation)) {
                    mVlcOrientation = orientation;
                    WritableMap params = Arguments.createMap();
                    params.putString("orientation", orientation);
                    deviceEventEmitter.emit("orientationDidChange", params);
                }

                if (!specificVlcOrientation.equals(mSpecificVlcOrientation)) {
                    mSpecificVlcOrientation = specificVlcOrientation;
                    WritableMap params = Arguments.createMap();
                    params.putString("specificVlcOrientation", specificVlcOrientation);
                    deviceEventEmitter.emit("specificVlcOrientationDidChange", params);
                }
            }
        };
        ctx.addLifecycleEventListener(this);

        if (mOrientationEventListener.canDetectOrientation() == true) {
            Log.d("data:", "Habilitado");
            mOrientationEventListener.enable();
        } else {
            mOrientationEventListener.disable();
        }
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        this.ctx
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @ReactMethod
    public void HideNavigationBar(Promise promise) {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View decorView = getCurrentActivity().getWindow().getDecorView();
                    decorView.setSystemUiVisibility(UI_FLAG_HIDE_NAV_BAR);
                }
            });
        } catch (IllegalViewOperationException e) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("success", false);
            promise.reject("error", e);
        }
    }

    @ReactMethod
    public void ShowNavigationBar(Promise promise) {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View decorView = getCurrentActivity().getWindow().getDecorView();

                    int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;

                    decorView.setSystemUiVisibility(uiOptions);
                }
            });
        } catch (IllegalViewOperationException e) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("success", false);
            promise.reject("error", e);
        }
    }

    @ReactMethod
    public void isVlcOrientationLockedBySystem(Callback callback) {
        try {
            if (Settings.System.getInt(
                    getReactApplicationContext().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION
            ) == 1) {
                callback.invoke(null, false);
            } else {
                callback.invoke(null, true);
            };
        } catch (Settings.SettingNotFoundException e) {
            callback.invoke(e, null);
        }
    }

    @Override
    public String getName() {
        return "VlcOrientation";
    }

    @ReactMethod
    public void getVlcOrientation(Callback callback) {
        final int orientationInt = getReactApplicationContext().getResources().getConfiguration().orientation;

        String orientation = this.getVlcOrientationString(orientationInt);

        if (orientation == "null") {
            callback.invoke(orientationInt, null);
        } else {
            callback.invoke(null, orientation);
        }
    }

    @ReactMethod
    public void lockToPortrait() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        WritableMap params = Arguments.createMap();
        params.putString("orientation", PORTRAIT);
        sendEvent("orientationDidChange", params);
    }

    @ReactMethod
    public void lockToLandscape() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        WritableMap params = Arguments.createMap();
        params.putString("orientation", LANDSCAPE);
        sendEvent("orientationDidChange", params);
    }

    @ReactMethod
    public void lockToLandscapeLeft() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @ReactMethod
    public void lockToLandscapeRight() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
    }

    @ReactMethod
    public void unlockAllVlcOrientations() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public
    @Nullable
    Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<String, Object>();
        int orientationInt = getReactApplicationContext().getResources().getConfiguration().orientation;

        String orientation = this.getVlcOrientationString(orientationInt);
        if (orientation == "null") {
            constants.put("initialVlcOrientation", null);
        } else {
            constants.put("initialVlcOrientation", orientation);
        }

        return constants;
    }

    private boolean isDeviceVlcOrientationLocked() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return false;
        }
        return Settings.System.getInt(
                activity.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 0;
    }

    private boolean isLandscapeDevice() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return false;
        }

        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x > size.y;
    }

    private String getSpecificVlcOrientationString(int orientationValue) {
        if (orientationValue < 0) return ORIENTATION_UNKNOWN;
        final int index = (int) ((float) orientationValue / 90.0 + 0.5) % 4;
        return mVlcOrientations[index];
    }

    private String getVlcOrientationString(int orientationValue) {
        if (orientationValue == Configuration.ORIENTATION_LANDSCAPE) {
            return "LANDSCAPE";
        } else if (orientationValue == Configuration.ORIENTATION_PORTRAIT) {
            return "PORTRAIT";
        } else if (orientationValue == Configuration.ORIENTATION_UNDEFINED) {
            return "UNKNOWN";
        } else {
            return "null";
        }
    }

    private String getVlcOrientationStringWhenChanging(int orientationValue) {
        final String specificVlcOrientation = getSpecificVlcOrientationString(orientationValue);
        switch (specificVlcOrientation) {
            case LANDSCAPE_LEFT:
            case LANDSCAPE_RIGHT:
                return LANDSCAPE;
            case PORTRAIT:
            case PORTRAIT_UPSIDEDOWN:
                return PORTRAIT;
            default:
                return ORIENTATION_UNKNOWN;
        }
    }

    @Override
    public void onHostResume() {
        mHostActive = true;
    }

    @Override
    public void onHostPause() {
        mHostActive = false;
    }

    @Override
    public void onHostDestroy() {
        mHostActive = false;
    }
}
