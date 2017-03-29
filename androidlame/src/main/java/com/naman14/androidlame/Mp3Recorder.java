package com.naman14.androidlame;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 简单整理录制逻辑到此类，有其他需求时视情况封装
 * Created by liyiheng on 16/12/29.
 * Modified by Northern Captain 30 Mar 2017.
 */

public class Mp3Recorder
{

    private int minBuffer;
    private int inSampleRate;
    private AudioRecord audioRecord;
    private AndroidLame androidLame;
    private FileOutputStream outputStream;
    private volatile boolean isRecording = false;
    private byte[] mp3buffer;
    public long length;
    private long mStartTime;
    private Handler mainHandler;

    /**
     * Create recorder with default 44100 sample rate and 128k bitrate
     * @param file
     */
    public Mp3Recorder(File file)
    {
        this(file, 128);
    }

    /**
     * Create recorder with default 44100 sample rate
     * @param file
     * @param outputBitRate
     */
    public Mp3Recorder(File file, int outputBitRate)
    {
        this(file, outputBitRate, 44100);
    }

    public Mp3Recorder(File file, int outputBitRate, int inSampleRate)
    {
        this.inSampleRate = inSampleRate;
        minBuffer = AudioRecord.getMinBufferSize(inSampleRate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                inSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2);

        try
        {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e)
        {
            throw new RuntimeException("Could not open file for writing " + file.getAbsolutePath(), e);
        }
        androidLame = new LameBuilder()
                .setInSampleRate(inSampleRate)
                .setOutChannels(1)
                .setOutBitrate(outputBitRate)
                .setOutSampleRate(inSampleRate)
                .build();

        mainHandler = new Handler(Looper.getMainLooper());
    }


    public void pause()
    {
        isRecording = false;
        length += System.currentTimeMillis() - mStartTime;
    }

    public static final String RECORD_ERROR = "Mp3Recorder: record error";

    public void start()
    {
        mStartTime = System.currentTimeMillis();
        audioRecord.startRecording();
        isRecording = true;
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                short[] buffer = new short[inSampleRate * 2 * 5];
                mp3buffer = new byte[(int) (7200 + buffer.length * 2 * 1.25)];
                int bytesRead;
                while (isRecording)
                {
                    bytesRead = audioRecord.read(buffer, 0, minBuffer);
                    if (bytesRead > 0)
                    {
                        int bytesEncoded = androidLame.encode(buffer, buffer, bytesRead, mp3buffer);
                        if (bytesEncoded > 0)
                        {
                            try
                            {
                                outputStream.write(mp3buffer, 0, bytesEncoded);
                            } catch (final IOException e)
                            {
                                e.printStackTrace();
                                pause();

                                if (listener != null)
                                {
                                    mainHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            if(listener != null)
                                            {
                                                listener.onError(e);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    } else if (bytesRead < 0)
                    {
                        pause();
                        if (listener != null)
                        {
                            mainHandler.post(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    if(listener != null)
                                    {
                                        listener.onError(new RuntimeException(RECORD_ERROR));
                                    }
                                }
                            });
                        }
                    }
                }
                close();
            }
        }).start();
    }

    public boolean isRecording()
    {
        return isRecording;
    }

    private void close()
    {
        int outputMp3buf = androidLame.flush(mp3buffer);
        if (outputMp3buf > 0)
        {
            try
            {
                outputStream.write(mp3buffer, 0, outputMp3buf);
                outputStream.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        audioRecord.stop();
        audioRecord.release();
        androidLame.close();

        if(listener != null)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (listener != null)
                    {
                        listener.onStopped();
                    }
                }
            });
        }
    }

    /**
     * Stop recording, but not immediately. Register listener and receive onStopped notification
     * when full stop is done. Listener methods are called on UI thread.
     */
    public void stop()
    {
        isRecording = false;
    }

    public interface OnChangeListener
    {
        /**
         * 可能在异步线程中调用
         */
        void onError(Throwable err);

        /**
         * Called on UI thread when recording was stopped
         *
         */
        void onStopped();
    }

    private OnChangeListener listener;

    public void setOnChangeListener(OnChangeListener listener)
    {
        this.listener = listener;
    }
}