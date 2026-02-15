package com.bh.android.ApplicationNavigator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ApplicationNavigator extends AppCompatActivity
        implements ListView.OnScrollListener, OnItemLongClickListener {

    private static final boolean DBG = FeatureList.DBG;

    private static final String LOG_TAG = "BH_ApplicationNavigator";
    public static ArrayList<ApplicationInfo> mApplications;
    private ListView mlistOfAllApps = null;

    private static final String APPS_INDICATOR_KEY = "apps_indicator_key";
    private static final String APPS_LOADING_OVER_SOUND_KEY = "loading_over_sound_key";

    private Context mContext = null;
    private static SharedPreferences sp = null;
    private ProgressDialog mDialog = null;

    private static boolean mLaunchNow = false;


    private TextView mBackgroundProcessText = null;
    private TextView mForegroundProcessText = null;

    private static PackageManager manager = null;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((NavigatorService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            if (DBG) Log.v(LOG_TAG, "NavigatorSerice connected");

            readyToLoadApps();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            if (DBG) Log.v(LOG_TAG, "NavigatorSerice disconnected");
        }
    };

    /**
     * Maximum number of recent tasks to query.
     */
    private static final int MAX_RECENT_TASKS = 20;

    /**
     * Refreshes the recently launched applications stacked over the favorites. The number
     * of recents depends on how many favorites are present.
     */
    private void bindRecents() {

        if (DBG) Log.v(LOG_TAG, "bindRecents");

        final PackageManager manager = getPackageManager();
        final ActivityManager tasksManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final List<ActivityManager.RecentTaskInfo> recentTasks = tasksManager.getRecentTasks(
                MAX_RECENT_TASKS, 0);

        final int count = recentTasks.size();
        final ArrayList<ApplicationInfo> recents = new ArrayList<ApplicationInfo>();

        for (int i = count - 1; i >= 0; i--) {
            final Intent intent = recentTasks.get(i).baseIntent;

            if (Intent.ACTION_MAIN.equals(intent.getAction()) &&
                    !intent.hasCategory(Intent.CATEGORY_HOME)) {

                ApplicationInfo info = getApplicationInfo(manager, intent);
                if (info != null) {
                    info.intent = intent;
                    if (!mFavorites.contains(info)) {
                        recents.add(info);
                    }
                }
            }
        }

        mApplicationsStack.setRecents(recents);
    }

    /**
     * Keys during freeze/thaw.
     */
    //private static final String KEY_SAVE_GRID_OPENED = "grid.opened";

    private static final String DEFAULT_FAVORITES_PATH = "etc/favorites.xml";

    private static final String TAG_FAVORITES = "favorites";
    private static final String TAG_FAVORITE = "favorite";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_CLASS = "class";

    /**
     * Refreshes the favorite applications stacked over the all apps button.
     * The number of favorites depends on the user.
     */
    private void bindFavorites(boolean isLaunching) {
        if (DBG) Log.v(LOG_TAG, "bindFavorites, isLaunching = " + isLaunching);

        if (!isLaunching || mFavorites == null) {

            if (mFavorites == null) {
                mFavorites = new LinkedList<ApplicationInfo>();
            } else {
                mFavorites.clear();
            }
            mApplicationsStack.setFavorites(mFavorites);

            FileReader favReader;

            // Environment.getRootDirectory() is a fancy way of saying ANDROID_ROOT or "/system".
            final File favFile = new File(Environment.getRootDirectory(), DEFAULT_FAVORITES_PATH);
            try {
                favReader = new FileReader(favFile);
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Couldn't find or open favorites file " + favFile);
                return;
            }

            final Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            final PackageManager packageManager = getPackageManager();

            try {
                final XmlPullParser parser = Xml.newPullParser();
                parser.setInput(favReader);

                beginDocument(parser);

                ApplicationInfo info;

                while (true) {
                    nextElement(parser);

                    String name = parser.getName();
                    if (!TAG_FAVORITE.equals(name)) {
                        break;
                    }

                    final String favoritePackage = parser.getAttributeValue(null, TAG_PACKAGE);
                    final String favoriteClass = parser.getAttributeValue(null, TAG_CLASS);

                    final ComponentName cn = new ComponentName(favoritePackage, favoriteClass);
                    intent.setComponent(cn);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    info = getApplicationInfo(packageManager, intent);
                    if (info != null) {
                        info.intent = intent;
                        mFavorites.addFirst(info);
                    }
                }
            } catch (XmlPullParserException e) {
                Log.w(LOG_TAG, "Got exception parsing favorites.", e);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Got exception parsing favorites.", e);
            }
        }

        mApplicationsStack.setFavorites(mFavorites);
    }

    private static void beginDocument(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(ApplicationNavigator.TAG_FAVORITES)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + ApplicationNavigator.TAG_FAVORITES);
        }
    }

    private static void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }
    }

    private static LinkedList<ApplicationInfo> mFavorites;
    private ApplicationsStackLayout mApplicationsStack;

    private static ApplicationInfo getApplicationInfo(PackageManager manager, Intent intent) {
        final ResolveInfo resolveInfo = manager.resolveActivity(intent, 0);

        if (resolveInfo == null) {
            return null;
        }

        final ApplicationInfo info = new ApplicationInfo();
        final ActivityInfo activityInfo = resolveInfo.activityInfo;
        info.icon = activityInfo.loadIcon(manager);
        if (info.title == null || info.title.length() == 0) {
            info.title = activityInfo.loadLabel(manager);
        }
        if (info.title == null) {
            info.title = "";
        }
        return info;
    }

    private boolean mIsBound = false;
    private NavigatorService mBoundService = null;

    private IntentFilter mIntentFilter = null;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mForegroundProcessText.setText(intent.getIntExtra("level", 0) + "%");
            }
        }
    };

    private TextView mLoadingMsg = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DBG) Log.v(LOG_TAG, "onCreate");

        getSoftwareVersion();// Show Version Name at first.

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);

        // Make sure the service is started.  It will continue running
        // until someone calls stopService().  The Intent we use to find
        // the service explicitly specifies our service component, because
        // we want it running in our own process and don't want other
        // applications to replace it.
        NavigatorService.isSystemTrigger = false;
        startService(new Intent(ApplicationNavigator.this,
                NavigatorService.class));

        if (DBG) Log.v(LOG_TAG, "ready to bindService");
        bindService(new Intent(ApplicationNavigator.this,
                NavigatorService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;


        // Request progress bar
        //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        //setProgressBarIndeterminateVisibility(true);

        mContext = this;

        if (sp == null) {
            sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        }

        setContentView(R.layout.main);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        mLaunchNow = true;


        mLoadingMsg = (TextView) findViewById(R.id.process_status);


        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDialogText = (TextView) inflate.inflate(R.layout.list_position, null);
        mDialogText.setVisibility(View.INVISIBLE);

        mBackgroundProcessText = (TextView) findViewById(R.id.backgroundText);
        mForegroundProcessText = (TextView) findViewById(R.id.foregroundText);

//        ImageButton button = (ImageButton)findViewById(R.id.button_app_store);
//        if(FeatureList.FEATURE_SAMSUNG_APPS == true) {
//        	button.setVisibility(View.GONE);
//        }

        registerIntentReceivers();

        mHandler.post(new Runnable() {
            public void run() {

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);

                mWindowManager.addView(mDialogText, lp);

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DBG) Log.v(LOG_TAG, "onResume");


        mReady = true;


        isForeground = true;

        if (!mLaunchNow) {
            readyToReLoadRecent();
        }

        registerReceiver(mIntentReceiver, mIntentFilter);

        updateMemoryStatus();
    }

    private void readyToReLoadRecent() {
        bindFavorites(!mLaunchNow);
        bindRecents();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DBG) Log.v(LOG_TAG, "onPause");
        isForeground = false;


        if (mIntentReceiver != null) {
            unregisterReceiver(mIntentReceiver);
            mIntentReceiver = null;
        }


        if ((mBoundService != null) &&
                (mReady) &&
                (mApplications != null)) {
            mBoundService.setLastApplicationListInfo(mApplications);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (DBG) Log.v(LOG_TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (DBG) Log.v(LOG_TAG, "onDestroy");

        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        unregisterReceiver(mApplicationsReceiver);

        if (mWindowManager != null &&
                mDialogText != null) {
            mWindowManager.removeView(mDialogText);
        }
    }

    private boolean isForeground = false;

    private final class RemoveWindow implements Runnable {
        public void run() {
            removeWindow();
        }
    }

    private void removeWindow() {
        if (mShowing) {
            mShowing = false;
            mDialogText.setVisibility(View.INVISIBLE);
        }
    }

    private final RemoveWindow mRemoveWindow = new RemoveWindow();
    //Handler mHandler = new Handler();
    private WindowManager mWindowManager;
    private TextView mDialogText;
    private boolean mShowing;
    private boolean mReady = false;

    private char mPrevLetter = Character.MIN_VALUE;

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        //int lastItem = firstVisibleItem + visibleItemCount - 1;
        if (mReady) {
            char firstLetter = mApplications.get(firstVisibleItem).title.charAt(0);
            //if(DBG)Log.v(LOG_TAG, "firstLetter:" + firstLetter);
            if (!mShowing && firstLetter != mPrevLetter) {
                mShowing = true;
                mDialogText.setVisibility(View.VISIBLE);
            }
            mDialogText.setText(((Character) firstLetter).toString());
            mHandler.removeCallbacks(mRemoveWindow);
            mHandler.postDelayed(mRemoveWindow, 3000);
            mPrevLetter = firstLetter;
        }
    }

    private final static String EXTRA_KEY_APP_PKG_NAME = "pkg";

    // attributes used as keys when passing values to InstalledAppDetails activity
    private static final String APP_PKG_PREFIX = "com.android.settings.";
    private static final String EXTRA_KEY_APP_PKG_NAME_1_6 = APP_PKG_PREFIX + "ApplicationPkgName";

    public boolean onItemLongClick(final AdapterView<?> parent, View arg1, final int position, long arg3) {
        // TODO Auto-generated method stub
        if (DBG) Log.v(LOG_TAG, "Long Click (" + position + "," + arg3);
        if (mReady) {


            // BH_Lin@20101026	----------------------------------------------->
            // purpose: Show the menu to end-user
            // option - Share the app info.(Tell a friend)
            // option - Show app info. in detail.
            //

            ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
            final String nameOfPackage = app.intent.getComponent().getPackageName();
            final String nameOfApp = app.title.toString();

            showItemSelection(mContext, nameOfApp, nameOfPackage, app.icon, app.version, app.intent);

            // BH_Lin@20101026	-----------------------------------------------<

        }

        return false;
    }

    public static void showItemSelection(final Context context, final String nameOfApp,
                                         final String nameOfPackage, final Drawable icon, final String version, final Intent appIntent) {


        LayoutInflater factory = LayoutInflater.from(context);
        final View textEntryView = factory.inflate(R.layout.app_selection_dialog, null);

        final ImageView appIcon =
                (ImageView) textEntryView.findViewById(R.id.selected_app_icon);
        appIcon.setImageDrawable(icon);

        final TextView appName =
                (TextView) textEntryView.findViewById(R.id.selected_app_name);
        appName.setText(nameOfApp);

        final TextView appVersion =
                (TextView) textEntryView.findViewById(R.id.selected_app_version);
        appVersion.setText(("version " + version));


        new AlertDialog.Builder(context)
                .setTitle(R.string.title_options).setIcon(R.drawable.ic_option)
                .setView(textEntryView)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                /* User clicked OK so do some stuff */

                    }
                })
                .setItems(R.array.select_dialog_items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {

                            case SELECTION_TELL_FRIEND:

                                ApplicationNavigator.doEmailTranscript(context, nameOfApp, nameOfPackage);
                                break;

                            case SELECTION_TELL_FRIEND_SMS:

                                ApplicationNavigator.doMMSTrascript(context, nameOfApp, nameOfPackage);


                                break;

                            case SELECTION_CREATE_SHORTCUT:

                                setupShortcut(context, appIntent);

                                break;

                            case SELECTION_VIEW_INTRODUCTION:

                                Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + nameOfPackage);// BH_Lin@2014
                                Intent launchIntent = new Intent(Intent.ACTION_VIEW, uri);
                                startActivitySafely(context, launchIntent);
                                break;

                            case SELECTION_CREATE_APPINFO:

                                //int sdkLevel = Integer.parseInt(SystemProperties.get("ro.build.version.sdk"));
                                int sdkLevel = Build.VERSION.SDK_INT;
                                if (sdkLevel <= 4) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setClassName("com.android.settings",
                                            "com.android.settings.InstalledAppDetails");
                                    intent.putExtra(EXTRA_KEY_APP_PKG_NAME_1_6, nameOfPackage);
                                    context.startActivity(intent);
                                } else if (sdkLevel >= 9) {// BH_Lin@20110403
                                    Uri pkgUri = Uri.parse("package:" + nameOfPackage);
                                    Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                                            pkgUri);
                                    intent.setClassName("com.android.settings",
                                            "com.android.settings.applications.InstalledAppDetails");
                                    context.startActivity(intent);
                                } else {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setClassName("com.android.settings",
                                            "com.android.settings.InstalledAppDetails");
                                    intent.putExtra(EXTRA_KEY_APP_PKG_NAME, nameOfPackage);
                                    context.startActivity(intent);
                                }

                                break;
                        }
                    }
                }).show();
    }

    private static final int SELECTION_TELL_FRIEND = 000;
    private static final int SELECTION_TELL_FRIEND_SMS = 001;
    private static final int SELECTION_CREATE_SHORTCUT = 002;
    private static final int SELECTION_VIEW_INTRODUCTION = 003;
    private static final int SELECTION_CREATE_APPINFO = 004;

    /**
     * This function creates a shortcut and returns it to the caller.  There are actually two
     * intents that you will send back.
     * <p/>
     * The first intent serves as a container for the shortcut and is returned to the launcher by
     * setResult().  This intent must contain three fields:
     * <p/>
     * <ul>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_INTENT} The shortcut intent.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_NAME} The text that will be displayed with
     * the shortcut.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_ICON} The shortcut's icon, if provided as a
     * bitmap, <i>or</i> {@link android.content.Intent#EXTRA_SHORTCUT_ICON_RESOURCE} if provided as
     * a drawable resource.</li>
     * </ul>
     * <p/>
     * If you use a simple drawable resource, note that you must wrapper it using
     * {@link android.content.Intent.ShortcutIconResource}, as shown below.  This is required so
     * that the launcher can access resources that are stored in your application's .apk file.  If
     * you return a bitmap, such as a thumbnail, you can simply put the bitmap into the extras
     * bundle using {@link android.content.Intent#EXTRA_SHORTCUT_ICON}.
     * <p/>
     * The shortcut intent can be any intent that you wish the launcher to send, when the user
     * clicks on the shortcut.  Typically this will be {@link android.content.Intent#ACTION_VIEW}
     * with an appropriate Uri for your content, but any Intent will work here as long as it
     * triggers the desired action within your Activity.
     */
    private static void setupShortcut(Context context, Intent appIntent) {
        if (DBG) Log.v(LOG_TAG, "OoO setupShortcut( " + appIntent);

        android.content.pm.ApplicationInfo appInfo = null;
        try {
            appInfo = manager.getApplicationInfo(
                    appIntent.getComponent().getPackageName(), 0);
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            Log.w(LOG_TAG, "" + e.toString());
            e.printStackTrace();

            return;
        }

        // First, set up the shortcut intent.  For this example, we simply create an intent that
        // will bring us directly back to this activity.  A more typical implementation would use a 
        // data Uri in order to display a more specific result, or a custom action in order to 
        // launch a specific operation.

        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(appIntent.getComponent().getPackageName(),
                appIntent.getComponent().getClassName());
        // Then, set up the container intent (the response to the caller)

        Intent intent = new Intent();
        intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);

        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, manager.getApplicationLabel(appInfo));
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                BHAppUtility.drawableToBitmap(manager.getApplicationIcon(appInfo)));
        context.sendBroadcast(intent);
    }


    private static void doEmailTranscript(Context context, String appTitle, String packageName) {

        if (DBG) Log.v(LOG_TAG, "doEmailTranscript");

        String[] mailto = {""};

        // Create a new Intent to send messages
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        // Add attributes to the intent
        sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendIntent.putExtra(Intent.EXTRA_EMAIL, mailto);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Share the app info. to you\""
                + appTitle + "\"");

        sendIntent.putExtra(Intent.EXTRA_TEXT,
                context.getText(R.string.link_google_play_package).toString() + packageName);// BH_Lin@20140415

        sendIntent.addCategory(Intent.CATEGORY_DEFAULT);
        sendIntent.setType("text/html");
        context.startActivity(Intent.createChooser(sendIntent, "Choose Email Client"));

    }

    private static void doMMSTrascript(Context context, String appTitle, String packageName) {

        if (DBG) Log.v(LOG_TAG, "doMMSTrascript");

        Intent sendIntent = new Intent();
        sendIntent = new Intent(Intent.ACTION_SEND);
        /*
        if("HTC".equalsIgnoreCase(SystemProperties.get("ro.product.brand"))) {
			sendIntent = new Intent("android.intent.action.SEND_MSG");// HTC's MSG
        } else {
        	sendIntent = new Intent(Intent.ACTION_SEND);	
        }
        */

        sendIntent.addCategory(Intent.CATEGORY_DEFAULT);

        // MMS Body
        String mmsContent = ("Share app \"" + appTitle + "\" to you : " +
                context.getText(R.string.link_google_play_package).toString() + packageName);// BH_Lin@20140415
        sendIntent.putExtra("sms_body", mmsContent);
        sendIntent.setType("text/plain");

        context.startActivity(Intent.createChooser(sendIntent, "Choose MMS Client"));
    }

    private void updateMemoryStatus() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalSize = stat.getBlockCount();
        long availableBlocks = stat.getAvailableBlocks();
        String availStr = formatSize(availableBlocks * blockSize);
        String sizeStr = formatSize(totalSize * blockSize);

        mBackgroundProcessText.setText(" " + availStr
                + "/" + sizeStr + " ");

        if (DBG) Log.v(LOG_TAG, "Total = " + sizeStr + "Avail=" + availStr);
    }

    private String formatSize(long size) {
        return Formatter.formatFileSize(this, size);
    }

    private void showNowLoading(boolean enabled) {

        if (DBG) Log.d(LOG_TAG, "*** showNowLoading: " + enabled);

        View displayLoading = (View) findViewById(R.id.process_area);
        if (enabled) {
            displayLoading.setVisibility(View.VISIBLE);
        } else {
            displayLoading.setVisibility(View.INVISIBLE);
        }
    }

    private static final int WM_LOADAPP_COMPLETE = 1001;
    private static final int WM_LOADAPP_START = 1002;
    private static final int WM_LOADAPP = 1003;

    private ActionBar mActionBar = null;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WM_LOADAPP_COMPLETE:

                    if (DBG) Log.d(LOG_TAG, "OoO WM_LOADAPP_COMPLETE");

                    bindApplications();
                    bindFavorites(mLaunchNow);
                    bindRecents();
                    bindButtons();
                    //setTitle((getText(R.string.app_name) + "    (Total Apps= " + mApplications.size() + ")"));
                    if (mActionBar == null) {
                        mActionBar = getSupportActionBar();
                    }

                    mActionBar.setSubtitle("Total Apps: " + mApplications.size());


                    if (mLaunchNow) {
                        mLaunchNow = false;
                    }

                    if (mDialog != null) {
                        mDialog.dismiss();
                        mDialog = null;
                    }

                    //setProgressBarIndeterminateVisibility(false);
                    mReady = true;
                    updateRefreshStatus();
                    break;

                case WM_LOADAPP_START:

