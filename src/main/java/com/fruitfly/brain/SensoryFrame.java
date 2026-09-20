package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-independent description of what the fly senses during one tick. The game fills this from the world;
 * {@link SensoryEncoders} turns it into firing rates of the real sensory neuron types.
 *
 * <p>Head coordinates: azimuth 0 = straight ahead, positive = fly's right; elevation positive = up.</p>
 */
public final class SensoryFrame {
    /** Luminance 0..1 per retina column (index = {@link RetinaGeometry} column index). NaN = not sampled this tick. */
    public float[] luminance = new float[0];
    /** Chromatic channels (0..1) per retina column for color vision (R7 UV, R8 blue/green). NaN = not sampled. */
    public float[] red = new float[0];
    public float[] green = new float[0];
    public float[] blue = new float[0];
    public float[] uv = new float[0];

    /** Salient visual objects (entities, thrown items, hands) for feature-level drive of LC neurons. */
    public final List<VisualObject> objects = new ArrayList<>();

    /** Self-rotation for optic-flow neurons (deg/s, positive yaw = turning right, positive pitch = nose up, roll = right down). */
    public float yawRateDegPerS, pitchRateDegPerS, rollRateDegPerS;

    /** Glomerulus (e.g. "DM1", "VA2", "V", "VP2") → effective drive 0..1+ (already includes distance fall-off). */
    public final Map<String, Float> odor = new HashMap<>();
    /** Bearing (deg) of the dominant odor source, NaN if none. Used for bilateral ORN gain. */
    public float odorBearingDeg = Float.NaN;
    /** Drive and bearing of actual plant/sugar food items, kept separate from generic appetitive odor. */
    public float foodDrive;
    public float foodBearingDeg = Float.NaN;

    /** GRN type (e.g. "LB3b", "LgLG3", "PhG1a") → contact drive 0..1. Fires only on contact. */
    public final Map<String, Float> taste = new HashMap<>();

    /** Antennal deflection by airflow 0..1 per side (wind or self-motion airspeed). */
    public float windLeft, windRight;
    /** Body tilt relative to gravity, 0..1. */
    public float tilt;
    /** Sound in the JO-B (<100 Hz) and JO-A (>100 Hz) bands, 0..1; song = a conspecific's courtship song. */
    public float soundLow, soundHigh, song;
    /** Touch / bristle deflection 0..1 per body region. */
    public float touchHead, touchWing, touchLegs, touchNotum, touchAbdomen;
    /** Debris on the head/antennae (rain, dust, cobweb) 0..1 → grooming trigger (JO-F, BM_InOm, BM_Taste). */
    public float groomDust;
    /** Nociceptive event 0..1 (damage taken). */
    public float damage;
    /** Thermo/hygro 0..1. */
    public float hot, cold, dry, moist;
    /** Body state flags (proprioception). */
    public boolean airborne, legsOnGround = true;
    /** Wingbeat active (haltere/campaniform feedback) 0..1. */
    public float wingbeat;

    public void clear() {
        Arrays.fill(luminance, Float.NaN);
        if (red != null && red.length > 0) Arrays.fill(red, Float.NaN);
        if (green != null && green.length > 0) Arrays.fill(green, Float.NaN);
        if (blue != null && blue.length > 0) Arrays.fill(blue, Float.NaN);
        if (uv != null && uv.length > 0) Arrays.fill(uv, Float.NaN);
        objects.clear();
        yawRateDegPerS = pitchRateDegPerS = rollRateDegPerS = 0;
        odor.clear();
        odorBearingDeg = Float.NaN;
        foodDrive = 0;
        foodBearingDeg = Float.NaN;
        taste.clear();
        windLeft = windRight = tilt = 0;
        soundLow = soundHigh = song = 0;
        touchHead = touchWing = touchLegs = touchNotum = touchAbdomen = 0;
        groomDust = damage = 0;
        hot = cold = dry = moist = 0;
        airborne = false;
        legsOnGround = true;
        wingbeat = 0;
    }

    /** Add odor drive for a glomerulus (accumulates across sources). */
    public void addOdor(String glomerulus, float drive) {
        odor.merge(glomerulus, drive, Float::sum);
    }

    public void addTaste(String grnType, float drive) {
        taste.merge(grnType, drive, Float::max);
    }

    /** A visual object in head coordinates. */
    public static final class VisualObject {
        public float azimuthDeg, elevationDeg;
        /** Angular diameter (deg). */
        public float angularSizeDeg;
        /** Rate of change of angular diameter (deg/s); positive = approaching / expanding. */
        public float expansionDegPerS;
        /** Angular velocity across the retina (deg/s). */
        public float angularSpeedDegPerS;
        /** Darkness contrast 0..1 (1 = black object on bright background). */
        public float contrast = 1f;
        /** Another fly (courtship target candidate). */
        public boolean flyLike;

        public VisualObject() {}

        public VisualObject(float az, float el, float size, float expansion, float speed, boolean flyLike) {
            this.azimuthDeg = az;
            this.elevationDeg = el;
            this.angularSizeDeg = size;
            this.expansionDegPerS = expansion;
            this.angularSpeedDegPerS = speed;
            this.flyLike = flyLike;
        }
    }
}
