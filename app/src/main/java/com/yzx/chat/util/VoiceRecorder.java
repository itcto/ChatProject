package com.yzx.chat.util;

import android.media.MediaRecorder;
import android.os.Handler;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by YZX on 2017年12月08日.
 * 优秀的代码是它自己最好的文档,当你考虑要添加一个注释时,问问自己:"如何能改进这段代码，以让它不需要注释？"
 */


public class VoiceRecorder {

    private static final int DEFAULT_MAX_DURATION = 60 * 1000;
    private static final int DEFAULT_GET_AMPLITUDE_INTERVAL = 200;

    private String mSavePath;
    private int mMaxDuration;
    private OnRecorderStateListener mOnRecorderStateListener;
    private OnAmplitudeChange mOnAmplitudeChangeListener;

    private Handler mOnAmplitudeChangeHandler;
    private String initFailReason;
    private MediaRecorder mRecorder;
    private long mStartRecorderTime;
    private int mGetAmplitudeInterval = DEFAULT_GET_AMPLITUDE_INTERVAL;
    private int mCurrentAmplitude;

    public VoiceRecorder(String savePath) {
        this(savePath, null);
    }

    public VoiceRecorder(String savePath, OnRecorderStateListener onRecorderStateListener) {
        this(savePath, DEFAULT_MAX_DURATION, onRecorderStateListener);
    }

    public VoiceRecorder(String savePath, int maxDuration) {
        this(savePath, maxDuration, null);
    }

    public VoiceRecorder(String savePath, int maxDuration, OnRecorderStateListener onRecorderStateListener) {
        mSavePath = savePath;
        mMaxDuration = maxDuration;
        mOnRecorderStateListener = onRecorderStateListener;
    }

    private void init() {
        mRecorder = new MediaRecorder();
        try {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mRecorder.setOutputFile(mSavePath);
            mRecorder.setMaxDuration(mMaxDuration);
        } catch (RuntimeException e) {
            initFailReason = e.toString();
            return;
        }
        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (what != MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
                    if (mOnRecorderStateListener != null) {
                        mOnRecorderStateListener.onComplete(mSavePath, System.currentTimeMillis() - mStartRecorderTime);
                    }
                } else {
                    mOnRecorderStateListener.onError("VoiceRecorder Error:MEDIA_RECORDER_INFO_UNKNOWN");
                }
                release();
            }
        });
        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                if (mOnRecorderStateListener != null) {
                    if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
                        mOnRecorderStateListener.onError("VoiceRecorder Error:MEDIA_RECORDER_ERROR_UNKNOWN");
                    } else {
                        mOnRecorderStateListener.onError("VoiceRecorder Error:MEDIA_ERROR_SERVER_DIED");
                    }
                }
                release();
            }
        });
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            initFailReason = e.toString();
        }

    }

    private void release() {
        if (mOnAmplitudeChangeHandler != null) {
            mOnAmplitudeChangeHandler.removeCallbacks(mGetAmplitudeRunnable);
        }
        if (mRecorder != null) {
            mRecorder.reset();
            mRecorder.release();
        }
        mCurrentAmplitude = 0;
        mRecorder = null;
    }

    private void updateAmplitude() {
        if (mOnAmplitudeChangeListener == null) {
            return;
        }
        if (mOnAmplitudeChangeHandler == null) {
            mOnAmplitudeChangeHandler = new Handler();
        }
        mOnAmplitudeChangeHandler.post(mGetAmplitudeRunnable);
    }

    private final Runnable mGetAmplitudeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRecorder == null || mOnAmplitudeChangeListener == null) {
                return;
            }
            int nowAmplitude;
            try {
                nowAmplitude = mRecorder.getMaxAmplitude();
            } catch (RuntimeException e) {
                e.printStackTrace();
                return;
            }
            if (nowAmplitude != mCurrentAmplitude) {
                mOnAmplitudeChangeListener.onAmplitudeChange(nowAmplitude);
                mCurrentAmplitude = nowAmplitude;
            }
            mOnAmplitudeChangeHandler.postDelayed(this, mGetAmplitudeInterval);
        }
    };

    public void prepare() {
        release();
        init();
    }


    public void start() {
        if (!TextUtils.isEmpty(initFailReason)) {
            if (mOnRecorderStateListener != null) {
                mOnRecorderStateListener.onError(initFailReason);
            }
            return;
        }
        try {
            mRecorder.start();
            mStartRecorderTime = System.currentTimeMillis();
            updateAmplitude();
        } catch (RuntimeException e) {
            if (mOnRecorderStateListener != null) {
                mOnRecorderStateListener.onError(e.toString());
            }
        }
    }

    public void stop() {
        if (mRecorder == null) {
            return;
        }
        try {
            mRecorder.stop();
            if (mOnRecorderStateListener != null) {
                mOnRecorderStateListener.onComplete(mSavePath, System.currentTimeMillis() - mStartRecorderTime);
            }
        } catch (RuntimeException e) {
            if (mOnRecorderStateListener != null) {
                mOnRecorderStateListener.onError(e.toString());
            }
        }
        release();
    }

    public boolean cancelAndDelete() {
        try {
            mRecorder.stop();
        } catch (RuntimeException ignored) {
        }
        release();
        File file = new File(mSavePath);
        return !file.exists() || file.delete();
    }

    public void setSavePath(String savePath) {
        mSavePath = savePath;
    }

    public void setMaxDuration(int maxDuration) {
        mMaxDuration = maxDuration;
    }

    public void setOnRecorderStateListener(OnRecorderStateListener listener) {
        mOnRecorderStateListener = listener;
    }

    public void setGetAmplitudeInterval(int ms) {
        mGetAmplitudeInterval = ms;
    }

    public void setOnAmplitudeChange(OnAmplitudeChange onAmplitudeChange, Handler handler) {
        if (mOnAmplitudeChangeHandler != null) {
            mOnAmplitudeChangeHandler.removeCallbacks(mGetAmplitudeRunnable);
        }
        mOnAmplitudeChangeListener = onAmplitudeChange;
        mOnAmplitudeChangeHandler = handler;
    }

    public interface OnRecorderStateListener {
        void onComplete(String filePath, long duration);

        void onError(String error);
    }

    public interface OnAmplitudeChange {
        void onAmplitudeChange(int amplitude);
    }
}