//   				mDialog = ProgressDialog.show(mContext, 
//            	      		getText(R.string.title_loading), 
//            	       		null, true, false);
//    			
                    mReady = false;
                    updateRefreshStatus();
                    //setProgressBarIndeterminateVisibility(true);

                    break;

                case WM_LOADAPP:

                    readyToLoadApps();

                    break;
            }
        }
    };

    public static void setMood(Context context, String textOfShow) {
        // In this sample, we'll use the same text for the ticker and the expanded notification

        // Set the icon, scrolling text and timestamp
        Notification notification = null;
//        notification.flags |= Notification.FLAG_NO_CLEAR |
//                Notification.FLAG_ONGOING_EVENT;

        Intent intent = new Intent(context, ApplicationNavigator.class);
        intent.setAction(Intent.ACTION_MAIN);

        // The PendingIntent to launch our activity if the user selects this
        // notification.  Note the use of FLAG_UPDATE_CURRENT so that if there
        // is already an active matching pending intent, we will update its
        // extras to be the ones passed in here.
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Set the info for the views that show in the notification panel.
//        	notification.setLatestEventInfo(context,
//        			context.getText(R.string.title_navigator),
//        			context.getText(R.string.msg_navigator), contentIntent);
//
//
//        // Send the notification.
//        // We use a layout id because it is a unique number.  We use it later to cancel.

        Notification.Builder builder = new Notification.Builder(context);

        builder.setAutoCancel(false)
                .setTicker("this is ticker text")
                .setContentTitle("WhatsApp Notification")
                .setContentText("You have a new message")
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN);
        notification = builder.build();

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        mNotificationManager.notify(R.layout.main, notification);
    }

    public static boolean isFuntionEnable(Context context) {

        if (sp == null) {
            sp = PreferenceManager.getDefaultSharedPreferences(context);
        }

        boolean ret = sp.getBoolean(APPS_INDICATOR_KEY, false);

        if (DBG) Log.d(LOG_TAG, "OoO Current functionEnable(" + ret);

        return ret;
    }

    /**
     * Binds actions to the various buttons
     */
    private void bindButtons() {
        if (DBG) Log.v(LOG_TAG, "bindButtons");
        mlistOfAllApps = (ListView) findViewById(R.id.all_apps);
        mlistOfAllApps.setOnItemClickListener(new ApplicationLauncher());
        mlistOfAllApps.setOnScrollListener(this);
        mlistOfAllApps.setOnItemLongClickListener(this);
    }

    /**
     * Starts the selected activity/application in the grid view.
     */
    private class ApplicationLauncher implements AdapterView.OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
            startActivitySafely(mContext, app.intent);
        }
    }

    private ApplicationsAdapter mApplicationsAdapter = null;

    /**
     * Creates a new appplications adapter for the grid view and registers it.
     */
    private void bindApplications() {

        if (DBG) Log.v(LOG_TAG, "bindApplications");
        if (mlistOfAllApps == null) {
            mlistOfAllApps = (ListView) findViewById(R.id.all_apps);
        }

        mApplicationsAdapter = new ApplicationsAdapter(this, mApplications);
        mlistOfAllApps.setAdapter(mApplicationsAdapter);
        mlistOfAllApps.setSelection(0);


        if (mApplicationsStack == null) {
            mApplicationsStack = (ApplicationsStackLayout) findViewById(R.id.faves_and_recents);
        }
    }

    private synchronized void addPackage(String packageName) {

        if (!mReady) {
            return;
        }

        if (packageName != null && packageName.length() > 0) {
            final PackageManager packageManager = getPackageManager();
            final List<ResolveInfo> matches = findActivitiesForPackage(packageManager, packageName);

            if (matches.size() > 0) {
                final ApplicationsAdapter adapter = mApplicationsAdapter;

                for (ResolveInfo info : matches) {
                    adapter.setNotifyOnChange(false);
                    //adapter.add(makeAndCacheApplicationInfo(packageManager, info, this));

                    // insert the latest application in position 0.
                    adapter.insert(makeAndCacheApplicationInfo(packageManager, info, this), 0);
                }

                //adapter.sort(new ApplicationInfoComparator());
                adapter.notifyDataSetChanged();
            }
        }
    }

    private static ApplicationInfo makeAndCacheApplicationInfo(PackageManager manager,
                                                               ResolveInfo info, Context context) {

        ComponentName componentName = new ComponentName(
                info.activityInfo.applicationInfo.packageName,
                info.activityInfo.name);
        ApplicationInfo application = null;

        if (application == null) {
            application = new ApplicationInfo();
            //application.container = ItemInfo.NO_ID;

            updateApplicationInfoTitleAndIcon(manager, info, application, context);

            application.setActivity(componentName,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }

        // BH_Lin@20100530 -------------------------------------------->
        // purpose: get Application Version 
        // 
        try {
            PackageInfo packageInfo = manager.getPackageInfo(componentName.getPackageName(), 0);
            application.version = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return application;
    }

    private static void updateApplicationInfoTitleAndIcon(PackageManager manager, ResolveInfo info,
                                                          ApplicationInfo application, Context context) {

        application.title = info.loadLabel(manager);
        if (application.title == null) {
            application.title = info.activityInfo.name;
        }

        application.icon = info.loadIcon(manager);
    }

    private static List<ResolveInfo> findActivitiesForPackage(PackageManager packageManager,
                                                              String packageName) {

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        final List<ResolveInfo> matches = new ArrayList<ResolveInfo>();

        if (apps != null) {
            // Find all activities that match the packageName
            int count = apps.size();
            for (int i = 0; i < count; i++) {
                final ResolveInfo info = apps.get(i);
                final ActivityInfo activityInfo = info.activityInfo;
                if (packageName.equals(activityInfo.packageName)) {
                    matches.add(info);
                }
            }
        }

        return matches;
    }

    private synchronized void removePackage(String packageName) {

        if (!mReady) {
            return;
        }

        if (packageName != null && packageName.length() > 0) {
            final ApplicationsAdapter adapter = mApplicationsAdapter;

            final List<ApplicationInfo> toRemove = new ArrayList<ApplicationInfo>();
            final int count = adapter.getCount();

            for (int i = 0; i < count; i++) {
                final ApplicationInfo applicationInfo = adapter.getItem(i);
                final Intent intent = applicationInfo.intent;
                final ComponentName component = intent.getComponent();
                if (packageName.equals(component.getPackageName())) {
                    toRemove.add(applicationInfo);
                }
            }

            for (ApplicationInfo info : toRemove) {
                adapter.setNotifyOnChange(false);
                adapter.remove(info);
            }

            if (toRemove.size() > 0) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Registers various intent receivers. The current implementation registers
     * only a wallpaper intent receiver to let other applications change the
     * wallpaper.
     */
    private void registerIntentReceivers() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mApplicationsReceiver, filter);
        filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    }

    private final BroadcastReceiver mApplicationsReceiver = new ApplicationsIntentReceiver();

    private class UpdateCursorTask extends AsyncTask<Object, Object, Object> {

        @Override
        protected Object doInBackground(Object... arg0) {
            // TODO Auto-generated method stub

            loadApplications(mContext);
            mHandler.sendEmptyMessage(WM_LOADAPP_COMPLETE);

            return null;
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class ApplicationsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String packageName = intent.getData().getSchemeSpecificPart();
            final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

//            if (LauncherModel.DEBUG_LOADERS) {
//                d(LauncherModel.LOG_TAG, "application intent received: " + action +
//                        ", replacing=" + replacing);
//                d(LauncherModel.LOG_TAG, "  --> " + intent.getData());
//            }

            if (!Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    if (!replacing) {
                        removePackage(packageName);
                    }
                    // else, we are replacing the package, so a PACKAGE_ADDED will be sent
                    // later, we will update the package at this time
                } else {
                    if (!replacing) {
                        if (DBG) Log.d(LOG_TAG, "  --> add package");
                        addPackage(packageName);
                    } else {

                        if (DBG) Log.d(LOG_TAG, "  --> update package " + packageName);

                        //updatePackage(Launcher.this, packageName);
                    }
                }
            } else {

                if (DBG) Log.d(LOG_TAG, "  --> sync package " + packageName);

                //syncPackage(packageName);
            }
        }
    }

    // BH_Lin@20101208	------------------------------------------------------->
    // purpose: enhance performance.
    // 
    static class ViewHolder {
        ImageView appIcon;
        TextView textView;
        TextView textView2;
        ImageView appLocation;
    }
    // BH_Lin@20101208	-------------------------------------------------------<

    /**
     * GridView adapter to show the list of all installed applications.
     */
    private class ApplicationsAdapter extends ArrayAdapter<ApplicationInfo> {
        public ApplicationsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
            super(context, 0, apps);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater mInflater = getLayoutInflater();
                convertView = mInflater.inflate(R.layout.application, parent, false);
                holder = new ViewHolder();

                holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.textView = (TextView) convertView.findViewById(R.id.app_name);
                //textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                holder.textView2 = (TextView) convertView.findViewById(R.id.app_version);
                holder.appLocation = (ImageView) convertView.findViewById(R.id.location_flag);
                convertView.setTag(holder);

            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final ApplicationInfo info = mApplications.get(position);

            Drawable icon = info.icon;
            holder.appIcon.setImageDrawable(icon);
            holder.textView.setText(info.title);
            holder.textView2.setText(("v" + info.version));
            if (info.isMovable) {
                if (info.isInternal) {
                    holder.appLocation.setImageResource(R.drawable.ic_app_in_phone);
                } else {
                    holder.appLocation.setImageResource(R.drawable.ic_app_in_sd);
                }
            } else {
                holder.appLocation.setImageResource(R.drawable.ic_app_unable_to_sd);
            }

            return convertView;
        }
    }

    private static int MENU_ITEM_BASE_INDEX = 0;
    private static final int MENU_ITEM_VIEW_MORE_APPS = MENU_ITEM_BASE_INDEX++;
    private static final int MENU_ITEM_SYSTEM_SETTINGS = MENU_ITEM_BASE_INDEX++;
    private static final int MENU_ITEM_SHOW_INDICATOR = MENU_ITEM_BASE_INDEX++;
    private static final int MENU_ITEM_ABOUT = MENU_ITEM_BASE_INDEX++;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // BH_Lin@20120726	-------------------------------------------------->
        // purpose:
