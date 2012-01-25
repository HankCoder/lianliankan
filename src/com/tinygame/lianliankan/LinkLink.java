package com.tinygame.lianliankan;

import net.youmi.android.appoffers.AppOffersManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.tinygame.lianliankan.config.Config;
import com.tinygame.lianliankan.config.Env;
import com.tinygame.lianliankan.engine.Chart;
import com.tinygame.lianliankan.engine.FillContent;
import com.tinygame.lianliankan.engine.Hint;
import com.tinygame.lianliankan.engine.Tile;
import com.tinygame.lianliankan.utils.ImageSplitUtils;
import com.tinygame.lianliankan.utils.SoundEffectUtils;
import com.tinygame.lianliankan.view.LevelView;
import com.tinygame.lianliankan.view.LevelView.LevelChangedListener;
import com.tinygame.lianliankan.view.LinkLinkSurfaceView;
import com.tinygame.lianliankan.view.LinkLinkSurfaceView.LLViewActionListener;
import com.tinygame.lianliankan.view.TimeProgressView;
import com.tinygame.lianliankan.view.TimeProgressView.TimeProgressListener;

public class LinkLink extends Activity implements LLViewActionListener
                                            , TimeProgressListener
                                            , AnimationListener
                                            , LevelChangedListener {
    private static final String TAG = "LinkLink";
    
    private LinkLinkSurfaceView mLLView;
    private View newGameButton, arrangeButton, hintButton;
    private View mNoMoreTipsView;
    private TextView mArrangeCount;
    private TextView mHintCount;
    private View mNext;
    private TimeProgressView mTimeView;
    private LevelView mLevelView;
    private int mCurrentTimeProgress;
    private LayoutInflater mInflater;
    private Dialog mWinDialog;
    private Dialog mLoseDialog;
    private Dialog mResetDialog;
    private Dialog mDownloadDialog;
    private int mCurDiffArrangeCount;
    private int mCurDiffHintCount;
    private boolean mFinishDialogShow;
    private boolean mAppDownloadShow;
    
    private static final int PLAY_READY_SOUND = 0;
    private static final int PLAY_BACKGROUND_SOUND = 1;
    private static final int START_PROGRESS_TIME_VIEW = 2;
    private static final int RESET_PROGRESS_TIME_VIEW = 3;
    private static final int SHOW_FINISH_ONE_TIME = 4;
    private static final int SHOW_FAILED_DIALOG = 5;
    private static final int SHOW_NO_MORE_TIPS = 6;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case PLAY_READY_SOUND:
                {
                    boolean soundOpen = SettingManager.getInstance().getSoundOpen();
                    if (soundOpen) {
                        SoundEffectUtils.getInstance().playReadySound();
                    }
                }
                break;
            case PLAY_BACKGROUND_SOUND:
                boolean soundOpen = SettingManager.getInstance().getSoundOpen();
                if (soundOpen) {
                    SoundEffectUtils.getInstance().playSpeedSound();
                }
                break;
            case START_PROGRESS_TIME_VIEW:
                mTimeView.setTotalTime(mCurrentTimeProgress);
                mTimeView.startProgress();
                break;
            case RESET_PROGRESS_TIME_VIEW:
                mTimeView.reset();
                break;
            case SHOW_FINISH_ONE_TIME:
                showFinishDialog();
                break;
            case SHOW_FAILED_DIALOG:
                showFailedDialog();
                break;
            case SHOW_NO_MORE_TIPS:
                showNoMoreTipsView();
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN ,    
                WindowManager.LayoutParams.FLAG_FULLSCREEN);    
        SettingManager.getInstance().init(getApplicationContext());
        SoundEffectUtils.getInstance().init(this);
        ImageSplitUtils.getInstance().init(this);
        ThemeManager.getInstance().init(this);
        Env.ICON_REGION_INIT = false;
        
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        resetContent();
        mHandler.sendEmptyMessageDelayed(PLAY_BACKGROUND_SOUND, 500);
        
        AppOffersManager.init(this, Config.APP_ID, Config.APP_SECRET_KEY, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAppDownloadShow = false;
        
        checkAppPoint();
        reloadCurrentLevel();
        updateToolsCount();
    }

    private void checkAppPoint() {
        if (!Config.DEBUG_CLOSE_APP_DOWNLOAD) {
            int level = Categary_diff_selector.getInstance().getCurrentLevel();
            int point = AppOffersManager.getPoints(this);
            if (point < Config.POINT && level >= Config.APP_DOWNLOA_SHOW_LEVEL) {
                showCountDownloadDialog();
            }
        }
    }
    
    private void updateToolsCountView() {
        if (mArrangeCount != null) {
            mArrangeCount.setText(String.valueOf(mCurDiffArrangeCount)); 
        }
        if (mHintCount != null) {
            mHintCount.setText(String.valueOf(mCurDiffHintCount)); 
        }         
    }
    
    private void updateToolsCount() {
        mCurDiffArrangeCount = Categary_diff_selector.getInstance().getCurrentDiffArrange();
        mCurDiffHintCount = Categary_diff_selector.getInstance().getCurrentDiffHint();
    }
    
    public void resetContent() {
        setContentView(R.layout.main);
        mLLView = (LinkLinkSurfaceView) findViewById(R.id.llk);
        mLLView.setLLViewActionListener(this);
        
//        reloadCurrentLevel();

        newGameButton = findViewById(R.id.newGame);
        arrangeButton = findViewById(R.id.arrange);
        hintButton = findViewById(R.id.hint);
        mNext = findViewById(R.id.next);
        mTimeView = (TimeProgressView) findViewById(R.id.time);
        mTimeView.setTimeProgressListener(this);
        mLevelView = (LevelView) findViewById(R.id.level);
        mLevelView.setLevelChangedListener(this);
        mLevelView.setLevel(Categary_diff_selector.getInstance().getCurrentLevel());
        mArrangeCount = (TextView) findViewById(R.id.arrage_count);
        mHintCount = (TextView) findViewById(R.id.hint_count);
        
        mNoMoreTipsView = findViewById(R.id.no_more_tips);
        
        newGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Env.ICON_REGION_INIT = false;
                reloadCurrentLevel();
            }
        });
        arrangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurDiffArrangeCount == 0) {
                    return;
                }
                
                Chart chart = mLLView.getChart();
                chart.reArrange();
                
                Tile[] hint = new Hint(chart).findHint();
                if (hint == null) {
                    noMoreConnectChanged();
                }
                
                mLLView.forceRefresh();
                if (mCurDiffArrangeCount > 0) {
                    mCurDiffArrangeCount--;
                }
                updateToolsCountView();
            }
        });
        hintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurDiffHintCount == 0) {
                    return;
                }
                
                Tile[] hint = new Hint(mLLView.getChart()).findHint();
                mLLView.showHint(hint);
                
                if (mCurDiffHintCount > 0) {
                    mCurDiffHintCount--;
                }
                updateToolsCountView();
            }
        });
        mNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryUpdateDiffAndCategory();
            }
        });
