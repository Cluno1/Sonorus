// Adapted from alphaTab v1.8.4 AndroidAudioWorker.kt (MPL-2.0).
// Upstream: https://github.com/CoderLine/alphaTab/blob/v1.8.4/packages/kotlin/src/android/src/main/java/alphaTab/platform/android/AndroidAudioWorker.kt
package alphaTab.platform.android;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps the AudioTrack queue free of artificial silence between count-in and score playback. */
public final class AndroidAudioWorker {
    /**
     * Silence written once after every play(). A waking output path, Bluetooth above all, swallows
     * its first moments: at 110 BPM the first two clicks were quiet on Bluetooth earphones.
     */
    private static final int LEAD_IN_MILLIS = 800;

    private final AndroidSynthOutput output;
    private final int sampleRate;
    private int leadInFloatsRemaining;
    private final AudioTrack track;
    private final float[] buffer;
    private final Semaphore playingSemaphore = new Semaphore(1);
    private final ScheduledExecutorService updateTimer = Executors.newScheduledThreadPool(1);
    private final Thread writeThread;
    private ScheduledFuture<?> updateSchedule;
    private volatile boolean stopped;
    private volatile int previousPosition = -1;
    private volatile int startPosition = -1;
    private final AtomicLong framesWritten = new AtomicLong();

    public AndroidAudioWorker(AndroidSynthOutput output, int sampleRate, int bufferSizeInSamples) {
        this.output = output;
        this.sampleRate = sampleRate;
        buffer = new float[bufferSizeInSamples];
        track = new AudioTrack(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            bufferSizeInSamples * Float.BYTES,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        track.setPositionNotificationPeriod(bufferSizeInSamples);
        playingSemaphore.acquireUninterruptibly();
        writeThread = new Thread(this::writeSamples, "alphaTab Audio Worker");
        writeThread.start();
    }

    /** The score UI delays its count-in overlay by the same time so it stays with the sound. */
    public static int leadInMillis() {
        return LEAD_IN_MILLIS;
    }

    /** Stereo floats of lead-in silence for a sample rate. */
    public static int leadInFloats(int sampleRate) {
        return (int) (sampleRate * (long) LEAD_IN_MILLIS / 1000L) * 2;
    }

    public void setOutputDevice(AudioDeviceInfo device) {
        track.setPreferredDevice(device);
    }

    private void writeSamples() {
        while (!stopped) {
            try {
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    playingSemaphore.acquire();
                    playingSemaphore.release();
                    continue;
                }
                if (previousPosition == -1) {
                    // Played-sample callbacks start after the silence, so the lead-in cannot
                    // shorten the count-in or move the score cursor.
                    leadInFloatsRemaining = leadInFloats(sampleRate);
                    startPosition = track.getPlaybackHeadPosition() + leadInFloatsRemaining / 2;
                    previousPosition = startPosition;
                }
                if (leadInFloatsRemaining > 0) {
                    final int count = Math.min(leadInFloatsRemaining, buffer.length);
                    Arrays.fill(buffer, 0, count, 0f);
                    final int written = track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING);
                    if (written > 0) {
                        leadInFloatsRemaining -= written;
                    } else {
                        Thread.sleep(2L);
                    }
                    continue;
                }
                final int available = output.read(buffer, 0, buffer.length);
                if (available <= 0) {
                    // alphaTab needs the played-samples callback to finish its count-in.
                    // Writing an entire silent buffer here puts that silence in front of
                    // the first score beat, so wait for the synth instead.
                    Thread.sleep(2L);
                    continue;
                }
                int offset = 0;
                while (offset < available && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    int written = track.write(buffer, offset, available - offset, AudioTrack.WRITE_BLOCKING);
                    if (written <= 0) break;
                    offset += written;
                    framesWritten.addAndGet(written / 2L);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void close() {
        stopped = true;
        playingSemaphore.release();
        track.stop();
        writeThread.interrupt();
        try {
            writeThread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        track.release();
        updateTimer.shutdownNow();
    }

    public void play() {
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) return;
        previousPosition = -1;
        startPosition = -1;
        framesWritten.set(0L);
        track.play();
        stopped = false;
        updateSchedule = updateTimer.scheduleWithFixedDelay(
            this::onUpdatePlayedSamples, 0L, 10L, TimeUnit.MILLISECONDS
        );
        playingSemaphore.release();
    }

    public void pause() {
        if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) return;
        track.pause();
        playingSemaphore.acquireUninterruptibly();
        // A paused stream may still contain the previous score's queued frames.
        // The next play() creates a fresh count-in, so resume from an empty track.
        track.flush();
        if (updateSchedule != null) updateSchedule.cancel(true);
    }

    private void onUpdatePlayedSamples() {
        int samplePosition = track.getPlaybackHeadPosition();
        if (previousPosition == -1 || startPosition == -1) return;
        samplePosition = (int) Math.min(samplePosition, startPosition + framesWritten.get());
        int delta = samplePosition - previousPosition;
        if (delta < 0) return;
        previousPosition = samplePosition;
        if (delta > 0) output.onSamplesPlayed$android_release(delta);
    }
}
