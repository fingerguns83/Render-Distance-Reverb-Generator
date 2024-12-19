package net.fg83.craftverb;

import net.fabricmc.api.ModInitializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Craftverb implements ModInitializer {

    @Override
    public void onInitialize() {
    }



    public static Map<Integer, Double> applyAbsorption(Map<Integer, Double> input, List<AbsorptionCoefficient> attenuator) {
        Map<Integer, Double> output = new HashMap<>();

        AbsorptionCoefficient coefficient;
        for (int i = 0; i < attenuator.size(); i++) {
            coefficient = attenuator.get(i);
            int frequency = coefficient.getFrequency();  // Get the frequency for this coefficient
            double absorption = coefficient.getCoefficient();  // Get the absorption factor
            double currentEnergy = input.getOrDefault(frequency, 0.00);  // Get current energy at that frequency
            output.put(frequency, currentEnergy - (currentEnergy * absorption));
        }
        return output;
    }

    public static Map<Integer, Double> applyDistance(Map<Integer, Double> input, Double distance, List<AbsorptionCoefficient> medium) {
        Map<Integer, Double> output = new HashMap<>();

        AbsorptionCoefficient coefficient;
        for (int i = 0; i < medium.size(); i++){
            coefficient = medium.get(i);
            int frequency = coefficient.getFrequency();  // Get the frequency for this coefficient
            double absorption = coefficient.getCoefficient();  // Get the absorption factor
            double currentEnergy = input.getOrDefault(frequency, 0.00);  // Get current energy at that frequency
            output.put(frequency, currentEnergy - (distance * (currentEnergy * absorption)));
        }
        return output;
    }
}
