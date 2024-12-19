package net.fg83.craftverb;

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
