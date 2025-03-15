package net.fg83.rdrgen;

/**
 * The AbsorptionCoefficient class represents the relationship between a specific frequency band
 * and its corresponding attenuation coefficient, which is used to describe the absorption characteristics
 * of a material or medium at that frequency.
 * This class is immutable and provides methods to access the frequency band and the attenuation coefficient values.
 */
public class AbsorptionCoefficient {
    private final int frequencyBand;
    private final double attenuationCoefficient;

    public AbsorptionCoefficient(int frequencyBand, double attenuationCoefficient) {
        this.frequencyBand = frequencyBand;
        this.attenuationCoefficient = attenuationCoefficient;
    }

    public int getFrequency() {
        return frequencyBand;
    }

    public double getCoefficient() {
        return attenuationCoefficient;
    }
}
