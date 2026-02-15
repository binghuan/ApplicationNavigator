package com.bh.android.ApplicationNavigator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import java.util.List;

class FeatureList {

    private final static String LOG_TAG = "BH_FeatureList";

    public static final boolean DBG = false;


    public static final String CATEGORY_APP_MARKET =
            "android.intent.category.APP_MARKET";

    // BH_Lin@20120820	------------------------------------------------------->
    // purpose: in order to submit application to Samsung Apps.
    // remove the link to Goolge Play.
    public static final boolean FEATURE_SAMSUNG_APPS = true;

    public static boolean isFacebookAppExisted(Context context) {

        boolean ret = false;


        PackageManager pm = context.getPackageManager();

        Uri uri = Uri.parse(context.getString(R.string.facebook_link_app));// BH_Lin@20110626
        Intent launchIntent = new Intent(Intent.ACTION_VIEW, uri);

        final List<ResolveInfo> apps = pm.queryIntentActivities(launchIntent, 0);
        if ((apps != null) && apps.size() > 0) {
            ret = true;
        }

        if (DBG) Log.d(LOG_TAG, "isFacebookAppExisted(" + ret);

        return ret;

    }

}
