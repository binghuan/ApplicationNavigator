package com.bh.android.ApplicationNavigator;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

public class AppsReceiver extends BroadcastReceiver {

    private final boolean DBG = FeatureList.DBG;

    private static final String LOG_TAG = "BH_AppsReceiver";

    private Handler mHandler;

    private NotificationManager mNotificationManager = null;

    public AppsReceiver() {
        if (DBG) Log.v(LOG_TAG, "OoO Constructor");

        if (mHandler == null) {
            if (DBG) Log.v(LOG_TAG, "OoO newHandler");
            mHandler = new Handler();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        Context mContext = context;

        if (mNotificationManager == null) {
            if (DBG) Log.d(LOG_TAG, "OoO getSystemServer ");
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(R.layout.application);
        }

        if (DBG) Log.d(LOG_TAG, "OoO onReceiveIntent " + action);

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (ApplicationNavigator.isFuntionEnable(context)) {
                if (DBG) Log.d(LOG_TAG, "OoO Enabling Indecator");
                ApplicationNavigator.setMood(context, context.getText(R.string.indicator_on).toString());
            } else {
                if (DBG) Log.d(LOG_TAG, "OoO not to Enabling Indecator");
                NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(R.layout.application);

            }
        } else if (action.equals(Intent.ACTION_PACKAGE_REPLACED)) {

            if (!intent.getComponent().getPackageName().contains(context.getPackageName())) {
                return;
            }

            if (ApplicationNavigator.isFuntionEnable(context)) {
                ApplicationNavigator.setMood(context, context.getText(R.string.indicator_on).toString());
            }
        }
    }
}
