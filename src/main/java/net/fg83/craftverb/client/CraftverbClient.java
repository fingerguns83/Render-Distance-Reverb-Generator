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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

public class CraftverbClient implements ClientModInitializer {

    public static final Map<String, List<AbsorptionCoefficient>> absorptionCoefficients = new HashMap<>();
    public static final Map<String, String> blockCoefficientKeys = new HashMap<>();
    public Map<Double, Map<Integer, Double>> irMatrix = new HashMap<>();
    public Map<Integer, AudioEvent> irBands = new HashMap<>();

    private static KeyBinding keyBinding;
    private boolean isKeyPressed = false;

    Queue<Ray> rayQueue = new LinkedList<>();

    @Override
    public void onInitializeClient() {
        loadAbsorptionCoefficients();
        loadBlockCoefficientKeys();

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
                    client.player.sendMessage(Text.of("Starting acoustic trace..."), false);
                    processRay(client);
                    isKeyPressed = true;
                }
            } else {
                isKeyPressed = false; // Reset when the key is released
            }
        });

    }

    private void processRay(MinecraftClient client) {
        assert client.player != null;

        Entity camera = client.cameraEntity;

        if (camera == null) {
            return;
        }
        int rayNumber = 0;
        System.out.println("Starting trace...");
        for (float pitch = -90.0F; pitch <= 90.0F; pitch += 0.1F) {
            for (float yaw = -180.0F; yaw <= 180.0F; yaw += 0.1F) {

                Vec3d startPos = camera.getEyePos();
                Vec3d currentDir = camera.getRotationVector(pitch, yaw); // Modify yaw/pitch here

                Ray ray = new Ray(startPos, currentDir, camera);
                ray.trace();
                addEnergyToIR(ray.getDelayTime(), ray.getEnergy());
                if (rayNumber % 64800 == 0){

                    String progress = String.valueOf((((Math.round((double) rayNumber * 1000) / 1000) / 64800000) * 100));
                    int length = Math.min(progress.length(), 4);
                    //client.player.sendMessage(Text.of(progress.substring(0, length) + "%"), false);
                    System.out.println(progress.substring(0, length) + "%");
                }
                rayNumber++;
            }
        }
        System.out.println("Trace complete");
        System.out.println("Generating IR...");
        generateIR();
    }

    private void generateIR(){
        for (int frequencyBand : Ray.FREQUENCY_BANDS) {
            int sampleLength = (int) Math.round(irMatrix.keySet().stream().max(Comparator.naturalOrder()).get()) + 1;
            int min = (int) Math.round(irMatrix.keySet().stream().min(Comparator.naturalOrder()).get()) + 1;

            System.out.println("Length: " + sampleLength + " samples (" + sampleLength / 48000.0 + " seconds)");
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

        System.out.println("IR generated");
        System.out.println("Writing WAV file");
        AudioUtils.writeWavFile("finalIR.wav", combinedIR);
    }

    private void addEnergyToIR(double delayTime, Map<Integer, Double> newEnergy){
        irMatrix.merge(delayTime, newEnergy, (existingEnergy, incomingEnergy) -> {
            incomingEnergy.forEach((frequency, energy) ->
                    existingEnergy.merge(frequency, energy, Double::sum)
            );
            return existingEnergy;
        });
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
