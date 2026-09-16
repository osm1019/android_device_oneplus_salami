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
    private String mNode;

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
     * Prefer pwm_onepulse when the panel actually implements it (Ace 3 / AA551).
     * OP11 Samsung/BOE reject that node with EFAULT; ColorOS high-frequency PWM
     * dimming is dimlayer_bl_en instead.
     */
    private String pwmNode() {
        if (mNode != null) {
            return mNode;
        }
        String pulse = FileUtils.readLineTrimmed(Constants.NODE_ONEPULSE_PWM);
        if (pulse != null && !pulse.isEmpty()) {
            mNode = Constants.NODE_ONEPULSE_PWM;
        } else if (FileUtils.isFileWritable(Constants.NODE_DIMLAYER_BL)
                || FileUtils.fileExists(Constants.NODE_DIMLAYER_BL)) {
            mNode = Constants.NODE_DIMLAYER_BL;
        }
        return mNode;
    }

    public boolean isPwmSupported() {
        String node = pwmNode();
        return node != null && FileUtils.isFileWritable(node);
    }

    public boolean isPwmEnabled() {
        String node = pwmNode();
        if (node != null) {
            String value = FileUtils.readLineTrimmed(node);
            if (value != null) {
                return parseEnabled(value);
            }
        }
        return mSharedPrefs.getBoolean(Constants.KEY_ONEPULSE_PWM, false);
    }

    /**
     * Re-apply the persisted PWM choice after boot: kernel state resets.
     */
    public void restorePwmSetting() {
        boolean wanted = mSharedPrefs.getBoolean(Constants.KEY_ONEPULSE_PWM, false);
        if (wanted && isPwmSupported() && !isPwmEnabled()) {
            if (setPwm(true)) {
                Log.i(TAG, "Restored PWM setting after boot via " + pwmNode());
            } else {
                Log.w(TAG, "Failed to restore PWM setting after boot");
            }
        }
    }

    public boolean enablePwm() {
        if (!isPwmSupported()) {
            Log.w(TAG, "PWM node is not writable");
            return false;
        }

        HbmController hbmController = HbmController.getInstance(mContext);
        if (hbmController.isHbmEnabled()) {
            Log.i(TAG, "HBM is active, disabling it (PWM has priority)");
            if (!hbmController.disableHbm()) {
                Log.w(TAG, "Failed to disable HBM before enabling PWM");
                return false;
            }
        }

        PanelModeSettle.awaitIfNeeded("before PWM on");
        if (!setPwm(true)) {
            return false;
        }
        PanelModeSettle.mark();
        return true;
    }

    public boolean disablePwm() {
        if (!isPwmSupported()) {
            Log.w(TAG, "PWM node is not writable");
            return false;
        }

        if (!setPwm(false)) {
            return false;
        }
        PanelModeSettle.mark();
        return true;
    }

    private boolean setPwm(boolean enable) {
        String node = pwmNode();
        if (node == null) {
            return false;
        }
        String want = enable ? "1" : "0";
        if (!FileUtils.writeLine(node, want)) {
            Log.w(TAG, "PWM sysfs write failed (node=" + node + " enable=" + enable + ")");
            return false;
        }
        String got = FileUtils.readLineTrimmed(node);
        if (got == null || parseEnabled(got) != enable) {
            Log.w(TAG, "PWM node mismatch after write: node=" + node
                    + " want=" + want + " got=" + got);
            return false;
        }
        mSharedPrefs.edit().putBoolean(Constants.KEY_ONEPULSE_PWM, enable).commit();
        Log.i(TAG, "PWM set to " + enable + " via " + node);
        return true;
    }

    /** dimlayer_bl_en prints "1 0"; pwm_onepulse prints "1". */
    private static boolean parseEnabled(String line) {
        int space = line.indexOf(' ');
        String first = (space < 0 ? line : line.substring(0, space)).trim();
        return "1".equals(first);
    }
}
