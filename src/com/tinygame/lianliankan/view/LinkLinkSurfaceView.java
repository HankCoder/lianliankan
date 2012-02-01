package com.tinygame.lianliankan.view;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import com.tinygame.lianliankan.R;
import com.tinygame.lianliankan.ThemeManager;
import com.tinygame.lianliankan.config.Env;
import com.tinygame.lianliankan.engine.BlankRoute;
import com.tinygame.lianliankan.engine.Chart;
import com.tinygame.lianliankan.engine.ConnectiveInfo;
import com.tinygame.lianliankan.engine.Direction;
import com.tinygame.lianliankan.engine.DirectionPath;
import com.tinygame.lianliankan.engine.Hint;
import com.tinygame.lianliankan.engine.Tile;
import com.tinygame.lianliankan.utils.AssetsImageLoader;
import com.tinygame.lianliankan.utils.ImageSplitUtils;
import com.tinygame.lianliankan.utils.SoundEffectUtils;

public class LinkLinkSurfaceView extends SurfaceView implements Callback {
    private static final String TAG = "LinkLinkSurfaceView";

    public static interface LLViewActionListener {
        void onNoHintToConnect();
        void onFinishOnTime();
        void onDismissTouch();
    }
    
    private static final int PADDING_LEFT = 0;
    private static final int REFRESH_DELAY = 300;
    
    private Context mContext;
    private Paint mPaintHint;
    private Paint mPaintPic;
    private Paint mPaintSelect;
    private Paint mPaintPath;
    private Paint mPaintDismissing;
    private int mStartX;
    private int mStartY;
    
    private Random mRandom;
    private int mCurBackgroundIndex;
    private ArrayList<Bitmap> mBackgroundBtList;
    private Bitmap mLightHBt;
    private Bitmap mLightVBt;
    private Drawable mSelectorDrawable;
    private Drawable mHintDrawable;
    private ArrayList<ArrayList<Point>> mLinePoints = null;
    
    private SurfaceHolder mHolder;
    
    private LLViewActionListener mLLViewActionListener;
    
    private Chart mChart;
    private LinkedList<BlankRoute> mRoutes = new LinkedList<BlankRoute>();
    private Tile[] mHint;
    private Tile mSelectTileCur;
    private Tile mSelectTileOne;
    private Tile mSelectTileTwo;
    
    private boolean mCurRoundClipTile;
    private boolean mTileDataChanged = true;
    private boolean mShowTouch;
    private boolean mShowHint;
    private boolean mForceRefresh;
    private int mTileDataChangedCount;
    
    private DrawTread mDrawTread;
    
