package com.fruitfly.entity;

import com.fruitfly.brain.MotorDecoder;
import com.fruitfly.brain.SensoryFrame;

/**
 * Internal physiological state of an adult Drosophila.
 *
 * This is deliberately not an AI controller. These variables represent homeostatic state
 * (energy, water, protein, sleep pressure, reproductive drive, grooming need, stress and
 * health) and are fed back into the sensory/neuromodulatory interface. The connectome still
 * selects the motor behaviour.
 */
public final class FlyPhysiology {
    private double energy = 0.72;          // stored metabolic energy
    private double water = 0.82;
    private double protein = 0.70;
    private double sleepPressure = 0.10;
    private double reproductiveDrive = 0.35;
    private double groomingNeed = 0.02;
    private double stress = 0.05;
    private double health = 1.0;
    private double ageDays;
    private double awakeSeconds;

    public void tick(FlyEntity fly, SensoryFrame sense, MotorDecoder.MotorCommand cmd, double dtSeconds) {
        if (dtSeconds <= 0) return;

        ageDays += dtSeconds / 86400.0;

        double locomotion = Math.min(1.0,
                Math.abs(cmd.forward) * 0.8 + Math.abs(cmd.backward) * 0.9 +
                Math.abs(cmd.yaw) * 0.25 + cmd.flightPower * 1.5);
        double metabolicRate = 0.000010 + 0.000018 * locomotion;
        energy = clamp(energy - metabolicRate * dtSeconds);
        water = clamp(water - (0.000004 + 0.000008 * locomotion) * dtSeconds);
        protein = clamp(protein - 0.0000025 * dtSeconds);

        boolean moving = locomotion > 0.08 || sense.airborne || sense.wingbeat > 0.15;
        if (moving) awakeSeconds += dtSeconds;
        else awakeSeconds = Math.max(0, awakeSeconds - dtSeconds * 0.15);

        // Sleep pressure rises with sustained wakefulness and falls while the fly is quiescent.
        if (moving) sleepPressure = clamp(sleepPressure + dtSeconds / 90000.0);
        else sleepPressure = clamp(sleepPressure - dtSeconds / 18000.0);

        // Temperature and dehydration increase physiological strain.
        double thermalStress = Math.max(sense.hot, sense.cold) * 0.35;
        double dehydration = Math.max(0, 0.45 - water) * 0.8;
        double malnutrition = Math.max(0, 0.35 - Math.min(energy, protein)) * 0.8;
        stress = clamp(stress + dtSeconds * (thermalStress + dehydration + malnutrition) * 0.00025
                - dtSeconds * 0.00005);

        // Physical damage is supplied through the real nociceptive/tactile sensory path.
        if (sense.damage > 0) {
            stress = clamp(stress + sense.damage * 0.08);
            health = clamp(health - sense.damage * 0.015);
        }

        // Grooming need accumulates from contact/debris and decays when the motor system actually grooms.
        double contamination = Math.max(sense.groomDust,
                Math.max(sense.touchHead, Math.max(sense.touchWing, sense.touchLegs)));
        groomingNeed = clamp(groomingNeed + contamination * dtSeconds * 0.001);
        if (cmd.mode == MotorDecoder.Mode.GROOM) {
            groomingNeed = clamp(groomingNeed - dtSeconds * 0.0025);
            stress = clamp(stress - dtSeconds * 0.00015);
        }

        // Reproductive motivation is state dependent: adults need adequate reserves and low stress.
        double condition = Math.min(energy, Math.min(water, protein));
        double maturity = smoothstep(1.5, 2.0, ageDays);
        reproductiveDrive = clamp(reproductiveDrive
                + dtSeconds * (0.000004 * maturity * condition - 0.000006 * stress));

        // Recovery is strongest during quiet periods with adequate reserves.
        if (!moving && sleepPressure < 0.35 && condition > 0.45) {
            health = clamp(health + dtSeconds * 0.00002);
        }
        if (condition < 0.12 || water < 0.08) {
            health = clamp(health - dtSeconds * 0.00002);
        }
    }

    /** Nutrient uptake from actual feeding/contact. */
    public void feed(double nutrition) {
        double n = Math.max(0, nutrition);
        energy = clamp(energy + n * 0.55);
        protein = clamp(protein + n * 0.25);
        water = clamp(water + n * 0.20);
        stress = clamp(stress - n * 0.03);
    }

    public double hunger() {
        return clamp(1.0 - (0.60 * energy + 0.40 * protein));
    }
    public double thirst() { return clamp(1.0 - water); }
    public double proteinNeed() { return clamp(1.0 - protein); }
    public double satiety() { return 1.0 - hunger(); }
    public double sleepPressure() { return sleepPressure; }
    public double reproductiveDrive() { return reproductiveDrive; }
    public double groomingNeed() { return groomingNeed; }
    public double stress() { return stress; }
    public double health() { return health; }
    public double ageDays() { return ageDays; }
    public double awakeSeconds() { return awakeSeconds; }

    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    private static double smoothstep(double a, double b, double x) {
        double t = clamp((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
