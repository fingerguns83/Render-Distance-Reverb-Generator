package net.fg83.rdcompanion.client;

import be.tarsos.dsp.AudioEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fg83.rdcompanion.AbsorptionCoefficient;
import net.fg83.rdcompanion.AudioUtils;
import net.fg83.rdcompanion.Ray;
import net.fg83.rdcompanion.task.PopulateRaysTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The RDRCompanionClient class provides functionality for ray tracing and impulse response
 * (IR) generation within a Minecraft client mod. It manages the state and resources required
 * for performing energy calculations, storing results, and interacting with the player through
 * notifications and messages.
 *
 * Fields:
 * - `absorptionCoefficients`: Stores mappings of materials to frequency-based absorption coefficients.
 * - `blockCoefficientKeys`: Maps block names to coefficient sets.
 * - `keyBinding`: Manages key bindings for user interactions.
 * - `isKeyPressed`: Tracks the state of key presses.
 * - `raysSubmitted`: Tracks the total number of rays submitted for processing.
 * - `processedRays`: Tracks the total number of rays that have been processed.
 * - `trackingProgress`: Indicates whether progress tracking is enabled.
 * - `lastProgressUpdate`: Tracks the last progress checkpoint.
 * - `irMatrix`: Stores impulse response data organized by delay time and frequency.
 * - `irBands`: Represents available frequency bands for calculations.
 * - `rayPool`: A thread pool for managing ray-tracing tasks.
 * - `tracedRayQueue`: A concurrent queue for storing ray-tracing results.
 * - `isCastingRays`: Tracks whether ray casting is currently in progress.
 * - `isGeneratingIR`: Tracks whether impulse response generation is currently in progress.
 */
public class RDRCompanionClient implements ClientModInitializer {

    public static final Map<String, List<AbsorptionCoefficient>> absorptionCoefficients = new HashMap<>();
    public static final Map<String, String> blockCoefficientKeys = new HashMap<>();

    private static KeyBinding keyBinding;
    private boolean isKeyPressed = false;

    public static long raysSubmitted;
    public static long processedRays;
    public static boolean trackingProgress;
    public static int lastProgressUpdate;

    public Map<Double, Map<Integer, Double>> irMatrix;
    public Map<Integer, AudioEvent> irBands;

    public ForkJoinPool rayPool;
    public Queue<Ray> tracedRayQueue;

    public AtomicBoolean isCastingRays;
    public AtomicBoolean isGeneratingIR;

