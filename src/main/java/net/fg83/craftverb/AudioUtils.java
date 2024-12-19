package net.fg83.craftverb;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import be.tarsos.dsp.synthesis.NoiseGenerator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioUtils {
    public static final int SAMPLE_RATE = 48000;
    private static final int SAMPLE_SIZE_IN_BITS = 16; // Typical value for audio processing
    private static final int CHANNELS = 1; // Mono for IR
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    public static final TarsosDSPAudioFormat AUDIO_FORMAT = new TarsosDSPAudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
    );

    public static AudioEvent createAudioEventFromSamples(int start, float[] samples) {
        System.out.println("Delay Start: " + start);
        AudioEvent audioEvent = new AudioEvent(AudioUtils.AUDIO_FORMAT);
        audioEvent.setFloatBuffer(samples.clone());
        NoiseGenerator noiseGenerator = new NoiseGenerator();
        noiseGenerator.processingFinished();
        noiseGenerator.process(audioEvent);
        float[] floatBuffer = audioEvent.getFloatBuffer();
        for (int i = 0; i < start; i++){
            floatBuffer[i] = 0;
        }
        audioEvent.setFloatBuffer(floatBuffer);
        return audioEvent;
    }

    public static float[] applyLowpassFilter(AudioEvent event, double frequency, int sampleRate) {
        LowPassFS lowPassFS = new LowPassFS((float) frequency, sampleRate);

        try {
            lowPassFS.processingFinished();
            lowPassFS.process(event);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return event.getFloatBuffer();
    }

    public static float[] applyHighpassFilter(AudioEvent event, double frequency, int sampleRate) {
        HighPass highPassFS = new HighPass((float) frequency, sampleRate);
        try {
            highPassFS.processingFinished();
            highPassFS.process(event);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return event.getFloatBuffer();
    }

    public static float[] applyBandpassFilter(AudioEvent event, double centerFrequency, double bandwidth, int sampleRate) {
        BandPass bandPassFilter = new BandPass((float) centerFrequency, (float) bandwidth, sampleRate);

        try {
            bandPassFilter.processingFinished();
            bandPassFilter.process(event);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return event.getFloatBuffer();
    }

    public static void applyAttenuation(AudioEvent event, Map<Double, Map<Integer, Double>> irMatrix, Integer frequencyBand){
        float[] unprocessed = event.getFloatBuffer();
        float[] processed = unprocessed.clone();
        AtomicBoolean isFirst = new AtomicBoolean(true);
        irMatrix.keySet().forEach(delayTime -> {
            int sampleIndex = (int) (Math.round(delayTime) - 1);
            if (isFirst.get()) {
                System.out.println("First sample index: " + sampleIndex);
                isFirst.set(false);
            }
            Map<Integer, Map<Double, Double>> matrixByFreq = new HashMap<>();
            irMatrix.forEach((delayTime2, energyMap) -> {
                energyMap.forEach((frequency, energy) -> {
                    Map<Double,Double> energySample = new HashMap<>();
                    energySample.put(delayTime2, energy);
                    matrixByFreq.put(frequency, energySample);
                });
            });
            double maxAmpl = matrixByFreq.get(frequencyBand).values().stream().max(Comparator.naturalOrder()).get();
            float oldEnergy = unprocessed[sampleIndex];

            float newEnergy = (float) ((oldEnergy * irMatrix.get(delayTime).get(frequencyBand)) / maxAmpl);
            processed[sampleIndex] = newEnergy;
        });
        event.setFloatBuffer(processed);
    }

    public static AudioEvent combineBands(Map<Integer, AudioEvent> bandSignals){
        int length = bandSignals.values().iterator().next().getBufferSize();
        float[] combinedSamples = new float[length];
        AudioEvent combinedEvent = new AudioEvent(AUDIO_FORMAT);

        for (AudioEvent bandSignal : bandSignals.values()) {
            for (int i = 0; i < length; i++) {
                combinedSamples[i] += bandSignal.getFloatBuffer()[i];
            }
        }
        combinedEvent.setFloatBuffer(combinedSamples);
        return combinedEvent;
    }

    public static void writeWavFile(String filename, AudioEvent event) {
        try {
            WaveformWriter writer = new WaveformWriter(AUDIO_FORMAT, filename);
            writer.process(event);
            writer.processingFinished();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double[] simpleFlatted(Map<Double, Map<Integer, Double>> irMatrix){
        int sampleLength = (int) Math.round(irMatrix.keySet().stream().max(Comparator.naturalOrder()).get());
        double[] irWaveform = new double [sampleLength + 1];
        irMatrix.forEach((sampleIndex, energyMap) -> {
            double totalEnergy = energyMap.values().stream().mapToDouble(Double::doubleValue).sum();
            irWaveform[sampleIndex.intValue()] += totalEnergy;
        });
        return irWaveform;
    }

    public static void simpleNormalize(double[] irWaveform){
        double maxAmplitude = Arrays.stream(irWaveform).map(Math::abs).max().orElse(1.0);
        for (int i = 0; i < irWaveform.length; i++) {
            irWaveform[i] /= maxAmplitude;
        }
    }

    public static void simpleExport(Map<Double, Map<Integer, Double>> irMatrix){
        double[] irWaveform = simpleFlatted(irMatrix);

        simpleNormalize(irWaveform);

        double meanAmplitude = Arrays.stream(irWaveform).map(Math::abs).average().orElse(0);
        for (int i = 0; i < irWaveform.length; i++) {
            irWaveform[i] -= meanAmplitude;
        }

        try {
            writeWaveFile("simpleWavFile.wav", irWaveform, SAMPLE_RATE);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeWaveFile(String filePath, double[] samples, int sampleRate) throws IOException {
        int byteRate = sampleRate * 2; // 16-bit audio = 2 bytes per sample
        int dataSize = samples.length * 2; // Total size of sample data in bytes
        int chunkSize = 36 + dataSize;

        try (FileOutputStream out = new FileOutputStream(filePath)) {
            // Write WAV header
            out.write("RIFF".getBytes()); // ChunkID
            out.write(intToBytes(chunkSize)); // ChunkSize
            out.write("WAVE".getBytes()); // Format
            out.write("fmt ".getBytes()); // Subchunk1ID
            out.write(intToBytes(16)); // Subchunk1Size (PCM)
            out.write(shortToBytes((short) 1)); // AudioFormat (1 = PCM)
            out.write(shortToBytes((short) 1)); // NumChannels (mono)
            out.write(intToBytes(sampleRate)); // SampleRate
            out.write(intToBytes(byteRate)); // ByteRate
            out.write(shortToBytes((short) 2)); // BlockAlign
            out.write(shortToBytes((short) 16)); // BitsPerSample
            out.write("data".getBytes()); // Subchunk2ID
            out.write(intToBytes(dataSize)); // Subchunk2Size

            // Write samples
            for (double sample : samples) {
                short intSample = (short) (sample * 32767); // Scale to 16-bit range
                out.write(shortToBytes(intSample));
            }
        }
        System.out.println("Wrote " + filePath);
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    private static byte[] shortToBytes(short value) {
        return new byte[]{(byte) (value), (byte) (value >> 8)};
    }
}
