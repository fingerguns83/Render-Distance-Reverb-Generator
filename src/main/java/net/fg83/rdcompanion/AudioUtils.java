package net.fg83.rdcompanion;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.WaveformWriter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Utility class for performing various operations on audio data, such as filtering,
 * smoothing, attenuation application, decay processing, and signal manipulation.
 */
public class AudioUtils {
    public static final int SAMPLE_RATE = 48000;
    private static final int SAMPLE_SIZE_IN_BITS = 32; // Typical value for audio processing
    private static final int CHANNELS = 1; // Mono for IR
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    public static final float DIFFUSION_ALPHA = 0.2F; // Smoothing "strength"
    public static final int SMOOTHING_ITERATIONS = 50;
    public static final float SMOOTHING_NOISE_FLOOR = 0.01F; // Noise floor inserted where there is no ray data
    public static final float DECAY_SCALE = 0.0001F;

    public static final TarsosDSPAudioFormat AUDIO_FORMAT = new TarsosDSPAudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
    );

    /**
     * Creates an AudioEvent object from the specified starting index and float array of audio samples.
     *
     * @param samples an array of float values representing the audio samples
     * @return an AudioEvent instance containing the provided audio samples
     */
    public static AudioEvent createAudioEventFromSamples(float[] samples) {
        AudioEvent audioEvent = new AudioEvent(AudioUtils.AUDIO_FORMAT);
        audioEvent.setFloatBuffer(samples.clone());

        return audioEvent;
    }

    /**
     * Applies a lowpass filter to the audio data contained in the specified AudioEvent object.
     * The filter attenuates frequencies above the specified cutoff frequency.
     *
     * @param event the AudioEvent object containing the audio data to be filtered
     * @param frequency the cutoff frequency for the lowpass filter in Hz
     * @param sampleRate the sample rate of the audio data in Hz
     */
    public static void applyLowpassFilter(AudioEvent event, double frequency, int sampleRate) {
        LowPassFS lowPassFS = new LowPassFS((float) frequency, sampleRate);

        try {
            lowPassFS.processingFinished();
            lowPassFS.process(event);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Applies a high-pass filter to the audio data contained in the given AudioEvent object.
     * This method processes the audio buffer, attenuating frequencies below the specified cutoff frequency.
     *
     * @param event the AudioEvent object containing the audio data to be filtered
     * @param frequency the cutoff frequency for the high-pass filter, in Hertz
     * @param sampleRate the sampling rate of the audio data, in samples per second
     */
    public static void applyHighpassFilter(AudioEvent event, double frequency, int sampleRate) {
        HighPass highPassFS = new HighPass((float) frequency, sampleRate);
        try {
            highPassFS.processingFinished();
            highPassFS.process(event);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Applies a bandpass filter to the audio data encapsulated in the given AudioEvent object.
     * The bandpass filter isolates frequencies around a defined center frequency with the specified bandwidth.
     *
     * @param event the AudioEvent object containing the audio data to be filtered
     * @param centerFrequency the center frequency of the bandpass filter in Hertz
     * @param bandwidth the bandwidth of the bandpass filter in Hertz
     * @param sampleRate the sample rate of the audio data in Hertz
     */
    public static void applyBandpassFilter(AudioEvent event, double centerFrequency, double bandwidth, int sampleRate) {
        BandPass bandPassFilter = new BandPass((float) centerFrequency, (float) bandwidth, sampleRate);

        try {
            bandPassFilter.processingFinished();
            bandPassFilter.process(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Smooths the input impulse response, applies decay effects, and adds noise based on the
     * provided parameters. This method processes audio data in a time-domain buffer and modifies
     * it in place to introduce diffusion, apply a decay envelope, and add noise.
     *
     * @param event           The AudioEvent containing the input float buffer to process.
     * @param irMatrix        A map containing impulse response (IR) coefficients indexed by
     *                        time and frequency band.
     * @param frequencyBand   The frequency band for which to apply processing.
     * @param diffusionAlpha  A diffusion factor used during smoothing iterations.
     *                        Higher values increase smoothing.
     * @param smoothingIterations The number of smoothing iterations to perform.
     *                             More iterations result in greater smoothing of the IR data.
     * @param noiseFloor      A multiplier for the noise floor, affecting the intensity of
     *                        added noise.
     * @param decayScale      A scaling factor applied to the decay envelope, influencing
     *                        the rate of decay applied to the IR data.
     */
    public static void smoothAndApplyDecay(AudioEvent event, Map<Double, Map<Integer, Double>> irMatrix, Integer frequencyBand, float diffusionAlpha, int smoothingIterations, float noiseFloor, double decayScale) {
        float[] inbuffer = event.getFloatBuffer();
        float[] smoothedBuffer = new float[inbuffer.length];

        // Step 1: Copy IR data into a working buffer
        for (int i = 0; i < inbuffer.length; i++) {
            if (irMatrix.containsKey((double) i) && irMatrix.get((double) i).containsKey(frequencyBand)) {
                double coef = irMatrix.get((double) i).get(frequencyBand);
                smoothedBuffer[i] = applyCoefficient(coef);
            } else {
                smoothedBuffer[i] = 0.0F;
            }
        }

        // Step 2: Estimate decay curve
        double firstEntry = findFirstNonZeroEntry(irMatrix, frequencyBand);
        double lastEntry = findLastNonZeroEntry(irMatrix, frequencyBand);
        double middleEntry = findMiddleEntry(irMatrix, frequencyBand);

        // Approximate decay curve: logarithmic or exponential
        double decayRate = calculateDecayRate(firstEntry, middleEntry, lastEntry, decayScale);

        // Step 3: Apply smoothing
        for (int iter = 0; iter < smoothingIterations; iter++) {
            float[] tempBuffer = smoothedBuffer.clone();
            for (int i = 1; i < smoothedBuffer.length - 1; i++) {
                smoothedBuffer[i] += diffusionAlpha * (tempBuffer[i - 1] + tempBuffer[i + 1] - 2 * tempBuffer[i]);
            }
        }

        // Step 4: Apply noise and decay envelope
        for (int i = 0; i < inbuffer.length; i++) {
            float absorp = smoothedBuffer[i];
            absorp = Math.max(0.0F, Math.min(absorp, 1.0F)); // Clamp to [0, 1]

            // Calculate the decay envelope
            float decayFactor = calculateDecayFactor(i, firstEntry, decayRate);
            absorp *= decayFactor;

            // Add noise and center on 0
            float noise = ((((float) Math.random()) - 0.5F) * absorp);
            inbuffer[i] = noise + (absorp > 0.0F ? noiseFloor * absorp : 0.0F);
        }
    }

    /**
     * Finds the first non-zero entry in the specified frequency band from the given nested map.
     *
     * @param irMatrix a map where keys are doubles representing time or another metric, and values are maps
     *                 of integers (frequency bands) to their respective double values (entries)
     * @param frequencyBand the specific frequency band to search for a non-zero entry
     * @return the first double key in the outer map that contains a non-zero value for the specified frequency
     *         band, or 0.0 if no such entry exists
     */
    private static double findFirstNonZeroEntry(Map<Double, Map<Integer, Double>> irMatrix, Integer frequencyBand) {
        return irMatrix.keySet().stream()
                .sorted()
                .filter(key -> irMatrix.get(key).containsKey(frequencyBand) && irMatrix.get(key).get(frequencyBand) > 0)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * Finds the last non-zero entry in the given frequency band from the irMatrix.
     * Iterates over the sorted keys of the irMatrix in reverse order to locate the
     * first occurrence where the specified frequency band has a non-zero value.
     *
     * @param irMatrix a map where keys are doubles and values are maps of frequency
     *                 bands (as integers) to their corresponding double values
     * @param frequencyBand the frequency band (as an integer) to check for a non-zero value
     * @return the key (as a double) of the irMatrix corresponding to the last non-zero
     *         entry in the specified frequency band, or 0.0 if no such entry exists
     */
    private static double findLastNonZeroEntry(Map<Double, Map<Integer, Double>> irMatrix, Integer frequencyBand) {
        return irMatrix.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .filter(key -> irMatrix.get(key).containsKey(frequencyBand) && irMatrix.get(key).get(frequencyBand) > 0)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * Finds the middle key from a sorted list of keys in the given irMatrix that correspond to
     * a specific frequency band and have a positive value.
     *
     * @param irMatrix a map containing keys of type Double mapping to another map of Integer and Double values
     * @param frequencyBand an Integer representing the frequency band to filter keys
     * @return the middle key from the filtered and sorted list of keys, or 0.0 if no such key exists
     */
    private static double findMiddleEntry(Map<Double, Map<Integer, Double>> irMatrix, Integer frequencyBand) {
        List<Double> keys = irMatrix.keySet().stream()
                .sorted()
                .filter(key -> irMatrix.get(key).containsKey(frequencyBand) && irMatrix.get(key).get(frequencyBand) > 0)
                .toList();
        if (keys.isEmpty()) return 0.0;
        return keys.get(keys.size() / 2);
    }

    /**
     * Calculates the decay rate based on the provided values and a scaling factor.
     * This method computes a logarithmic rate of decay that is scaled by the given decayScale.
     * If the middle or last value is zero, a default rate of 0.1 is returned to avoid division by zero.
     *
     * @param first The initial value in the sequence.
     * @param middle The middle value in the sequence, used as a reference (not directly in computation in this version).
     * @param last The final value in the sequence.
     * @param decayScale A scaling factor to adjust the steepness of the decay rate.
     * @return The calculated decay rate, or 0.1 if division by zero is avoided.
     */
    private static double calculateDecayRate(double first, double middle, double last, double decayScale) {
        if (middle == 0 || last == 0) return 0.1; // Avoid division by zero

        // Modify the logarithmic rate with the decayScale
        double rate = Math.log(last / first) / (last - first);

        // Apply decay scaling for flexibility
        return rate * decayScale; // Scale the rate to adjust steepness
    }

    /**
     * Calculates the decay factor based on the given index, initial value, and decay rate.
     *
     * @param index     The index for which the decay factor is being calculated.
     * @param first     The initial value used as a reference for the calculation.
     * @param decayRate The rate at which the decay progresses.
     * @return The calculated decay factor as a float value.
     */
    private static float calculateDecayFactor(int index, double first, double decayRate) {
        if (decayRate == 0) return 1.0F;
        return (float) Math.pow(1.0 - decayRate * (index - first), 3);
    }

    /**
     * Adjusts the given coefficient value to a range between 0.0 and 1.0.
     * If the coefficient is less than 0, it returns 0.0.
     * If the coefficient is greater than 1, it returns 1.0.
     * Otherwise, it rounds the coefficient to 8 decimal places and converts it to a float.
     *
     * @param coef the coefficient value to be adjusted, provided as a double.
     * @return the adjusted coefficient as a float, constrained between 0.0 and 1.0.
     */
    private static float applyCoefficient(double coef) {
        if (coef < 0) {
            return 0.0F;
        } else if (coef > 1) {
            return 1.0F;
        } else {
            return new BigDecimal(coef)
                    .setScale(8, RoundingMode.HALF_UP)
                    .floatValue();
        }
    }

    /**
     * Combines multiple audio band signals into a single audio event by summing their respective sample data.
     *
     * @param bandSignals a map where the keys represent band indices and the values are AudioEvent objects containing the audio signals for each band to be combined
     * @return a new AudioEvent containing the combined audio data from all provided bands
     */
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

    /**
     * Reverses the elements of the audio data buffer in the given AudioEvent object.
     * This method processes the buffer such that the first element becomes the last,
     * the second element becomes the second to last, and so on, effectively reversing the array.
     *
     * @param event the AudioEvent object containing the audio data buffer to be reversed
     */
    public static void reverseIR(AudioEvent event){
        float[] inbuffer = event.getFloatBuffer();
        int left = 0;                // Start index
        int right = inbuffer.length - 1; // End index

        while (left < right) {
            // Swap elements at 'left' and 'right'
            float temp = inbuffer[left];
            inbuffer[left] = inbuffer[right];
            inbuffer[right] = temp;

            // Move towards the center
            left++;
            right--;
        }
    }

    /**
     * Removes leading zeros from the audio data buffer in the specified AudioEvent object.
     * The method processes the buffer by identifying the first non-zero element
     * and adjusts the buffer to start from that index, effectively removing
     * the leading zeros.
     *
     * @param event the AudioEvent object containing the audio data buffer to be processed
     */
    public static void removeLeadingZeros(AudioEvent event) {
        // Find the index of the first non-zero element
        float[] inbuffer = event.getFloatBuffer();
        int startIndex = 0;
        while (startIndex < inbuffer.length && inbuffer[startIndex] == 0.0f) {
            startIndex++;
        }

        // Return a new array starting from the first non-zero index
        event.setFloatBuffer(Arrays.copyOfRange(inbuffer, startIndex, inbuffer.length));
    }
    /**
     * Trims the silence from the audio buffer in the given AudioEvent object.
     * This method identifies the first peak in the audio data, searches for a sequence
     * of zeros after the peak, and adjusts the buffer to exclude the silent portion.
     *
     * @param event the AudioEvent object containing the audio data to be processed
     */
    public static void trimSilence(AudioEvent event) {
        float[] inbuffer = event.getFloatBuffer();
        int maxIndex = findFirstMaxIndex(inbuffer);
        int endIndex = findConsecutiveZeros(inbuffer, maxIndex, 850);
        if (endIndex > maxIndex){
            event.setFloatBuffer(Arrays.copyOfRange(inbuffer, 0, endIndex));
        }
    }

    /**
     * Trims the decay portion of the audio data in the given AudioEvent object.
     * This method identifies the first peak in the audio buffer and determines a suitable end index
     * where the signal decays significantly. It then reduces the buffer to include only the relevant
     * portion of the audio data up to the determined index.
     *
     * @param event the AudioEvent object containing the audio data to be processed
     */
    public static void trimDecay(AudioEvent event) {
        float[] inbuffer = event.getFloatBuffer();
        int maxIndex = findFirstMaxIndex(inbuffer);
        int endIndex = findChunkIndex(inbuffer, maxIndex, 275);
        if (endIndex > maxIndex){
            event.setFloatBuffer(Arrays.copyOfRange(inbuffer, 0, endIndex));
        }
    }

    /**
     * Adjusts the decay of the audio data contained in the given AudioEvent object.
     * This method applies an exponential decay to the latter portion of the audio buffer,
     * starting at 70% of the array length and gradually attenuating the values toward the end.
     *
     * @param event the AudioEvent object whose audio buffer will be processed and adjusted
     */
    public static void adjustDecay(AudioEvent event) {
        float[] inbuffer = event.getFloatBuffer();
        int length = inbuffer.length;
        int startIndex = (int) Math.round(length * 0.7); // 70% of the way through the array, e.g., 699 for an array of length 1000
        float[] resultArray = new float[length];

        // Apply exponential decay from startIndex to the final index
        for (int i = 0; i < length; i++) {
            // Calculate exponential decay factor
            // The decay will start at 0 (no reduction) at index startIndex and gradually reduce values as we move to the end
            double decayFactor = Math.exp(-0.05 * (i - startIndex)); // Decay rate of 0.05 for this example

            // Apply decay factor to each value
            if (i >= startIndex) {
                resultArray[i] = (float) (inbuffer[i] * decayFactor);
            } else {
                resultArray[i] = inbuffer[i]; // No change for indices before startIndex
            }
        }

        event.setFloatBuffer(resultArray);
    }

    /**
     * Cleans up the impulse response (IR) in the given AudioEvent object.
     * This method performs a series of processing steps: reversing the audio data,
     * removing leading zeros, trimming silence, trimming exponential decay,
     * and adjusting the decay characteristics of the IR.
     *
     * @param event the AudioEvent object containing the audio data to be processed
     */
    public static void cleanupIR(AudioEvent event) {
        removeLeadingZeros(event);
        reverseIR(event);
        removeLeadingZeros(event);
        reverseIR(event);
        trimSilence(event);
        trimDecay(event);
        adjustDecay(event);
    }

    /**
     * Finds the index of the first occurrence of the maximum absolute value in the given array.
     *
     * @param array the array of float values to search for the first maximum absolute value
     * @return the index of the first occurrence of the maximum absolute value in the array
     * @throws IllegalArgumentException if the array is null or empty
     */
    public static int findFirstMaxIndex(float[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty");
        }

        float maxValue = array[0]; // Initialize max value to the first element
        int maxIndex = 0;          // Initialize max index to the first index

        for (int i = 1; i < array.length; i++) { // Start from the second element
            if (Math.abs(array[i]) > maxValue) {
                maxValue = Math.abs(array[i]);
                maxIndex = i; // Update the index of the max value
            }
        }

        return maxIndex;
    }

    /**
     * Finds the index in the array where a specified number of consecutive zeros begin.
     * Iterates through the array starting from the given index and looks for a sequence of zeroes
     * equal to the target count. Returns the index of the last zero in the sequence if found,
     * or -1 if the sequence is not found.
     *
     * @param array the array of float values in which to search for consecutive zeros
     * @param startIndex the starting index in the array from which the search begins
     * @param zeroCountTarget the number of consecutive zeros to search for in the array
     * @return the index of the last zero in the sequence if found, or -1 if not found
     */
    public static int findConsecutiveZeros(float[] array, int startIndex, int zeroCountTarget) {
        int zeroCount = 0;

        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == 0.0f) {
                zeroCount++;
                if (zeroCount == zeroCountTarget) {
                    return i; // Return the final index of the zeros
                }
            } else {
                zeroCount = 0; // Reset count if a non-zero is encountered
            }
        }

        return -1; // Return -1 if not found
    }

    /**
     * Analyzes chunks of the given array and determines an index satisfying specific conditions.
     * The array is divided into chunks of the specified size, starting at the given start index.
     * Each chunk is evaluated based on its maximum value and mean. The method returns the
     * index of the final element of the first chunk sequence that meets the condition for six
     * consecutive chunks. If no such sequence is found, it returns -1.
     *
     * @param array the array of float values to process
     * @param startIndex the index in the array where processing begins
     * @param chunkSize the size of each chunk to divide the array into
     * @return the index of the last element of the sixth consecutive chunk meeting the condition,
     *         or -1 if no such sequence exists
     */
    public static int findChunkIndex(float[] array, int startIndex, int chunkSize) {
        int consecutiveCount = 0;
        float previousChunkMean = Float.MAX_VALUE;

        for (int i = startIndex; i < array.length; i += chunkSize) {
            // Handle the case where the remaining elements are less than a full chunk
            int chunkEnd = Math.min(i + chunkSize, array.length);
            float[] chunk = Arrays.copyOfRange(array, i, chunkEnd);

            float chunkMax = findMax(chunk);
            float chunkMean = findMean(chunk);

            // Check the condition: max of current chunk < mean of previous chunk
            if (chunkMax < previousChunkMean || (chunkMax == 0.0f && previousChunkMean == 0.0f)) {
                consecutiveCount++;
                if (consecutiveCount == 4) {
                    return chunkEnd - 1; // Return the final index of the current chunk
                }
            } else {
                consecutiveCount = 0; // Reset the counter if the condition is broken
            }

            // Update the mean for the next comparison
            previousChunkMean = chunkMean;
        }

        // If no condition met, return -1
        return -1;
    }

    /**
     * Finds the maximum absolute value in the given array of floats.
     *
     * @param array the array of float values in which to find the maximum absolute value
     * @return the maximum absolute value in the array
     */
    public static float findMax(float[] array) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : array) {
            if (Math.abs(value) > max) {
                max = Math.abs(value); // Use absolute value
            }
        }
        return max;
    }

    /**
     * Calculates the mean of the absolute values of elements in the given array of floats.
     *
     * @param array the array of float values for which the mean of absolute values is calculated
     * @return the mean of the absolute values of the elements in the array
     */
    public static float findMean(float[] array) {
        float sum = 0;
        for (float value : array) {
            sum += Math.abs(value); // Use absolute value
        }
        return sum / array.length;
    }

    /**
     * Writes audio data encapsulated in an AudioEvent object to a WAV file at the specified location.
     *
     * @param filename the name and path of the output WAV file
     * @param event the AudioEvent object containing the audio data to be written
     */
    public static void writeWavFile(String filename, AudioEvent event) {
        try {
            WaveformWriter writer = new WaveformWriter(AUDIO_FORMAT, filename);
            writer.process(event);
            writer.processingFinished();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