//        mNext.setVisibility(View.GONE);
    }
    
    @Override
    public void onStop() {
        super.onStop();
        LOGD("[[onStop]]");
        boolean soundOpen = SettingManager.getInstance().getSoundOpen();
        if (soundOpen) {
            SoundEffectUtils.getInstance().stopSpeedSound();
        }
        Categary_diff_selector.getInstance().saveCurretInfo();
//        SettingManager.getInstance().setOpenLevel(Categary_diff_selector.getInstance().getCurrentLevel());
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        LOGD("[[onDestroy]]");
        mHandler.sendEmptyMessage(RESET_PROGRESS_TIME_VIEW);
        Categary_diff_selector.getInstance().saveCurretInfo();
//        SettingManager.getInstance().setOpenLevel(Categary_diff_selector.getInstance().getCurrentLevel());
    }

    @Override
    public void onNoHintToConnect() {
        noMoreConnectChanged();
    }
    
    @Override
    public void onDismissTouch() {
        if (mTimeView != null) {
            mTimeView.onDissmisTouch();
        }
    }
    
    private void noMoreConnectChanged() {
        mHandler.removeMessages(SHOW_NO_MORE_TIPS);
        Chart chart = mLLView.getChart();
        chart.reArrange();
        Tile[] hint = new Hint(chart).findHint();
        if (hint != null) {
            mHandler.sendEmptyMessage(SHOW_NO_MORE_TIPS);
        } else {
            noMoreConnectChanged();
        }
    }

    @Override
    public void onFinishOnTime() {
        mFinishDialogShow = true;
        mHandler.sendEmptyMessage(SHOW_FINISH_ONE_TIME);
    }
    
    @Override
    public void onLevelChanged(int level) {
        this.checkAppPoint();
    }
    
    private void showCountDownloadDialog() {
        mDownloadDialog = new AlertDialog.Builder(this).setTitle("提示")
                .setMessage(getString(R.string.download_tips))
                .setPositiveButton("下载", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        AppOffersManager.showAppOffers(LinkLink.this);
                        if (mDownloadDialog != null) {
                            mDownloadDialog.dismiss();
                            mDownloadDialog = null;
                        }
                        mAppDownloadShow = false;
                    }
                }).setNegativeButton("取消", 
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).create();
        mDownloadDialog.setCancelable(false);
        mDownloadDialog.show();
        mAppDownloadShow = true;
    }
    
    private void showNoMoreTipsView() {
        if (mNoMoreTipsView.getVisibility() == View.GONE) {
            mNoMoreTipsView.setVisibility(View.VISIBLE);
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.no_more_tips_anim);
            anim.setAnimationListener(this);
            mNoMoreTipsView.startAnimation(anim);
        }
    }
    
    private void showFinishDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View showView = mInflater.inflate(R.layout.win_view, null);
        View next = showView.findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryUpdateDiffAndCategory();
                if (mWinDialog != null) {
                    mFinishDialogShow = false;
                    mWinDialog.dismiss();
                    mWinDialog = null;
                }
            }
        });
        
        View retry = showView.findViewById(R.id.retry);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Env.ICON_REGION_INIT = false;
                reloadCurrentLevel();   
                if (mWinDialog != null) {
                    mFinishDialogShow = false;
                    mWinDialog.dismiss();
                    mWinDialog = null;
                }
            }
        });
        
        View quit = showView.findViewById(R.id.quit);
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWinDialog != null) {
                    mFinishDialogShow = false;
                    mWinDialog.dismiss();
                    mWinDialog = null;
                }
                finish();
            }
        });
        
        builder.setView(showView);
        mWinDialog = builder.create();
        mWinDialog.setCancelable(false);
        mWinDialog.show();
        mHandler.removeMessages(START_PROGRESS_TIME_VIEW);
        mHandler.sendEmptyMessage(RESET_PROGRESS_TIME_VIEW);   
    }
    
    private void showFailedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View showView = mInflater.inflate(R.layout.lose_view, null);
        View next = showView.findViewById(R.id.retry);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Env.ICON_REGION_INIT = false;
                reloadCurrentLevel();
                if (mLoseDialog != null) {
                    mLoseDialog.dismiss();
                    mLoseDialog = null;
                }
            }
        });
        View quit = showView.findViewById(R.id.quit);
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoseDialog != null) {
                    mLoseDialog.dismiss();
                    mLoseDialog = null;
                }
                finish();
            }
        });
        
        builder.setView(showView);
        mLoseDialog = builder.create();
        mLoseDialog.setCancelable(false);
        mLoseDialog.show();
    }
    
    private void reloadCurrentLevel() {
        String diff = Categary_diff_selector.getInstance().getCurrentDiff();
        String cate = Categary_diff_selector.getInstance().getCurrentCategary();
        LOGD("[[reloadCurrentLevel]] diff = " + diff + " cate = " + cate + " >>>>>>>>>");
        if (cate != null && diff != null) {
            ThemeManager.getInstance().loadImageByCategary(cate);
            Chart c = new Chart(FillContent.getRandomWithDiff(diff
                                , ThemeManager.getInstance().getCurrentImageCount() - 1));
            mLLView.setChart(c);
            mCurrentTimeProgress = Categary_diff_selector.getInstance().getCurrentTime();
            mLevelView.setLevel(Categary_diff_selector.getInstance().getCurrentLevel());

            mHandler.sendEmptyMessage(RESET_PROGRESS_TIME_VIEW);
            if (!mAppDownloadShow) {
                mHandler.sendEmptyMessageDelayed(PLAY_READY_SOUND, 200);
                mHandler.removeMessages(START_PROGRESS_TIME_VIEW);
                mHandler.sendEmptyMessageDelayed(START_PROGRESS_TIME_VIEW, 1000);
            }
            mLLView.forceRefresh();
            
            updateToolsCount();
            updateToolsCountView();
        } else {
            showResetGameDialog();
        }
    }
    
    private void showResetGameDialog() {
        LOGD("[[showResetGameDialog]] >>>>>>>>>>>>>>>");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View showView = mInflater.inflate(R.layout.reset_game_view, null);
        View next = showView.findViewById(R.id.retry);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingManager.getInstance().setLastCategory(0);
                SettingManager.getInstance().setLastDiff(0);
                Categary_diff_selector.getInstance().restDiff();
                Categary_diff_selector.getInstance().resetCategory();
                Env.ICON_REGION_INIT = false;
                reloadCurrentLevel();
                if (mResetDialog != null) {
                    mResetDialog.dismiss();
                    mResetDialog = null;
                }
            }
        });
        View quit = showView.findViewById(R.id.quit);
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mResetDialog != null) {
                    mResetDialog.dismiss();
                    mResetDialog = null;
                }
                finish();
            }
        });
        
        builder.setView(showView);
        mResetDialog = builder.create();
        mResetDialog.setCancelable(false);
        mResetDialog.show();
    }
    
    private void tryUpdateDiffAndCategory() {
        String diff = Categary_diff_selector.getInstance().updateDiff();
        String cate = Categary_diff_selector.getInstance().getCurrentCategary();
        LOGD("tryUpdateDiffAndCategory >>>>> diff = " + diff + " cate = " + cate + " >>>>>>>");
        if (diff == null) {
            cate = Categary_diff_selector.getInstance().updateCategory();
            if (cate != null) {
                Categary_diff_selector.getInstance().restDiff();
                diff = Categary_diff_selector.getInstance().getCurrentDiff();
            }
        }
        if (diff != null && cate != null) {
            Env.ICON_REGION_INIT = false;
            ThemeManager.getInstance().loadImageByCategary(cate);
            Chart c = new Chart(FillContent.getRandomWithDiff(diff
                                , ThemeManager.getInstance().getCurrentImageCount() - 1));
            mLLView.setChart(c);
            mLLView.forceRefresh();
            mCurrentTimeProgress = Categary_diff_selector.getInstance().getCurrentTime();
            mLevelView.setLevel(Categary_diff_selector.getInstance().getCurrentLevel());

            mHandler.sendEmptyMessage(RESET_PROGRESS_TIME_VIEW);
            if (!mAppDownloadShow) {
                mHandler.sendEmptyMessageDelayed(PLAY_READY_SOUND, 200);
                mHandler.removeMessages(START_PROGRESS_TIME_VIEW);
                mHandler.sendEmptyMessageDelayed(START_PROGRESS_TIME_VIEW, 1000);
            }
            
            updateToolsCount();
            updateToolsCountView();
            
            SettingManager.getInstance().setOpenLevel(Categary_diff_selector.getInstance().getCurrentLevel());
//            checkAppPoint();
        } else {
            showResetGameDialog();
        }
    }
    
    @Override
    public void onTimeCostFinish() {
        if (!mFinishDialogShow) {
            mHandler.sendEmptyMessage(SHOW_FAILED_DIALOG);
        }
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        LOGD("[[onAnimationEnd]] >>>>>>>>>");
        if (mNoMoreTipsView != null) {
            mNoMoreTipsView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        
    }

    @Override
    public void onAnimationStart(Animation animation) {
        
    }
    
    private void LOGD(String msg) {
        if (com.tinygame.lianliankan.config.Config.DEBUG) {
            Log.d(TAG, msg);
        }
    }
}