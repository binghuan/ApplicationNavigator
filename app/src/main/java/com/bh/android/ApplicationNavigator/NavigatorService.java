package com.bh.android.ApplicationNavigator;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NavigatorService extends Service {

    private final boolean DBG = FeatureList.DBG;

    private static ArrayList<ApplicationInfo> mbackupApplications;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        NavigatorService getService() {
            return NavigatorService.this;
        }
    }

    private PackageManager manager = null;
    public static boolean isSystemTrigger = true;

    /**
     * Loads the list of installed applications in mApplications.
     */
    private void loadApplications(boolean isLaunching, Context context) {
        String LOG_TAG = "BH_NavigatorService";
        if (DBG) Log.v(LOG_TAG, "loadApplications");
        if (isLaunching && mbackupApplications != null) {
            if (DBG) Log.v(LOG_TAG, "no need to loadApplications");
            return;
        }

        if (manager == null) {
            manager = context.getPackageManager();
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        if (DBG) Log.v(LOG_TAG, "queryIntentActivities");
        final List<ResolveInfo> apps = manager.queryIntentActivities(mainIntent, 0);
        if (DBG) Log.v(LOG_TAG, "sorting");
        // the method waste much time

        if (DBG) Log.v(LOG_TAG, "ready to add app info");

        if (apps != null) {
            final int count = apps.size();

            if (mbackupApplications == null) {
                mbackupApplications = new ArrayList<ApplicationInfo>(count);
            }
            mbackupApplications.clear();

            for (int i = 0; i < count; i++) {
                ApplicationInfo application = new ApplicationInfo();
                ResolveInfo info = apps.get(i);

                application.title = info.loadLabel(manager);
                application.setActivity(new ComponentName(
                                info.activityInfo.applicationInfo.packageName,
                                info.activityInfo.name),
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                application.icon = info.activityInfo.loadIcon(manager);

                // BH_Lin@20100530 -------------------------------------------->
                // purpose: get Application Version 
                // 

                PackageInfo packageInfo = null;

                try {
                    packageInfo = getPackageManager().getPackageInfo(
                            info.activityInfo.packageName, 0);
                    application.version = packageInfo.versionName;

                    String[] requestedPermissions = packageInfo.requestedPermissions;

                    //if(true)Log.d(LOG_TAG, "query App(" + packageInfo.packageName);

                    String permission = "";

                    application.usePermissionBluetooth = false;
                    application.usePermissionCalendar = false;
                    application.usePermissionGPS = false;
                    application.usePermissionPhoto = false;
                    application.usePermissionContacts = false;

                    if (requestedPermissions != null)
                        for (int k = 0; k < requestedPermissions.length; k++) {

                            Log.d(LOG_TAG, "requestedPermissions(" + requestedPermissions[k]);
                            permission = requestedPermissions[k];
                            if ("android.permission.ACCESS_COARSE_LOCATION".equals(permission)
                                    || "android.permission.ACCESS_FINE_LOCATION".equals(permission)) {
                                application.usePermissionGPS = true;
                                if (DBG) Log.d(LOG_TAG, "------> hit GPS");
                            } else if ("android.permission.READ_CALENDAR".equals(permission)
                                    || "android.permission.WRITE_CALENDAR".equals(permission)) {
                                application.usePermissionCalendar = true;
                                if (DBG) Log.d(LOG_TAG, "------> hit calendar");
                            } else if ("android.permission.WRITE_EXTERNAL_STORAGE".equals(permission)
                                    || "android.permission.READ_EXTERNAL_STORAGE".equals(permission)) {
                                application.usePermissionPhoto = true;
                                if (DBG) Log.d(LOG_TAG, "------> hit photo");
                            } else if ("android.permission.BLUETOOTH".equals(permission)) {
                                application.usePermissionBluetooth = true;
                                if (DBG) Log.d(LOG_TAG, "------> hit bluetooth");
                            } else if ("android.permission.READ_CONTACTS".equals(permission)) {
                                application.usePermissionContacts = true;
                                if (DBG) Log.d(LOG_TAG, "------> hit contact");
                            }
                        }
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                }

                if ((packageInfo != null)) {
                    try {
                        android.content.pm.ApplicationInfo mAppInfo =
                                manager.getApplicationInfo(packageInfo.packageName,
                                        PackageManager.GET_UNINSTALLED_PACKAGES);

                        if (DBG) Log.v(LOG_TAG, packageInfo.packageName + ":" + mAppInfo.sourceDir);

                        String appSourceDir = mAppInfo.sourceDir;
                        if ((appSourceDir.indexOf("/data/app") != -1) ||
                                (appSourceDir.indexOf("/system/app") != -1)) {
                            application.isInternal = true;
                        }
                    } catch (NameNotFoundException e) {
                        Log.e(LOG_TAG, "Exception when retrieving package: " +
                                packageInfo.versionName, e);
                    }

                    mbackupApplications.add(application);
                }
            }

            try {
                Collections.sort(mbackupApplications, new Comparator<ApplicationInfo>() {
                    public int compare(ApplicationInfo arg0, ApplicationInfo arg1) {
                        return ((String) arg0.getOrder()).compareTo((String) arg1.getOrder());
                    }
                });
            } catch (ClassCastException e) {
                Log.e(LOG_TAG, e.toString());
            }
        }
    }

    public ArrayList<ApplicationInfo> getLastApplicationListInfo() {
        if (mbackupApplications != null) {
            return mbackupApplications;
        } else {
            String LOG_TAG = "BH_NavigatorService";
            if (DBG) Log.v(LOG_TAG, "null app List info from service");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void setLastApplicationListInfo(ArrayList<ApplicationInfo> mAppInfo) {
        if (mAppInfo != null) {
            if (mbackupApplications != null) {
                mbackupApplications.clear();
            }
            mbackupApplications = (ArrayList<ApplicationInfo>) mAppInfo.clone();
        } else if (mAppInfo == null) {
            mbackupApplications.clear();
            mbackupApplications = null;
        }
    }

    private void setCurrentPriority() {
        // -----------------------------------
        // Set the priority of the calling thread, based on Linux priorities:
        // -----------------------------------

        // Changes the Priority of the calling Thread!
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
        // Changes the Priority of passed Thread (first param)
        Process.setThreadPriority(Process.myTid(), Process.THREAD_PRIORITY_URGENT_DISPLAY);
    }

    @Override
    public void onCreate() {
        String LOG_TAG = "BH_NavigatorService";
        if (DBG) Log.v(LOG_TAG, "onCreate");

        setCurrentPriority();
        // Display a notification about us starting.  We put an icon in the status bar.

        if (DBG) Log.v(LOG_TAG, "isSystemTrigger(" + isSystemTrigger);

        if (isSystemTrigger) {
            loadApplications(isSystemTrigger, this);
        }

        //setForeground(true);
    }

    @Override
    public void onDestroy() {
        String LOG_TAG = "BH_NavigatorService";
        if (DBG) Log.v(LOG_TAG, "onDestroy");

        // Cancel the persistent notification.

        // Tell the user we stopped.

    }

    @Override
    public IBinder onBind(Intent intent) {
        String LOG_TAG = "BH_NavigatorService";
        if (DBG) Log.v(LOG_TAG, "onBind");
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();


}

