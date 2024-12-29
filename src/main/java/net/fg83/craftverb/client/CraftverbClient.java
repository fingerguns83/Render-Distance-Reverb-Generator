package net.fg83.craftverb.client;

import be.tarsos.dsp.AudioEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fg83.craftverb.AbsorptionCoefficient;
import net.fg83.craftverb.AudioUtils;
import net.fg83.craftverb.Ray;
import net.fg83.craftverb.task.PopulateRaysTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
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

public class CraftverbClient implements ClientModInitializer {

    public static final Map<String, List<AbsorptionCoefficient>> absorptionCoefficients = new HashMap<>();
    public static final Map<String, String> blockCoefficientKeys = new HashMap<>();

    private static KeyBinding keyBinding;
    private boolean isKeyPressed = false;

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
                "Ray Step", // The translation key for the keybinding
                InputUtil.Type.KEYSYM, // The type of input, here it's a keyboard key
                GLFW.GLFW_KEY_I, // The Alt key
                "CraftVerb" // The translation key for the category in the keybind menu
        ));

        // Register a tick event to listen for the key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isIPressed = keyBinding.isPressed();

            if (isIPressed) {
                if (!isKeyPressed) {
                    client.player.sendMessage(Text.of("Grab some coffee, this is going to take a while."), false);

                    new Thread(new PopulateRaysTask(client, this)).start();
                    
                    isKeyPressed = true;
                }
            } else {
                isKeyPressed = false; // Reset when the key is released
            }
        });

    }

    public void initialize(){
        trackingProgress = false;
        lastProgressUpdate = -1;

        irMatrix = new HashMap<>();
        irBands = new HashMap<>();

        rayPool = new ForkJoinPool(); // Creates a custom ForkJoinPool

        tracedRayQueue = new ConcurrentLinkedQueue<>();

        isCastingRays = new AtomicBoolean(false);
        isGeneratingIR = new AtomicBoolean(false);
    }


    public void generateIR(MinecraftClient client){
        for (int frequencyBand : Ray.FREQUENCY_BANDS) {
            int sampleLength = (int) Math.round(irMatrix.keySet().stream().max(Comparator.naturalOrder()).get()) + 1;
            int min = (int) Math.round(irMatrix.keySet().stream().min(Comparator.naturalOrder()).get()) + 1;

            irBands.put(frequencyBand, AudioUtils.createAudioEventFromSamples(min, new float[sampleLength]));
        }


        irBands.forEach((frequencyBand, audioEvent) -> {
            AudioUtils.applyAttenuation(audioEvent, irMatrix, frequencyBand);

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
        AudioEvent combinedIR = AudioUtils.combineBands(irBands);
        AudioUtils.cleanupIR(combinedIR);
        double length = (double) Math.round(((double) combinedIR.getBufferSize() / AudioUtils.SAMPLE_RATE) * 100) / 100;

        client.player.sendMessage(Text.of("IR waveform generated! (" + length + " seconds)"), false);
        
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
            name = client.getCurrentServerEntry().name;
        }

        String filename = "IR_" + name + "_" + timestamp + ".wav";
        
        AudioUtils.writeWavFile("VerbCrafter" + File.separator + filename, combinedIR);

        client.player.playSoundToPlayer(SoundEvent.of(Identifier.of("minecraft", "block.amethyst_block.chime")), SoundCategory.PLAYERS, 2.0F, 0.8F);
        client.player.sendMessage(Text.of("Wrote file '" + filename + "'!"), false);

        initialize();
    }

    public void addEnergyToIR(double delayTime, Map<Integer, Double> newEnergy){
        irMatrix.merge(delayTime, newEnergy, (existingEnergy, incomingEnergy) -> {
            incomingEnergy.forEach((frequency, energy) ->
                    existingEnergy.merge(frequency, energy, Double::sum)
            );
            return existingEnergy;
        });
    }

    public static void reportProgress(CraftverbClient craftverbClient, MinecraftClient client) {
        final int totalRays = 6480000;

        // Calculate progress percentage
        int currentSize = craftverbClient.tracedRayQueue.size();
        int percentage = Math.round(((float) currentSize / totalRays) * 100);
        int progressStep = (int) Math.floor((double) percentage / 5);

        // Check if progress needs to be updated
        if (progressStep > lastProgressUpdate) {
            lastProgressUpdate = progressStep;

            // Build the progress bar message
            String progressMessage = buildProgressMessage(progressStep);

            // Send the message to the player
            client.player.sendMessage(Text.of(progressMessage), false);
        }
    }

    // Helper method to build the progress bar
    private static String buildProgressMessage(int progressStep) {
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

    private void loadAbsorptionCoefficients() {
        try (InputStream inputStream = getClass().getResourceAsStream("/coefficient_sets.json");
             InputStreamReader reader = new InputStreamReader(inputStream)) {

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
        catch (IOException e) {
            throw new RuntimeException("Failed to load coefficient_sets.json", e);
        }
    }

    private void loadBlockCoefficientKeys() {
        try (InputStream inputStream = getClass().getResourceAsStream("/item_map.json");
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            JsonArray json = JsonParser.parseReader(reader).getAsJsonArray();

            for (JsonElement element : json) {
                JsonObject item = element.getAsJsonObject();
                String block = item.get("block").getAsString();
                String coef = item.get("coefficients").getAsString();
                blockCoefficientKeys.put(block, coef);
            }

            System.out.println("Loaded " + blockCoefficientKeys.size() + " block coefficient keys");
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load item_map.json", e);
        }
    }
}
