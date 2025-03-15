package net.fg83.rdrgen;

import java.util.List;

/**
 * Represents a collection of predefined mediums and their absorption coefficients
 * across different frequency bands. The absorption coefficients are used to quantify
 * the attenuation of sound as it propagates through a specific medium.
 *
 * This class provides static data for commonly encountered mediums including AIR,
 * WATER, and LAVA. Each medium is represented as a list of {@code AbsorptionCoefficient}
 * objects, where each object specifies the frequency band and the corresponding
 * attenuation coefficient.
 *
 * The data provided within this class can be used in acoustical simulations or
 * calculations where accurate medium-specific attenuation properties are required.
 */
public class Medium {
    public static List<AbsorptionCoefficient> AIR = List.of(
        new AbsorptionCoefficient(125, 0.00),
        new AbsorptionCoefficient(250, 0.001),
        new AbsorptionCoefficient(500, 0.003),
        new AbsorptionCoefficient(1000, 0.005),
        new AbsorptionCoefficient(2000, 0.010),
        new AbsorptionCoefficient(4000, 0.029)
    );

    public static List<AbsorptionCoefficient> WATER = List.of(
        new AbsorptionCoefficient(125, 0.001),
        new AbsorptionCoefficient(250, 0.004),
        new AbsorptionCoefficient(500, 0.015),
        new AbsorptionCoefficient(1000, 0.051),
        new AbsorptionCoefficient(2000, 0.127),
        new AbsorptionCoefficient(4000, 0.244)
    );

    public static List<AbsorptionCoefficient> LAVA = List.of(
        new AbsorptionCoefficient(125, 0.000037),
        new AbsorptionCoefficient(250, 0.000147),
        new AbsorptionCoefficient(500, 0.000588),
        new AbsorptionCoefficient(1000, 0.00236),
        new AbsorptionCoefficient(2000, 0.00942),
        new AbsorptionCoefficient(4000, 0.0377)
    );
}