    public Runnable mRefreshRunnable = new Runnable() {
        public void run() {
            try {
                mTileDataChanged = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public LinkLinkSurfaceView(Context context) {
        super(context);
        init(context);
    }

    public LinkLinkSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LOGD("[[surfaceChanged]] <<<<<<<< >>>>>>>>");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LOGD("[[surfaceCreated]] <<<<<<<< >>>>>>>>");
        if (mDrawTread != null) {
            mDrawTread.mRunning = false;
            mDrawTread = null;
        }
        
        mDrawTread = new DrawTread();
        mDrawTread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LOGD("[[surfaceDestroyed]] <<<<<<<< >>>>>>>>");
        if (mDrawTread != null) {
            mDrawTread.mRunning = false;
            mDrawTread = null;
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!Env.ICON_REGION_INIT || mChart == null) {
            return true;
        }
        int xPicIndex = (int) ((event.getX() - mStartX) / Env.ICON_WIDTH);
        int yPicIndex = (int) ((event.getY() - mStartY) / Env.ICON_WIDTH);
        if (xPicIndex >= mChart.xSize || xPicIndex < 0 || yPicIndex >= mChart.ySize || yPicIndex < 0) {
            return false;
        }
        LOGD("[[onTouch]] click = (x, " + event.getX() + " y " + event.getY()
                + " ) and index = (x, " + xPicIndex + " y " + yPicIndex + ") >>>>>>>>");
        
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            LOGD("[[onTouchEvent::Action_down]] >>>>>>>>>>");
            Tile touchTile = mChart.getTile(xPicIndex, yPicIndex);
            if (touchTile.isBlank() == false) {
                mHint = null;
                SoundEffectUtils.getInstance().playClickSound();
                handlerTileSelect(touchTile);
            }
            break;
        case MotionEvent.ACTION_UP:
//            this.postDelayed(mRefreshRunnable, REFRESH_DELAY);
            break;
        }
        return true;
    }

    public void setLLViewActionListener(LLViewActionListener l) {
        mLLViewActionListener = l;
    }
    
    public Chart getChart() {
        return mChart;
    }
    
    public void setChart(Chart chart) {
        mChart = chart;
    }
    
    public void changeBackground() {
        int random = mRandom.nextInt(100);
        mCurBackgroundIndex = random % mBackgroundBtList.size();
    }
    
    public void showHint(Tile[] hint) {
        mHint = hint;
        mShowHint = true;
    }
    
    public void forceRefresh() {
        LOGD("[[forceRefresh]] <<<<>>>> <<<<>>>>>");
        mForceRefresh = true;    
    }
    
    private void handlerTileSelect(Tile newTile) {
        if (mSelectTileCur == null) {
            mSelectTileCur = newTile;
        } else {
            if (mSelectTileCur == newTile) {
                return;
            } else {
                if (mSelectTileCur.getImageIndex() == newTile.getImageIndex()) {
                    ConnectiveInfo ci = mChart.connectvie(mSelectTileCur, newTile);
                    if (ci.getResult()) {
                        SoundEffectUtils.getInstance().playDisapperSound();
                        
                        if (mLLViewActionListener != null) {
                            mLLViewActionListener.onDismissTouch();
                        }
                        
                        mSelectTileOne = mSelectTileCur;
                        mSelectTileTwo = newTile;
                        mSelectTileCur = null;
                        mRoutes.add(ci.getRoute().dismissing());
                        synchronized (LinkLinkSurfaceView.this) {
                            mTileDataChangedCount++;
                            mTileDataChanged = true;
                        }
                        return ;
                    } else {
                        mSelectTileCur = newTile;
                    }
                } else {
                    mSelectTileCur = newTile;
                }
            }
        }
        mShowTouch = true;
    }
    
    private void doRoutes() {
        LOGD("[[doRoutes]] >>>>>> <<<<<<<");
        try {
            BlankRoute blankRoute = mRoutes.removeFirst();
            blankRoute.start.dismiss();
            blankRoute.end.dismiss();
            if (blankRoute.start == mSelectTileCur || blankRoute.end == mSelectTileCur) {
                mSelectTileCur = null;
            }
            if (mChart.isAllBlank()) {
                if (mLLViewActionListener != null) {
                    mLLViewActionListener.onFinishOnTime();
                }
            } else {
                Tile[] hint = new Hint(getChart()).findHint();
                if (hint == null && mLLViewActionListener != null) {
                    mLLViewActionListener.onNoHintToConnect();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private class DrawTread extends Thread {
        private boolean mRunning;
        
        DrawTread() {
            mRunning = true;
        }
        
        public void run() {
            while (mRunning) {
                if (mTileDataChanged || mShowHint 
                        || mForceRefresh
                        || mShowTouch) {
                    if (mShowHint || mForceRefresh || mShowTouch) {
                        Canvas canvas = mHolder.lockCanvas();
                        try {
                            if (canvas != null) {
                                try {
                                    if (mRoutes != null && mRoutes.size() > 0) {
                                        BlankRoute blankRoute = mRoutes.removeFirst();
                                        blankRoute.start.dismiss();
                                        blankRoute.end.dismiss();
                                    }
                                } catch (Exception e) {
                                }
                                
                                onDrawFullView(canvas);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (canvas != null) {
                                mHolder.unlockCanvasAndPost(canvas);
                            }
                        }
                    }
                    
                    if (mSelectTileOne != null 
                            && mSelectTileTwo != null
                            && mTileDataChanged) {
                        onDrawBoom();
                        mSelectTileOne = null;
                        mSelectTileTwo = null;
                        
                        doRoutes();
                        Canvas canvas_temp = mHolder.lockCanvas();
                        try {
                            if (canvas_temp != null) {
                                onDrawFullView(canvas_temp);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (canvas_temp != null) {
                                mHolder.unlockCanvasAndPost(canvas_temp);
                            }
                        }
                    }
                    
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    mShowTouch = false;
                    mForceRefresh = false;
                    mShowHint = false;
//                    mTileDataChanged = false;
                    synchronized (LinkLinkSurfaceView.this) {
                        if (mTileDataChangedCount > 0) {
                            mTileDataChangedCount--;
                        }
                        if (mTileDataChangedCount == 0) {
                            mTileDataChanged = false;
                        }
                    }
                }
            }
        }
    }
    
    private void init(Context context) {
        mContext = context;
        
        mPaintHint = new Paint();
        mPaintHint.setColor(Color.RED);
        mPaintHint.setStrokeWidth(4);
        mPaintHint.setStyle(Paint.Style.STROKE);
        
        mPaintSelect = new Paint();
        mPaintSelect.setColor(Color.RED);
        mPaintSelect.setStrokeWidth(3);
        mPaintSelect.setStyle(Paint.Style.STROKE);
        
        mPaintPath = new Paint();
        mPaintPath.setColor(Color.RED);
        mPaintPath.setStrokeWidth(6);
        mPaintPath.setAlpha(150);
        
        mPaintDismissing = new Paint();
        mPaintDismissing.setAlpha(80);
        
        mBackgroundBtList = new ArrayList<Bitmap>();
        mBackgroundBtList.add(AssetsImageLoader.loadBitmapFromAsset(mContext, "background/game_bg"));
        mBackgroundBtList.add(AssetsImageLoader.loadBitmapFromAsset(mContext, "background/game_bg1"));
        mBackgroundBtList.add(AssetsImageLoader.loadBitmapFromAsset(mContext, "background/game_bg2"));
        mCurBackgroundIndex = 0;
        mRandom = new Random();
        
        mHintDrawable = mContext.getResources().getDrawable(R.drawable.hint_icon);
        mSelectorDrawable = mContext.getResources().getDrawable(R.drawable.selector);
        
        mLightHBt = AssetsImageLoader.loadBitmapFromAsset(mContext, "image/light_h");
        mLightVBt = AssetsImageLoader.loadBitmapFromAsset(mContext, "image/light_v");
        
        getHolder().addCallback(this);
        mHolder = getHolder();
    }
    
    private void onDrawBoom() {
        ArrayList<Bitmap> ret = ImageSplitUtils.getInstance().getBoomBtList();
        LOGD(">>>>> draw boom >>>>>>>");
        mLinePoints = null;
        for (Bitmap bt : ret) {
            Canvas canvas = mHolder.lockCanvas();
            LOGD(">>>>> draw boom for bt = " + bt + ">>>>>>>");
            try {
                onDrawFullView(canvas);
                
                Rect src = new Rect(0, 0, bt.getWidth(), bt.getHeight());
                Rect dest = new Rect(mStartX + (mSelectTileOne.x) * Env.ICON_WIDTH
                                            , mStartY + (mSelectTileOne.y) * Env.ICON_WIDTH
                                            , mStartX + (mSelectTileOne.x + 1) * Env.ICON_WIDTH
                                            , mStartY + (mSelectTileOne.y + 1) * Env.ICON_WIDTH);
                canvas.drawBitmap(bt, src, dest, mPaintPic);
                        
                Rect destTwo = new Rect(mStartX + (mSelectTileTwo.x) * Env.ICON_WIDTH
                                            , mStartY + (mSelectTileTwo.y) * Env.ICON_WIDTH
                                            , mStartX + (mSelectTileTwo.x + 1) * Env.ICON_WIDTH
                                            , mStartY + (mSelectTileTwo.y + 1) * Env.ICON_WIDTH);
                canvas.drawBitmap(bt, src, destTwo, mPaintPic);
                        
                onDrawPathLine(canvas);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
            try {
                Thread.sleep(20);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        
        LOGD(">>>>> draw boom finish  >>>>>>>");
    }
    
    private void onDrawPathLine(Canvas canvas) {
        //test code 
        if (mLinePoints == null) {
            mLinePoints = new ArrayList<ArrayList<Point>>();
            for (BlankRoute eachRoute : mRoutes) {
                LOGD("[[onDrawPathLine]] on entry into onDrawPathLine <<<<>>>>>");
                ArrayList<Point> src = new ArrayList<Point>();
                Point mStartTileSildePointOne = null;
                Point mStartTileSildePointTwo = null;
                Point mEndTileSildePointOne = null;
                Point mEndTileSildePointTwo = null;
                for (DirectionPath each : eachRoute.getpath()) {
                    Tile eachTile = each.getTile();
                    int xPoint = mStartX + (eachTile.x) * Env.ICON_WIDTH + Env.ICON_WIDTH / 2;
                    int yPoint = mStartY + (eachTile.y) * Env.ICON_WIDTH + Env.ICON_WIDTH / 2;
                    src.add(new Point(xPoint, yPoint));
    
                    LOGD("[[onDrawPathLine]] src size = " + src.size()
                            + " eachRoute.getpath() size = " + eachRoute.getpath().length
                            + " >>>>>>>>>>><<<<<<<<<<");
                    
                    for (Direction eachDirection : each.getDirection()) {
    //                    canvas.drawLine(xPoint, yPoint, xPoint + eachDirection.padding(true, Env.ICON_WIDTH),
    //                            yPoint + eachDirection.padding(false, Env.ICON_WIDTH), mPaintPath);
                        Point curPoint = new Point(xPoint + eachDirection.padding(true, Env.ICON_WIDTH),
                                yPoint + eachDirection.padding(false, Env.ICON_WIDTH));
                        if (src.size() == 1) {
                            if (mStartTileSildePointOne == null) {
                                mStartTileSildePointOne = curPoint;
                            } else if (mStartTileSildePointTwo == null) {
                                mStartTileSildePointTwo = curPoint;
                            }
                        } else if (src.size() == eachRoute.getpath().length) {
                            if (mEndTileSildePointOne == null) {
                                mEndTileSildePointOne = curPoint;
                            } else if (mEndTileSildePointTwo == null) {
                                mEndTileSildePointTwo = curPoint;
                            }
                        }
                        
                        LOGD("[[onDrawPathLine]] cutPoint info = " + curPoint.toString() + " >>>>>>><<<<<<");
                    }
                }
                
                LOGD("[[onDrawPathLine]] >>>>> src point data = " + src + " >>>>>><<<<<<<");
                
                if (src.size() == 1) {
                    src.add(0, mStartTileSildePointOne);
                    src.add(mStartTileSildePointTwo);
                } else {
                    if (mStartTileSildePointOne != null && !pointInPath(mStartTileSildePointOne, src)) {
                        src.add(0, mStartTileSildePointOne);
                    }
                    if (mStartTileSildePointTwo != null && !pointInPath(mStartTileSildePointTwo, src)) {
                        src.add(0, mStartTileSildePointTwo);
                    }
                    if (mEndTileSildePointOne != null && !pointInPath(mEndTileSildePointOne, src)) {
                        src.add(mEndTileSildePointOne);
                    }
                    if (mEndTileSildePointTwo != null && !pointInPath(mEndTileSildePointTwo, src)) {
                        src.add(mEndTileSildePointTwo);
                    }
                }
                
                ArrayList<Point> drawPoint = new ArrayList<Point>();
                if (src.size() > 2) {
                    drawPoint.add(src.get(0));
                    drawPoint.add(src.get(1));
                }
                for (int i = 2; i < src.size(); ++i) {
                    int drawSize = drawPoint.size();
                    if (pointInExternLine(src.get(i), drawPoint.get(drawSize - 1), drawPoint.get(drawSize - 2))) {
                        drawPoint.remove(drawSize - 1);
                        drawPoint.add(src.get(i));
                    } else {
                        drawPoint.add(src.get(i));
                    }
                }
                
                if (drawPoint.size() > 1) {
                    for (int i = 1; i < drawPoint.size(); ++i) {
                        Point start = drawPoint.get(i - 1);
                        Point end = drawPoint.get(i);
                        drawLight(canvas, start, end);
                    }
                    
                    mLinePoints.add(drawPoint);
                }
                LOGD("[[onDrawPathLine]] mEndTileSildePointOne = " + mEndTileSildePointOne
                        + " mEndTileSildePointTwo = " + mEndTileSildePointTwo
                        + " mStartTileSildePointOne = " + mStartTileSildePointOne
                        + " mStartTileSildePointTwo = " + mStartTileSildePointTwo
                        + " array Point list = " + src.toString()
                        + " >>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<<<"
                        + " drawPoint = " + drawPoint.toString());
            }
        } else {
            for (ArrayList<Point> list : mLinePoints) {
                if (list.size() > 1) {
                    for (int i = 1; i < list.size(); ++i) {
                        Point start = list.get(i - 1);
                        Point end = list.get(i);
                        drawLight(canvas, start, end);
                    }                    
                }
            }
        }
    }
    
    private void drawLight(Canvas canvas, Point p1, Point p2) {
        if (p1 != null && p2 != null) {
            if (p1.x == p2.x) {
                int miny = Math.min(p1.y, p2.y);
                int maxy = Math.max(p1.y, p2.y);
                int width = mLightVBt.getWidth();
                int height = mLightVBt.getHeight();
                Rect src = new Rect(0, 0, width, height);
                Rect dest = new Rect(p1.x - width / 2, miny, p1.x + width / 2, maxy);
                canvas.drawBitmap(mLightVBt, src, dest, mPaintPic);
            } else if (p1.y == p2.y) {
                int minx = Math.min(p1.x, p2.x);
                int maxx = Math.max(p1.x, p2.x);
                int width = mLightHBt.getWidth();
                int height = mLightHBt.getHeight();
                Rect src = new Rect(0, 0, width, height);
                Rect dest = new Rect(minx, p1.y - height / 2, maxx, p1.y + height / 2);
                canvas.drawBitmap(mLightHBt, src, dest, mPaintPic);
            }
        }
    }
    
    private boolean pointInExternLine(Point point, Point p1, Point p2) {
        if (point == null || p1 == null || p2 == null) {
            return false;
        }
        if (p1.x == p2.x  && point.x == p1.x) {
            return true;
        }
        if (p1.y == p2.y && point.y == p1.y) {
            return true;
        }
        
        return false;
    }
    
    private boolean pointInPath(Point point, ArrayList<Point> path) {
        if (point == null || path == null || path.size() < 2) {
            return false;
        }
        for (int i = 1; i < path.size(); ++i) {
            if (pointOnLine(point, path.get(i - 1), path.get(i))) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean pointOnLine(Point checkPoint, Point point1, Point point2) {
        if (point1.x == point2.x && checkPoint.x == point1.x) {
            int minY = Math.min(point1.y, point2.y);
            int maxY = Math.max(point1.y, point2.y);
            if (checkPoint.y > minY && checkPoint.y < maxY) {
                return true;
            }
            return false;
        } else if (point1.y == point2.y && checkPoint.y == point1.y) {
            int minX = Math.min(point1.x, point2.x);
            int maxX = Math.max(point1.x, point2.x);
            if (checkPoint.x > minX && checkPoint.x < maxX) {
                return true;
            }
            return false;
        }
        
        return false;
    }
    
    private void onDrawFullView(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        mCurRoundClipTile = false;
        LOGD("[[onDrawFullView]] entry into  >>>>>>> width = " + width + " height = " + height
                + " canvas = " + canvas);
        canvas.drawColor(Color.BLACK);
        
        Bitmap curBg = mBackgroundBtList.get(mCurBackgroundIndex);
        
        if (curBg != null) {
            Rect bgSrc = new Rect(0, 0, curBg.getWidth(), curBg.getHeight());
            Rect bgDest = new Rect(0, 0, width, height);
            canvas.drawBitmap(curBg, bgSrc, bgDest, mPaintPic);
        }
        
        if (mChart == null) {
            LOGD("[[onDrawFullView]] return because chart invalid!");
            return;
        }

        LOGD("[[onDrawFullView]] before check init  >>>>>>>");
        if (!Env.ICON_REGION_INIT) {
            int sideLength = Math.min(width, height) - 2 * PADDING_LEFT;
            Env.ICON_REGION_INIT = true;
            Env.ICON_WIDTH = sideLength / mChart.xSize;
            LOGD("[[onDraw]] sideLength = "+ sideLength + " icon size = " + mChart.xSize 
                    + " icon width = " + Env.ICON_WIDTH + " >>>>>>>>>>>>>>>>>");
            int imageWidth = ImageSplitUtils.getInstance().getCurrentImageWidth();
            if (imageWidth < Env.ICON_WIDTH) {
//                Env.ICON_WIDTH = imageWidth;
                mStartX = (width - (mChart.xSize * Env.ICON_WIDTH)) / 2;
                mStartY = (height - (mChart.ySize * Env.ICON_WIDTH)) / 2;
            } else {
                mStartX = PADDING_LEFT;
                mStartY = (height - (mChart.ySize * Env.ICON_WIDTH)) / 2;
            }
        }
        
        LOGD("[[onDrawFullView]] before draw all tile  >>>>>>>");

        for (int yIndex = 0; yIndex < mChart.ySize; yIndex++) {
            for (int xIndex = 0; xIndex < mChart.xSize; xIndex++) {
                try {
                    Tile tileTemp = mChart.getTile(xIndex, yIndex);
                    Drawable drawable = ThemeManager.getInstance().getImage(mChart.getTile(xIndex, yIndex).getImageIndex());
                    if (tileTemp != mSelectTileTwo && tileTemp != mSelectTileOne && drawable != null) {
                        drawable.setBounds(mStartX + xIndex * Env.ICON_WIDTH
                                        , mStartY + yIndex * Env.ICON_WIDTH
                                        , mStartX + (xIndex + 1) * Env.ICON_WIDTH
                                        , mStartY + (yIndex + 1) * Env.ICON_WIDTH);
                        drawable.draw(canvas);
                    } else {
                        mCurRoundClipTile = true;
                    }
                } catch (Exception e) {
                }
            }
        }

        
        LOGD("[[onDrawFullView]] before draw all selector tile  >>>>>>>");
        if (mSelectTileCur != null) {
            mSelectorDrawable.setBounds(mStartX + (mSelectTileCur.x) * Env.ICON_WIDTH
                        , mStartY + (mSelectTileCur.y) * Env.ICON_WIDTH
                        , mStartX + (mSelectTileCur.x + 1) * Env.ICON_WIDTH
                        , mStartY + (mSelectTileCur.y + 1) * Env.ICON_WIDTH);
            mSelectorDrawable.draw(canvas);
        }

        LOGD("[[onDrawFullView]] before draw all Hint tile  >>>>>>>");
        if (null != mHint) {
            boolean blank = false;

            for (Tile tile : mHint) {
                if (tile.isBlank()) {
                    blank = true;
                }
            }
            if (blank == false) {
                for (Tile tile : mHint) {
                    mHintDrawable.setBounds(mStartX + tile.x * Env.ICON_WIDTH
                                , mStartY + tile.y * Env.ICON_WIDTH
                                , mStartX + (tile.x + 1) * Env.ICON_WIDTH
                                , mStartY + (tile.y + 1) * Env.ICON_WIDTH);
                    mHintDrawable.draw(canvas);
                }
            }
        }
        
        LOGD("[[onDrawFullView]] leva <<<<<<< width = " + width + " height = " + height);
    }

    private void LOGD(String msg) {
        if (com.tinygame.lianliankan.config.Config.DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
