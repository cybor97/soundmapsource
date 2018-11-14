package com.cybor97.soundmapsource;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Date;

public class AudioRecordStream extends AudioRecord {
    private byte[] currentBuffer;
    private Thread recordingThread;
    private long actualizedAt = new Date().getTime();
    private long lastRead = 0;

    /**
     * Class constructor.
     * Though some invalid parameters will result in an {@link IllegalArgumentException} exception,
     * other errors do not.  Thus you should call {@link #getState()} immediately after construction
     * to confirm that the object is usable.
     *
     * @param audioSource       the recording source.
     *                          See {@link MediaRecorder.AudioSource} for the recording source definitions.
     * @param sampleRateInHz    the sample rate expressed in Hertz. 44100Hz is currently the only
     *                          rate that is guaranteed to work on all devices, but other rates such as 22050,
     *                          16000, and 11025 may work on some devices.
     *                          {@link AudioFormat#SAMPLE_RATE_UNSPECIFIED} means to use a route-dependent value
     *                          which is usually the sample rate of the source.
     *                          {@link #getSampleRate()} can be used to retrieve the actual sample rate chosen.
     * @param channelConfig     describes the configuration of the audio channels.
     *                          See {@link AudioFormat#CHANNEL_IN_MONO} and
     *                          {@link AudioFormat#CHANNEL_IN_STEREO}.  {@link AudioFormat#CHANNEL_IN_MONO} is guaranteed
     *                          to work on all devices.
     * @param audioFormat       the format in which the audio data is to be returned.
     *                          See {@link AudioFormat#ENCODING_PCM_8BIT}, {@link AudioFormat#ENCODING_PCM_16BIT},
     *                          and {@link AudioFormat#ENCODING_PCM_FLOAT}.
     * @param bufferSizeInBytes the total size (in bytes) of the buffer where audio data is written
     *                          to during the recording. New audio data can be read from this buffer in smaller chunks
     *                          than this size. See {@link #getMinBufferSize(int, int, int)} to determine the minimum
     *                          required buffer size for the successful creation of an AudioRecord instance. Using values
     *                          smaller than getMinBufferSize() will result in an initialization failure.
     * @throws IllegalArgumentException
     */
    public AudioRecordStream(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) throws IllegalArgumentException {
        super(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        currentBuffer = new byte[bufferSizeInBytes];
    }

    @Override
    public void startRecording() throws IllegalStateException {
        if (getRecordingState() != RECORDSTATE_RECORDING)
            super.startRecording();
        if (recordingThread == null || recordingThread.isInterrupted()) {
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        AudioRecordStream.super.read(currentBuffer, 0, currentBuffer.length);
                        actualizedAt = new Date().getTime();
                    }
                }
            });
            recordingThread.start();
        }
    }

    @Override
    public int read(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes) {
        try {
            while (lastRead == actualizedAt)
                Thread.sleep(1);
        } catch (InterruptedException e) {
        }
        System.arraycopy(currentBuffer, offsetInBytes, audioData, offsetInBytes,
                sizeInBytes - offsetInBytes);
        lastRead = actualizedAt;
        return sizeInBytes - offsetInBytes;
    }

    @Override
    public void stop() throws IllegalStateException {
        if (recordingThread != null)
            recordingThread.interrupt();
        super.stop();
    }
}
