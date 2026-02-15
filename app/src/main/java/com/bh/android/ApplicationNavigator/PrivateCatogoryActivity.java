package com.bh.android.ApplicationNavigator;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class PrivateCatogoryActivity extends AppCompatActivity {

    private static final boolean DBG = FeatureList.DBG;

    private final String LOG_TAG = "BH_" + this.getClass().getSimpleName();

    private static ArrayList<CatogoryInfo> mCatogory;

    private Context mContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_privatelist);

        mContext = this;

        mCatogory = new ArrayList<CatogoryInfo>(mCatogoryList.length);

        for (int i = 0; i < mCatogoryList.length; i++) {
            CatogoryInfo catogory = new CatogoryInfo();
            catogory.title = getString(mCatogoryList[i][1]);
            catogory.icon = getResources().getDrawable(mCatogoryList[i][0]);
            mCatogory.add(catogory);
        }
        CatogoryAdapter mCatogoryAdapter = new CatogoryAdapter(this, mCatogory);

        ListView catoryListView = (ListView) findViewById(R.id.all_private);
        catoryListView.setAdapter(mCatogoryAdapter);
        catoryListView.setOnItemClickListener(new onCatogoryLaunch());
    }


    /**
     * Starts the selected activity/application in the grid view.
     */
    private class onCatogoryLaunch implements AdapterView.OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            //ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
            //startActivitySafely(mContext, app.intent);

            CatogoryInfo info = (CatogoryInfo) parent.getItemAtPosition(position);
            int type = R.string.catogory_lbs;
            if (getString(R.string.catogory_lbs).equals(info.title)) {
                type = TYPE_GPS;
            } else if (getString(R.string.catogory_bluetooth).equals(info.title)) {
                type = TYPE_BLUETOOTH;
            } else if (getString(R.string.catogory_canlendar).equals(info.title)) {
                type = TYPE_CALENDAR;
            } else if (getString(R.string.catogory_contact).equals(info.title)) {
                type = TYPE_CONTACT;
            } else if (getString(R.string.catogory_picture).equals(info.title)) {
                type = TYPE_SDCARD;
            } else if (getString(R.string.catogory_reminder).equals(info.title)) {
                type = TYPE_REMINDER;
            }

            showRelatedApp(type);
        }
    }

    // BH_Lin@20101208	------------------------------------------------------->
    // purpose: enhance performance.
    // 
    static class ListViewHolder {
        ImageView icon;
        TextView appNameText;
    }
    // BH_Lin@20101208	-------------------------------------------------------<

    private class ApplicationsAdapter extends ArrayAdapter<ApplicationInfo> {
        public ApplicationsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
            super(context, 0, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ListViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater mInflater = getLayoutInflater();
                convertView = mInflater.inflate(R.layout.application, parent, false);
                holder = new ListViewHolder();

                holder.icon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.appNameText = (TextView) convertView.findViewById(R.id.app_name);
                convertView.setTag(holder);

            } else {
                holder = (ListViewHolder) convertView.getTag();
            }

            final ApplicationInfo info = mApps.get(position);
            Drawable icon = info.icon;
            holder.icon.setImageDrawable(icon);
            holder.appNameText.setText(info.title);

            return convertView;
        }
    }

    private final int TYPE_BLUETOOTH = R.string.catogory_bluetooth;
    private final int TYPE_CALENDAR = R.string.catogory_canlendar;
    private final int TYPE_CONTACT = R.string.catogory_contact;
    private final int TYPE_GPS = R.string.catogory_lbs;
    private final int TYPE_SDCARD = R.string.catogory_picture;
    private final int TYPE_REMINDER = R.string.catogory_reminder;


    private static ArrayList<ApplicationInfo> mApps;

    private static final String APP_PKG_PREFIX = "com.android.settings.";
    private static final String EXTRA_KEY_APP_PKG_NAME_1_6 = APP_PKG_PREFIX + "ApplicationPkgName";
    private final static String EXTRA_KEY_APP_PKG_NAME = "pkg";

    /**
     * Starts the selected activity/application in the grid view.
     */
    private class ApplicationDetail implements AdapterView.OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);

            final String nameOfPackage = app.intent.getComponent().getPackageName();
            final String nameOfApp = app.title.toString();

            //int sdkLevel = Integer.parseInt(SystemProperties.get("ro.build.version.sdk"));
            int sdkLevel = Build.VERSION.SDK_INT;// BH_Lin@20140415
            if (sdkLevel <= 4) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setClassName("com.android.settings",
                        "com.android.settings.InstalledAppDetails");
                intent.putExtra(EXTRA_KEY_APP_PKG_NAME_1_6, nameOfPackage);
                mContext.startActivity(intent);
            } else if (sdkLevel >= 9) {// BH_Lin@20110403
                Uri pkgUri = Uri.parse("package:" + nameOfPackage);
                Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        pkgUri);
                intent.setClassName("com.android.settings",
                        "com.android.settings.applications.InstalledAppDetails");
                mContext.startActivity(intent);
            } else {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setClassName("com.android.settings",
                        "com.android.settings.InstalledAppDetails");
                intent.putExtra(EXTRA_KEY_APP_PKG_NAME, nameOfPackage);
                mContext.startActivity(intent);
            }
        }
    }

    private void showRelatedApp(int checkType) {

        if (DBG) Log.d(LOG_TAG, "showRelatedApp by type(" + getString(checkType));

        LayoutInflater factory = LayoutInflater.from(mContext);
        View mAppListView = factory.inflate(R.layout.applist_dialog, null);
        ListView mlistOfAllApps = (ListView) mAppListView.findViewById(R.id.app_list);
        mlistOfAllApps.setOnItemClickListener(new ApplicationDetail());


        if (mApps != null) {
            mApps.clear();
            mApps = null;
        }

        mApps = new ArrayList<ApplicationInfo>();

        boolean hit = false;

        for (int i = 0; i < ApplicationNavigator.mApplications.size(); i++) {

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo = ApplicationNavigator.mApplications.get(i);

            hit = false;

            if (DBG) Log.d(LOG_TAG, "check app(" + applicationInfo.title);
            if (DBG) Log.d(LOG_TAG, "check gps(" + applicationInfo.usePermissionGPS);
            if (DBG) Log.d(LOG_TAG, "check bluetooth(" + applicationInfo.usePermissionBluetooth);

            if (checkType == TYPE_GPS) {
                if (applicationInfo.usePermissionGPS) hit = true;
            } else if (checkType == TYPE_BLUETOOTH) {
                if (applicationInfo.usePermissionBluetooth) hit = true;
            } else if (checkType == TYPE_CALENDAR) {
                if (applicationInfo.usePermissionCalendar) hit = true;
            } else if (checkType == TYPE_CONTACT) {
                if (applicationInfo.usePermissionContacts) hit = true;
            } else if (checkType == TYPE_SDCARD) {
                if (applicationInfo.usePermissionPhoto) hit = true;
            } else if (checkType == TYPE_REMINDER) {
                //if(applicationInfo.usePermissionPhoto) hit = true;
            }

            if (hit) {
                mApps.add(applicationInfo);
                if (DBG) Log.d(LOG_TAG, "---> hit app(" + applicationInfo.title);
            }
        }


        ApplicationsAdapter mApplicationsAdapter = new ApplicationsAdapter(this, mApps);

        mlistOfAllApps.setAdapter(mApplicationsAdapter);
        //mlistOfAllApps.setSelection(0);


        new AlertDialog.Builder(mContext).setCancelable(false)
                .setTitle(checkType)
                //.setMessage(getString(R.string.description_privacy_prefix) + getString(checkType) + getString(R.string.description_privacy))
                .setView(mAppListView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                    }
                }).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    // BH_Lin@20101208	------------------------------------------------------->
    // purpose: enhance performance.
    // 
    static class ViewHolder {
        ImageView catogoryIcon;
        TextView textView;
    }
    // BH_Lin@20101208	-------------------------------------------------------<

    /*
     * <string name="catogory_lbs">Location Services</string>
    <string name="catogory_contact">Contacts</string>
    <string name="catogory_canlendar">Calendars</string>
    <string name="catogory_reminder">Reminders</string>
    <string name="catogory_picture">Photos</string>
    <string name="catogory_bluetooth">Bluetooth Sharing</string>
     */
    private final int[][] mCatogoryList = {
            {R.drawable.catogory_gps, R.string.catogory_lbs}
            , {R.drawable.catogory_calendar, R.string.catogory_canlendar}
            , {R.drawable.catogory_sd, R.string.catogory_picture}
            , {R.drawable.catogory_bluetooth, R.string.catogory_bluetooth}
            , {R.drawable.catogory_contact, R.string.catogory_contact}
    };

    /**
     * GridView adapter to show the list of all installed applications.
     */
    private class CatogoryAdapter extends ArrayAdapter<CatogoryInfo> {
        public CatogoryAdapter(Context context, ArrayList<CatogoryInfo> apps) {
            super(context, 0, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater mInflater = getLayoutInflater();
                convertView = mInflater.inflate(R.layout.private_catogory_item, parent, false);
                holder = new ViewHolder();

                holder.catogoryIcon =
                        (ImageView) convertView.findViewById(R.id.iconOfprivateCatogory);
                holder.textView =
                        (TextView) convertView.findViewById(R.id.catogory_name);
                convertView.setTag(holder);

            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final CatogoryInfo catogoryInfo = mCatogory.get(position);
            holder.textView.setText(catogoryInfo.title);
            holder.catogoryIcon.setImageDrawable(catogoryInfo.icon);

            return convertView;
        }
    }
}