    @Override
    public void onInitializeClient() {
        loadAbsorptionCoefficients();
        loadBlockCoefficientKeys();
        initialize();

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Run acoustic simulation",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "RDR Companion"
        ));
        // Register a tick event to listen for the key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isIPressed = keyBinding.isPressed();

            if (isIPressed) {
                if (!isKeyPressed) {

                    new Thread(new PopulateRaysTask(client.cameraEntity, client.cameraEntity, client, this)).start();
                    
                    isKeyPressed = true;
                }
            } else {
                isKeyPressed = false; // Reset when the key is released
            }
        });

    }

    /**
     * Initializes the state and resources required for ray tracing and impulse response generation.
     *
     * This method resets tracking variables, initializes data structures for storing
     * intermediate results, and configures thread pools and concurrent queues for
     * managing ray-tracing tasks. It also initializes atomic flags used to monitor
     * the status of concurrent operations, such as ray casting and IR generation.
     *
     * The following actions are performed:
     * - Resets counters for submitted and processed rays.
     * - Disables tracking of progress updates and clears the progress state.
     * - Initializes hash maps for storing intermediate IR data and frequency bands.
     * - Creates a custom ForkJoinPool for managing parallelized tasks.
     * - Prepares a concurrent queue for storing ray-tracing results.
     * - Instantiates atomic boolean flags for monitoring operation status.
     */
    public void initialize(){
        raysSubmitted = 0;
        processedRays = 0;
        trackingProgress = false;
        lastProgressUpdate = -1;

        irMatrix = new HashMap<>();
        irBands = new HashMap<>();

        rayPool = new ForkJoinPool(); // Creates a custom ForkJoinPool

        tracedRayQueue = new ConcurrentLinkedQueue<>();

        isCastingRays = new AtomicBoolean(false);
        isGeneratingIR = new AtomicBoolean(false);
    }


    /**
     * Generates an impulse response (IR) waveform by processing ray-traced energy data.
     *
     * This method iterates through predefined frequency bands, processes energy matrices
     * for each band, applies smoothing and decay, and applies frequency-specific filters.
     * It then combines all frequency bands into a single IR waveform, saves it as a .wav
     * file, and notifies the player in the Minecraft client. The method also ensures
     * proper directory creation for saving the file and plays a sound cue upon completion.
     *
     * @param client The instance of the Minecraft client used for player notification
     *               and accessing relevant runtime information such as server details.
     */
    public void generateIR(MinecraftClient client){
        for (int frequencyBand : Ray.FREQUENCY_BANDS) {
            if (irMatrix.keySet().stream().max(Comparator.naturalOrder()).isEmpty()){
                throw new RuntimeException("No energy data available for IR generation!");
            }
            int sampleLength = (int) Math.round(irMatrix.keySet().stream().max(Comparator.naturalOrder()).get()) + 1;

            irBands.put(frequencyBand, AudioUtils.createAudioEventFromSamples(new float[sampleLength]));
        }

        irBands.forEach((frequencyBand, audioEvent) -> {
            System.out.println("processing band: " + frequencyBand + "Hz");
            AudioUtils.smoothAndApplyDecay(audioEvent, irMatrix, frequencyBand, AudioUtils.DIFFUSION_ALPHA, AudioUtils.SMOOTHING_ITERATIONS, AudioUtils.SMOOTHING_NOISE_FLOOR, AudioUtils.DECAY_SCALE);

            switch (frequencyBand){
                case 125:
                    AudioUtils.applyLowpassFilter(audioEvent, 125, AudioUtils.SAMPLE_RATE);
                    break;
                case 250:
                    AudioUtils.applyLowpassFilter(audioEvent, 250, AudioUtils.SAMPLE_RATE);
                    AudioUtils.applyHighpassFilter(audioEvent, 125, AudioUtils.SAMPLE_RATE);
                    break;
                case 2000:
                    AudioUtils.applyHighpassFilter(audioEvent, 2000, AudioUtils.SAMPLE_RATE);
                    AudioUtils.applyLowpassFilter(audioEvent, 4000, AudioUtils.SAMPLE_RATE);
                    break;
                case 4000:
                    AudioUtils.applyHighpassFilter(audioEvent, 4000, AudioUtils.SAMPLE_RATE);
                    break;
                default:
                    int freqMax = frequencyBand * 2;
                    int freqMin = frequencyBand / 2;
                    double centerFreq = (double) (freqMax + freqMin) / 2;
                    double bandwidth = freqMax - freqMin;
                    AudioUtils.applyBandpassFilter(audioEvent, centerFreq, bandwidth, AudioUtils.SAMPLE_RATE);
            }
        });
        System.out.println("Combining bands...");
        AudioEvent combinedIR = AudioUtils.combineBands(irBands);
        AudioUtils.cleanupIR(combinedIR);
        double length = (double) Math.round(((double) combinedIR.getBufferSize() / AudioUtils.SAMPLE_RATE) * 100) / 100;

        sendPlayerMessage(client, "IR waveform generated! (" + length + " seconds)", new Formatting[]{Formatting.GOLD});
        
        Path directoryPath = Paths.get("VerbCrafter");
        if (!Files.exists(directoryPath)) {
            try {
                Files.createDirectory(directoryPath);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String name;
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            name = client.getServer().getSaveProperties().getLevelName();
        }
        else {
            name = Objects.requireNonNull(client.getCurrentServerEntry()).name;
        }

        String filename = "IR_" + name + "_" + timestamp + ".wav";
        
        AudioUtils.writeWavFile("VerbCrafter" + File.separator + filename, combinedIR);

        assert client.player != null;
        client.player.playSoundToPlayer(SoundEvent.of(Identifier.of("minecraft", "block.amethyst_block.chime")), SoundCategory.PLAYERS, 2.0F, 0.8F);
        sendPlayerMessage(client, "Wrote file '" + filename + "'!", new Formatting[]{Formatting.GOLD});

        initialize();
    }

    /**
     * Adds energy contributions to the impulse response (IR) matrix for a specific delay time.
     * This method merges new energy values with existing energy data in the IR matrix.
     * If the delay time is already present, frequencies and their energy values are combined
     * by summing the existing and incoming values. If a frequency does not exist,
     * it is added to the current energy data with its respective value.
     *
     * @param delayTime The time delay (in seconds) associated with the energy contributions.
     * @param newEnergy A map containing frequency-energy pairs to be added to the IR matrix.
     *                  Keys represent frequencies, and values represent corresponding energy contributions.
     */
    public void addEnergyToIR(double delayTime, Map<Integer, Double> newEnergy){
        irMatrix.merge(delayTime, newEnergy, (existingEnergy, incomingEnergy) -> {
            incomingEnergy.forEach((frequency, energy) ->
                    existingEnergy.merge(frequency, energy, Double::sum)
            );
            return existingEnergy;
        });
    }

    /**
     * Reports the progress of ray tracing operations to the player in the Minecraft client.
     *
     * This method calculates the progress percentage based on the number of processed rays
     * and the total rays to be processed. If progress reaches a certain threshold (increments of 5%),
     * it updates the player's chat with a progress bar displaying the progress percentage.
     *
     * @param client The instance of the Minecraft client used to send progress messages to the player.
     */
    public static void reportProgress(MinecraftClient client) {
        final int totalRays = 10810429;

        float percentage = ((float) processedRays / totalRays) * 100;

        int progressStep = (int) Math.floor(Math.floor(percentage) / 5);

        if (progressStep > lastProgressUpdate) {
            lastProgressUpdate = progressStep;

            String progressMessage = buildProgressBar(progressStep);

            sendPlayerMessage(client, progressMessage, new Formatting[]{Formatting.AQUA});
        }
    }


    /**
     * Builds a visual progress bar as a string representation indicating the current progress.
     * The progress bar consists of 20 steps, where filled and unfilled steps are represented
     * by specific symbols. The percentage value of progress is also appended at the end.
     *
     * @param progressStep The current progress step, ranging from 0 to 20, where each step
     *                     represents 5% completion.
     * @return A string representing the progress bar with the progress percentage.
     */
    private static String buildProgressBar(int progressStep) {
        StringBuilder progressBar = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i < progressStep) {
                progressBar.append("◆");
            } else {
                progressBar.append("◇");
            }
        }
        progressBar.append("] ").append(progressStep * 5).append("%");
        return progressBar.toString();
    }

    /**
     * Loads absorption coefficient data from a JSON file and populates the `absorptionCoefficients` map.
     *
     * This method reads the resource file "coefficient_sets.json" from the classpath. The JSON file is
     * expected to contain objects with keys representing material types and values containing frequency-to-coefficient
     * mappings. Each frequency-to-coefficient mapping specifies a frequency in Hz and its corresponding absorption
     * coefficient.
     *
     * For each entry in the JSON file:
     * - The material type (key) is used as the key for the `absorptionCoefficients` map.
     * - The frequency-to-coefficient pairs are stored as a list of `AbsorptionCoefficient` objects associated with the key.
     *
     * The method uses Java's try-with-resources statement to ensure that input streams and resources are
     * properly closed after use.
     *
     * Throws:
     * - RuntimeException: If the resource file "coefficient_sets.json" is missing or any I/O error occurs during reading
     *   or parsing.
     *
     * Outputs:
     * - Prints the total number of absorption coefficient sets loaded to the console.
     */
    private void loadAbsorptionCoefficients() {
        try (InputStream inputStream = getClass().getResourceAsStream("/coefficient_sets.json")) {
            assert inputStream != null;
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    String key = entry.getKey();
                    JsonObject coefficients = entry.getValue().getAsJsonObject();
                    List<AbsorptionCoefficient> coefficientList = new ArrayList<>();

                    for (Map.Entry<String, JsonElement> freqEntry : coefficients.entrySet()) {
                        int frequency = Integer.parseInt(freqEntry.getKey());
                        double value = freqEntry.getValue().getAsDouble();
                        coefficientList.add(new AbsorptionCoefficient(frequency, value));
                    }

                    absorptionCoefficients.put(key, coefficientList);
                }
                System.out.println("Loaded " + absorptionCoefficients.size() + " absorption coefficient sets");
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load coefficient_sets.json", e);
        }
    }

    /**
     * Loads block-to-coefficient key mappings from a JSON file and stores them in the `blockCoefficientKeys` map.
     *
     * The method accesses a resource file named "item_map.json" located in the classpath. This JSON file is expected
     * to contain an array of objects where each object represents a block and its associated coefficients. Each object
     * in the array should have the following keys:
     * - "block": The name of the block (String).
     * - "coefficients": The coefficient information (String).
     *
     * The method parses the JSON data, extracts the block names and their coefficients, and inserts them into the
     * `blockCoefficientKeys` map, where the block names serve as keys and the coefficients as their corresponding values.
     *
     * If the "item_map.json" resource cannot be found or any IOException occurs during the reading process, a runtime
     * exception is thrown with a descriptive error message.
     *
     * This method uses Java's try-with-resources to ensure that input streams and readers are automatically closed after use.
     *
     * Throws:
     * - RuntimeException: If the "item_map.json" file is missing or an I/O error occurs during reading or parsing.
     */
    private void loadBlockCoefficientKeys() {
        try (InputStream inputStream = getClass().getResourceAsStream("/item_map.json")) {
            assert inputStream != null;
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {

                JsonArray json = JsonParser.parseReader(reader).getAsJsonArray();

                for (JsonElement element : json) {
                    JsonObject item = element.getAsJsonObject();
                    String block = item.get("block").getAsString();
                    String coef = item.get("coefficients").getAsString();
                    blockCoefficientKeys.put(block, coef);
                }

                System.out.println("Loaded " + blockCoefficientKeys.size() + " block coefficient keys");
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load item_map.json", e);
        }
    }

    /**
     * Sends a message to the player within the Minecraft client.
     *
     * This method creates a formatted message using the specified text and formatting options
     * and sends it to the player's chat window.
     *
     * @param client The Minecraft client instance, which is used to access the player.
     * @param message The message text to be sent to the player.
     * @param formattingArray An array of Formatting options applied to style the message.
     */
    public static void sendPlayerMessage(MinecraftClient client, String message, Formatting[] formattingArray){
        MutableText messageText = Text.literal(message);
        messageText.formatted(formattingArray);
        assert client.player != null;
        client.player.sendMessage(messageText, false);
    }

}
