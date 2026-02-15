package com.bh.android.ApplicationNavigator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.util.Log;

class BHAppUtility {

    private final static boolean DBG = FeatureList.DBG;
    private final static String LOG_TAG = "BH_BHAppUtility";

    private final static int SOUND_PRESS_BUTTON = 001;
    private final static int SOUND_VIEW_MORE_APPS = 002;

    public static Bitmap drawableToBitmap(Drawable drawable) {

        Bitmap.Config c = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), c);

        Canvas canvas = new Canvas(bitmap);
        //canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

}