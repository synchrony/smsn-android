package net.fortytwo.extendo.brainstem;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;

/**
* @author Joshua Shinavier (http://fortytwo.net)
*/
public class NotificationToneGenerator {
    // Note: is it possible to generate a tone with lower latency than this default generator's?
    //private final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

    private static final float SYNTH_FREQUENCY = 880;

    private final AudioTrack audioTrack;
    private final int minSize;
    private final int sampleRate;

    public NotificationToneGenerator() {
        sampleRate = getValidSampleRate();

        minSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minSize,
                AudioTrack.MODE_STREAM);
    }

    public void play() {
        //startActivity(new Intent(thisActivity, PlaySound.class));

        //tg.startTone(ToneGenerator.TONE_PROP_BEEP);

        audioTrack.play();
        short[] buffer = new short[minSize];
        float angle = 0;
        //while (true) {
        //    if (play) {
        for (int i = 0; i < buffer.length; i++) {
            float angular_frequency =
                    (float) (2 * Math.PI) * SYNTH_FREQUENCY / sampleRate;
            buffer[i] = (short) (Short.MAX_VALUE * ((float) Math.sin(angle)));
            angle += angular_frequency;
        }
        audioTrack.write(buffer, 0, buffer.length);
        //    }
        //}
    }

    private int getValidSampleRate() {
        for (int rate : new int[]{8000, 11025, 16000, 22050, 44100}) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                return rate;
            }
        }

        throw new IllegalStateException("could not find a valid sample rate for audio output");
    }
}
