package com.bh.android.ApplicationNavigator;

import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class ApplicationsStackLayout extends ViewGroup implements View.OnClickListener, OnLongClickListener {
	
	private final boolean DBG = FeatureList.DBG;
	
	private final String LOG_TAG = "BH_ApplicationsStackLayout";

    private static final int VERTICAL = 1;

    private View mButton;
    private LayoutInflater mInflater;

    private int mFavoritesEnd;
    private int mFavoritesStart;

    private List<ApplicationInfo> mFavorites;
    private List<ApplicationInfo> mRecents;

    private int mOrientation = VERTICAL;

    private int mMarginLeft;
    private int mMarginTop;
    private int mMarginRight;
    private int mMarginBottom;

    private final Rect mDrawRect = new Rect();

    private Drawable mBackground;
    
    public ApplicationsStackLayout(Context context) {
        super(context);
        
        initLayout();
    }

    public ApplicationsStackLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ApplicationsStackLayout);

        mOrientation = a.getInt(R.styleable.ApplicationsStackLayout_stackOrientation, VERTICAL);

        mMarginLeft = a.getDimensionPixelSize(R.styleable.ApplicationsStackLayout_marginLeft, 0);
        mMarginTop = a.getDimensionPixelSize(R.styleable.ApplicationsStackLayout_marginTop, 0);
        mMarginRight = a.getDimensionPixelSize(R.styleable.ApplicationsStackLayout_marginRight, 0);
        mMarginBottom = a.getDimensionPixelSize(R.styleable.ApplicationsStackLayout_marginBottom, 0);

        if(DBG)Log.v(LOG_TAG, "ApplicationsStackLayout(" + mOrientation + "," +
        		mMarginLeft + "," + mMarginTop + "," + mMarginRight + "," + mMarginBottom);
        
        a.recycle();

        initLayout();
    }

    private void initLayout() {
        mInflater = LayoutInflater.from(getContext());
        mButton = mInflater.inflate(R.layout.all_applications_button, this, false);
        addView(mButton);

        mBackground = getBackground();
        setBackgroundDrawable(null);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable background = mBackground;

        final int right = getWidth();
        final int bottom = getHeight();

        // Draw behind recents
        if (mOrientation == VERTICAL) {
            mDrawRect.set(0, 0, right, mFavoritesStart);
        } else {
            mDrawRect.set(0, 0, mFavoritesStart, bottom);
        }
        background.setBounds(mDrawRect);
        background.draw(canvas);

        // Draw behind favorites
        if (mFavoritesStart > -1) {
            if (mOrientation == VERTICAL) {
                mDrawRect.set(0, mFavoritesStart, right, mFavoritesEnd);
            } else {
                mDrawRect.set(mFavoritesStart, 0, mFavoritesEnd, bottom);
            }
            background.setBounds(mDrawRect);
            background.draw(canvas);
        }

        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("ApplicationsStackLayout can only be used with "
                    + "measure spec mode=EXACTLY");
        }
        
        if(DBG)Log.v(LOG_TAG,"onMeasure(" + widthSize + "," + heightSize );
        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    	if(DBG)Log.v(LOG_TAG, "onLayout");
    	
    	if(mFavorites == null) {
    		return ;
    	}
        removeAllApplications();

        LayoutParams layoutParams = mButton.getLayoutParams();
        final int widthSpec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
        final int heightSpec = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
        
        if(DBG)Log.v(LOG_TAG, "widthSpec,heightSpec(" + layoutParams.width + "," + layoutParams.height);
        mButton.measure(widthSpec, heightSpec);

        if (mOrientation == VERTICAL) {
            layoutVertical();
        } else {
            layoutHorizontal();
        }
    }

    private void layoutVertical() {
    	
    	if(DBG)Log.v(LOG_TAG, "layoutVertical");
    	
        int childLeft = 0;
        int childTop = getHeight();

        int childWidth = mButton.getMeasuredWidth();
        int childHeight = mButton.getMeasuredHeight();

        childTop -= childHeight + mMarginBottom;
        mButton.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        childTop -= mMarginTop;
        mFavoritesEnd = childTop - mMarginBottom;

        int oldChildTop = childTop;
        childTop = stackApplications(mFavorites, childLeft, childTop);
        if (childTop != oldChildTop) {
            mFavoritesStart = childTop + mMarginTop;
        } else {
            mFavoritesStart = -1;
        }

        stackApplications(mRecents, childLeft, childTop);
    }

    private void layoutHorizontal() {
    	if(DBG)Log.v(LOG_TAG, "layoutHorizontal");
    	
        int childLeft = getWidth();
        int childTop = 0;

        int childWidth = mButton.getMeasuredWidth();
        int childHeight = mButton.getMeasuredHeight();

        mButton.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        childLeft -= mMarginLeft;
        mFavoritesEnd = childLeft - mMarginRight;

        int oldChildLeft = childLeft;
        childLeft = stackApplications(mFavorites, childLeft, childTop);
        if (childLeft != oldChildLeft) {
            mFavoritesStart = childLeft + mMarginLeft;
        } else {
            mFavoritesStart = -1;
        }

        stackApplications(mRecents, childLeft, childTop);
    }

    private int stackApplications(List<ApplicationInfo> applications, int childLeft, int childTop) {
    	
    	if(DBG)Log.v(LOG_TAG, "stackApplications (" + childLeft + "," + childTop);
    	
        LayoutParams layoutParams;
        int widthSpec;
        int heightSpec;
        int childWidth;
        int childHeight;

        final boolean isVertical = mOrientation == VERTICAL;

        final int count = applications.size();
        if(DBG)Log.v(LOG_TAG, "applications.size (" + count);
        for (int i = count - 1; i >= 0; i--) {
            final ApplicationInfo info = applications.get(i);
            final View view = createApplicationIcon(mInflater, this, info);

            layoutParams = view.getLayoutParams();
            widthSpec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
            heightSpec = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
            view.measure(widthSpec, heightSpec);

            childWidth = view.getMeasuredWidth();
            childHeight = view.getMeasuredHeight();

            if (isVertical) {
                childTop -= childHeight + mMarginBottom;

                if (childTop < 0) {
                    childTop += childHeight + mMarginBottom;
                    break;
                }
            } else {
                childLeft -= childWidth + mMarginRight;

                if (childLeft < 0) {
                    childLeft += childWidth + mMarginRight;
                    break;
                }
            }

            addViewInLayout(view, -1, layoutParams);

            view.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

            if (isVertical) {
                childTop -= mMarginTop;
            } else {
                childLeft -= mMarginLeft;
            }
        }

        return isVertical ? childTop : childLeft;
    }

    private void removeAllApplications() {
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View view = getChildAt(i);
            if (view != mButton) {
                removeViewAt(i);
            }
        }
    }

    private View createApplicationIcon(LayoutInflater inflater,
            ViewGroup group, ApplicationInfo info) {
    	
    	//RelativeLayout inflater.inflate(R.layout.favorite, group, false);
    	View favApp = (View) inflater.inflate(R.layout.favorite, group, false);
        final ImageView appIcon = (ImageView)favApp.findViewById(R.id.fav_app_icon);
        appIcon.setImageDrawable(info.icon);

        final TextView textView = (TextView) favApp.findViewById(R.id.app_text);
        textView.setText(info.title);
        info.icon.setBounds(0, 0, appIcon.getWidth(), appIcon.getHeight());
        favApp.setTag(info.intent);
        favApp.setOnClickListener(this);
        favApp.setOnLongClickListener(this);
        
        return favApp;
    }
    
    /**
     * Sets the list of favorites.
     *
     * @param applications the applications to put in the favorites area
     */
    public void setFavorites(List<ApplicationInfo> applications) {
        mFavorites = applications;
        requestLayout();
    }

    /**
     * Sets the list of recents.
     *
     * @param applications the applications to put in the recents area
     */
    public void setRecents(List<ApplicationInfo> applications) {
        mRecents = applications;
        requestLayout();
    }

    public void onClick(View v) {
        ApplicationNavigator.startActivitySafely(getContext(), (Intent) v.getTag());
    }
    


    public boolean onLongClick(View v) {
		
		Intent appIntent = (Intent)v.getTag();
		
		
		
		final Context context = getContext();
		final String apkName = appIntent.getComponent().getPackageName();
		PackageManager pm = context.getPackageManager();
		
		String nameOfApp = "";
		String appVer = "1.00";
		Drawable appIcon = null;
		
		try {
			appIcon = pm.getActivityIcon(appIntent);
			android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(apkName, 0);
			
			nameOfApp = pm.getApplicationLabel(appInfo).toString();
			
			// Get Application Version.
			PackageInfo pkgInfo = pm.getPackageInfo(apkName, 0);
			appVer = pkgInfo.versionName;
		} catch (NameNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		ApplicationNavigator.showItemSelection(context, nameOfApp, apkName, appIcon, appVer, appIntent);
		
		
		return false;
	}
}