//    	menu.add(0, MENU_ITEM_MANAGE_APPLICATION, 0, R.string.menu_manage)
//    	.setOnMenuItemClickListener(mManageApps)
//    	.setIcon(R.drawable.ic_apps_manage);    		

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);

        menu.add(0, MENU_ITEM_SHOW_INDICATOR, 0, R.string.menu_toggle_indicator)
                .setOnMenuItemClickListener(mShowIndicator)
                .setIcon(R.drawable.ic_menu_toggle_off);

        menu.add(0, MENU_ITEM_SYSTEM_SETTINGS, 0, R.string.menu_sys_settings)
                .setOnMenuItemClickListener(mSystemSettings)
                .setIcon(android.R.drawable.ic_menu_preferences);

        if (false) {
            menu.add(0, MENU_ITEM_VIEW_MORE_APPS, 0, R.string.menu_more_apps)
                    .setOnMenuItemClickListener(mMoreApps)
                    .setIcon(android.R.drawable.ic_menu_view);
        }

        menu.add(0, MENU_ITEM_ABOUT, 0, R.string.menu_about)
                .setOnMenuItemClickListener(mAbout)
                .setIcon(android.R.drawable.ic_menu_info_details);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.button_privatelist) {
            onPrivatelistClick();
            return true;
        } else if (id == R.id.btn_title_refresh) {
            onRefreshClick();
            return true;
        } else if (id == R.id.button_app_store) {
            onMarketClick();
            return true;
        } else if (id == R.id.btn_title_search) {
            onSearchClick();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem item = menu.findItem(MENU_ITEM_SHOW_INDICATOR);
        boolean value = isFuntionEnable(this);
        if (value) {
            item.setIcon(R.drawable.ic_menu_toggle_on);
        } else {
            item.setIcon(R.drawable.ic_menu_toggle_off);
        }

        return true;
    }

    // BH_Lin@20100617	------------------------------------------------------->
    // purpose: about me
    private final MenuItem.OnMenuItemClickListener mAbout = new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            // TODO Auto-generated method stub
            if (DBG) Log.v(LOG_TAG, "mMoreApps");

            LayoutInflater factory = LayoutInflater.from(mContext);
            final View textEntryView = factory.inflate(R.layout.about, null);

            final TextView appVersion =
                    (TextView) textEntryView.findViewById(R.id.app_version);
            appVersion.setText(getSoftwareVersion());

            final TextView sendMail =
                    (TextView) textEntryView.findViewById(R.id.act_sendmail);

            // text4 illustrates constructing a styled string containing a
            // link without using HTML at all.  Again, for a fixed string
            // you should probably be using a string resource, not a
            // hardcoded value.

            SpannableString ss = new SpannableString(
                    getText(R.string.sendmail).toString());

            ss.setSpan(new URLSpan("mailto:binghuanlin@gmail.com"), 0,
                    getText(R.string.sendmail).toString().length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sendMail.setText(ss);
            sendMail.setMovementMethod(LinkMovementMethod.getInstance());

            final TextView facebook =
                    (TextView) textEntryView.findViewById(R.id.act_facebook);
            ss = new SpannableString(
                    getText(R.string.visitfacebook).toString());

            if (FeatureList.isFacebookAppExisted(mContext)) {
                ss.setSpan(new URLSpan(mContext.getString(R.string.facebook_link_app)), 0,
                        getText(R.string.visitfacebook).toString().length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ss.setSpan(new URLSpan(mContext.getString(R.string.facebook_link_web)), 0,
                        getText(R.string.visitfacebook).toString().length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            facebook.setText(ss);
            facebook.setMovementMethod(LinkMovementMethod.getInstance());


            new AlertDialog.Builder(mContext)
                    .setIcon(R.drawable.icon)
                    .setTitle(R.string.app_name)
                    .setView(textEntryView)
                    .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {


                        }
                    }).show();


            return true;
        }
    };

    private String getSoftwareVersion() {
        String appVersion = "1.00";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            Log.v(LOG_TAG, packageInfo.versionName);
            appVersion = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Package name not found", e);
        }

        return appVersion;
    }
    // BH_Lin@20100617	-------------------------------------------------------<

    private final MenuItem.OnMenuItemClickListener mShowIndicator = new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            // TODO Auto-generated method stub
            if (DBG) Log.v(LOG_TAG, "mShowIndicator");

            SharedPreferences.Editor editor = sp.edit();

            boolean value = isFuntionEnable(mContext);
            // todo ...
            if (value) {
                if (DBG) Log.v(LOG_TAG, "will disable indicator");
                NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(R.layout.main);
                Toast.makeText(mContext, getText(R.string.indicator_off), Toast.LENGTH_SHORT).show();
            } else if (!value) {
                if (DBG) Log.v(LOG_TAG, "will enable indicator");
                setMood(mContext, getText(R.string.indicator_on).toString());
            }

            editor.putBoolean(APPS_INDICATOR_KEY, !value);
            editor.commit();

            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener mSystemSettings = new MenuItem.OnMenuItemClickListener() {

        public boolean onMenuItemClick(MenuItem item) {
            if (DBG) Log.v(LOG_TAG, "mSystemSettings");
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setClassName("com.android.settings", "com.android.settings.Settings");
            startActivitySafely(mContext, launchIntent);

            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener mMoreApps = new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            // TODO Auto-generated method stub
            if (DBG) Log.v(LOG_TAG, "mMoreApps");
            Uri uri = Uri.parse(getText(R.string.link_google_play_developer).toString() + "BH_Lin");// BH_Lin@20140415
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivitySafely(mContext, launchIntent);
            //where pkg_name is the full package path for an application

            return true;
        }
    };


    private void updateRefreshStatus() {
//        findViewById(R.id.btn_title_refresh).setVisibility(
//        		!mReady ? View.GONE : View.VISIBLE);
//        findViewById(R.id.title_refresh_progress).setVisibility(
//        		!mReady ? View.VISIBLE : View.GONE);

        showNowLoading(!mReady);
    }

    /**
     * Handle "search" title-bar action.
     */
    private void onSearchClick() {
        Intent intent = new Intent();
        intent.setAction(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //sendBroadcast(intent);
        startActivity(intent);
    }

    /**
     * Handle "refresh" title-bar action.
     */
    private void onRefreshClick() {
        // trigger off background sync
        if (DBG) Log.v(LOG_TAG, "mRefreshAppList");

        updateApplist();
    }

    private static final String CATEGORY_APP_MARKET = FeatureList.CATEGORY_APP_MARKET;

    private void onMarketClick() {
        if (DBG) Log.v(LOG_TAG, "onMarketClick");

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(CATEGORY_APP_MARKET);
        startActivitySafely(this, intent);
    }


    private void onPrivatelistClick() {
        if (DBG) Log.v(LOG_TAG, "onPrivatelistClick");

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, PrivateCatogoryActivity.class);
        startActivitySafely(this, intent);
    }


    private void updateApplist() {

        if ((mReady) && (!mIsScrollingAppList)) {
            resetPendingRequestRefresh();
            readyToReLoadApps();
        } else {
            setPendingRequestRefresh();
        }
    }

    @SuppressWarnings("unchecked")
    private void readyToLoadApps() {
        if (DBG) Log.v(LOG_TAG, "readyToLoadApps");

        ArrayList<ApplicationInfo> mLastAppListInfo = mBoundService.getLastApplicationListInfo();
        if (mLastAppListInfo != null) {
            if (DBG) Log.v(LOG_TAG, "clone application List");
            mApplications = (ArrayList<ApplicationInfo>) mLastAppListInfo.clone();
            if (DBG) Log.v(LOG_TAG, "restore Application Array");
        }

        mHandler.sendEmptyMessage(WM_LOADAPP_START);
        new UpdateCursorTask().execute();
    }

    private void readyToReLoadApps() {
        if (DBG) Log.v(LOG_TAG, "readyToReLoadApps");
        mApplications.clear();
        mApplications = null;
        mBoundService.setLastApplicationListInfo(null);
        readyToLoadApps();
    }

    public static void startActivitySafely(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG, "Secret Setter does not have the permission to launch " + intent +
                    ".Make sure to create a MAIN intent-filter for the corresponding activity" +
                    "or use the exported attribute for this activity");
        }
    }

    /**
     * Loads the list of installed applications in mApplications.
     */
    private void loadApplications(Context context) {
        if (DBG) Log.v(LOG_TAG, "loadApplications");
        if (true && mApplications != null) {
            if (DBG) Log.v(LOG_TAG, "----> no need to loadApplications");
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
        //Collections.sort(apps, new ResolveInfo.DisplayNameComparator(manager));

        if (DBG) Log.v(LOG_TAG, "ready to add app info");

        if (apps != null) {
            final int count = apps.size();

            if (mApplications == null) {
                mApplications = new ArrayList<ApplicationInfo>(count);
            }
            mApplications.clear();

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
                            info.activityInfo.packageName, PackageManager.GET_PERMISSIONS);
                    application.version = packageInfo.versionName;
                    String[] requestedPermissions = packageInfo.requestedPermissions;

                    if (DBG) Log.d(LOG_TAG, "query App(" + application.title);

                    String permission = "";

                    application.usePermissionBluetooth = false;
                    application.usePermissionCalendar = false;
                    application.usePermissionGPS = false;
                    application.usePermissionPhoto = false;
                    application.usePermissionContacts = false;

                    if (requestedPermissions != null)
                        for (int k = 0; k < requestedPermissions.length; k++) {

                            if (DBG)
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

                int FLAG_EXTERNAL_STORAGE = 0x40000;
                int FLAG_FORWARD_LOCK = 0x100000;

                boolean moveDisable = true;

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


                    if ((mAppInfo.flags & FLAG_EXTERNAL_STORAGE) != 0) {
                        if (DBG) Log.v(LOG_TAG,
                                "Always let apps move to internal storage from sdcard");
                        moveDisable = false;
                    } else {
                        if ((mAppInfo.flags & FLAG_FORWARD_LOCK) == 0 &&
                                (mAppInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&

                                packageInfo != null) {
                        }
                    }
                } catch (NameNotFoundException e) {
                    Log.e(LOG_TAG, "Exception when retrieving package: " +
                            packageInfo.versionName, e);
                }

                application.isMovable = (!moveDisable);

                mApplications.add(application);
                applicationInfo = application;

                mLoadingAppsCounter = (i + 1);

                mHandler.post(new Runnable() {
                    public void run() {
                        if (isForeground) {
                            //if(mDialog != null) {
                            //	mDialog.setMessage( ("<" + mLoadingAppsCounter + ">: " + applicationInfo.title.toString()));
                            //}

                            if (mLoadingMsg != null) {
                                mLoadingMsg.setText(("<" + mLoadingAppsCounter + ">: " + applicationInfo.title.toString()));
                            }
                        }
                    }
                });
            }
        }

        // BH_Lin@20100508	--------------------------------------------------->
        // purpose: enhance sorting method.
        Collections.sort(mApplications, new Comparator<ApplicationInfo>() {
            public int compare(ApplicationInfo arg0, ApplicationInfo arg1) {
                return ((String) arg0.getOrder()).compareTo((String) arg1.getOrder());
            }
        });
        // BH_Lin@20100508	---------------------------------------------------<
        mBoundService.setLastApplicationListInfo(mApplications);
    }

    private static int mLoadingAppsCounter = 0;

    private static ApplicationInfo applicationInfo = new ApplicationInfo();


    public void onScrollStateChanged(AbsListView arg0, int scrollState) {
        // TODO Auto-generated method stub
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            if (DBG) Log.i(LOG_TAG, "scrolling stopped...");
            setIsScrollingNow(false);

            if (mPendingRequestRefresh > 0) {
                updateApplist();
            }

        } else {
            setIsScrollingNow(true);
        }
    }

    private int mPendingRequestRefresh = 0;

    private void setPendingRequestRefresh() {
        mPendingRequestRefresh += 1;
    }

    private void resetPendingRequestRefresh() {
        mPendingRequestRefresh = 0;
    }


    private boolean mIsScrollingAppList = false;

    private void setIsScrollingNow(boolean value) {
        mIsScrollingAppList = value;
    }

}