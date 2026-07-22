/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.utils.FileUtils;

public class PwmController {
    private static final String TAG = "PwmController";
    private static PwmController sInstance;
    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private Boolean mPwmSupported;

    private PwmController(Context context) {
        mContext = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static synchronized PwmController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PwmController(context);
        }
        return sInstance;
    }

    /**
     * One-pulse support is panel-dependent: the kernel rejects reads with EFAULT
     * when the panel dtsi lacks oplus,pwm-onepulse-support, so a successful read
     * is the support signal. Panel support cannot change at runtime, so cache it.
     */
    public synchronized boolean isPwmSupported() {
        if (mPwmSupported == null) {
            mPwmSupported = FileUtils.readLineTrimmed(Constants.NODE_ONEPULSE_PWM) != null;
            if (!mPwmSupported) {
                Log.w(TAG, "One-pulse PWM is not supported on this panel");
            }
        }
        return mPwmSupported;
    }

    public boolean isPwmEnabled() {
        // The kernel state resets on reboot, so the node is the source of truth;
        // the preference is only a fallback while the node is unreadable
        String value = FileUtils.readLineTrimmed(Constants.NODE_ONEPULSE_PWM);
        if (value != null) {
            return "1".equals(value);
        }
        return mSharedPrefs.getBoolean(Constants.KEY_ONEPULSE_PWM, false);
    }

    /**
     * Re-apply the persisted PWM choice after boot: the panel always comes up with
     * one-pulse disabled, so a user selection would otherwise be lost on reboot.
     */
    public void restorePwmSetting() {
        boolean wanted = mSharedPrefs.getBoolean(Constants.KEY_ONEPULSE_PWM, false);
        if (wanted && isPwmSupported() && !isPwmEnabled()) {
            if (FileUtils.isFileWritable(Constants.NODE_ONEPULSE_PWM)) {
                setPwm(true);
                Log.i(TAG, "Restored PWM setting after boot");
            } else {
                Log.w(TAG, "PWM node is not writable, cannot restore setting");
            }
        }
    }

    public boolean enablePwm() {
        if (!isPwmSupported() || !FileUtils.isFileWritable(Constants.NODE_ONEPULSE_PWM)) {
            Log.w(TAG, "PWM is unsupported or node is not writable");
            return false;
        }

        // PWM has priority: disable HBM if it's active
        HbmController hbmController = HbmController.getInstance(mContext);
        if (hbmController.isHbmEnabled()) {
            Log.i(TAG, "HBM is active, disabling it (PWM has priority)");
            hbmController.disableHbm();
        }

        setPwm(true);
        return true;
    }

    public boolean disablePwm() {
        if (!isPwmSupported() || !FileUtils.isFileWritable(Constants.NODE_ONEPULSE_PWM)) {
            Log.w(TAG, "PWM is unsupported or node is not writable");
            return false;
        }

        setPwm(false);
        return true;
    }

    private void setPwm(boolean enable) {
        FileUtils.writeLine(Constants.NODE_ONEPULSE_PWM, enable ? "1" : "0");
        mSharedPrefs.edit().putBoolean(Constants.KEY_ONEPULSE_PWM, enable).commit();
        Log.i(TAG, "PWM set to: " + enable);
    }
}
