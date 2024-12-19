package net.fg83.craftverb;

import java.util.List;

public class Medium {
    public static List<AbsorptionCoefficient> AIR = List.of(
        new net.fg83.craftverb.AbsorptionCoefficient(125, 0.00),
        new net.fg83.craftverb.AbsorptionCoefficient(250, 0.001),
        new net.fg83.craftverb.AbsorptionCoefficient(500, 0.003),
        new net.fg83.craftverb.AbsorptionCoefficient(1000, 0.005),
        new net.fg83.craftverb.AbsorptionCoefficient(2000, 0.010),
        new net.fg83.craftverb.AbsorptionCoefficient(4000, 0.029)
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
