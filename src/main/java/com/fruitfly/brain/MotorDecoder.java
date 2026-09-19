package com.fruitfly.brain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns descending / motor neuron population activity into a {@link MotorCommand}: continuous channel values plus an
 * arbitrated behavioural mode following the priority ladder from the DN literature (escape > landing > brake >
 * halt > backward > forward/yaw > groom > song/feed), with hysteresis and minimum dwell times.
 *
 * <p>Runs on the brain thread: call {@link #update(LifNetwork, double)} once per brain tick after stepping and
 * before {@code endTick}. The resulting command is immutable and can be handed to the game thread.</p>
 */
public final class MotorDecoder {
    public enum Mode { IDLE, FORWARD, BACKWARD, HALT, BRAKE, ESCAPE, FLYING, LANDING, GROOM, FEED, SONG }

    /** Immutable decoded command. */
    public static final class MotorCommand {
        public final Map<String, Double> channels;
        public final Mode mode;
        /** 0..1 forward drive (walking speed fraction). */
        public final double forward;
        /** −1..1 yaw drive (positive = turn right). */
        public final double yaw;
        /** 0..1 backward drive. */
        public final double backward;
        /** 0..1 stop drive (halt + brake). */
        public final double stop;
        /** true on the tick the giant fibre fired. */
        public final boolean jump;
        public final double landing, flightPower, flightYaw, wingMotor;
        public final double groomAntenna, groomHead, groomLeg, groomAbdomen;
        public final double feed, courtship, song, songPulse;
        public final double legMotor, legMotorAsym;

        MotorCommand(Map<String, Double> ch, Mode mode, boolean jump) {
            this.channels = ch;
            this.mode = mode;
            this.jump = jump;
            this.forward = ch.getOrDefault(MotorMap.FORWARD, 0.0);
            this.yaw = ch.getOrDefault(MotorMap.YAW, 0.0);
            this.backward = ch.getOrDefault(MotorMap.BACKWARD, 0.0);
            this.stop = Math.max(ch.getOrDefault(MotorMap.HALT, 0.0), ch.getOrDefault(MotorMap.BRAKE, 0.0));
            this.landing = ch.getOrDefault(MotorMap.LANDING, 0.0);
            this.flightPower = ch.getOrDefault(MotorMap.FLIGHT_POWER, 0.0);
            this.flightYaw = ch.getOrDefault(MotorMap.FLIGHT_YAW, 0.0);
            this.wingMotor = ch.getOrDefault(MotorMap.WING_MOTOR, 0.0);
            this.groomAntenna = ch.getOrDefault(MotorMap.GROOM_ANTENNA, 0.0);
            this.groomHead = ch.getOrDefault(MotorMap.GROOM_HEAD, 0.0);
            this.groomLeg = ch.getOrDefault(MotorMap.GROOM_LEG, 0.0);
            this.groomAbdomen = ch.getOrDefault(MotorMap.GROOM_ABDOMEN, 0.0);
            this.feed = ch.getOrDefault(MotorMap.FEED, 0.0);
            this.courtship = ch.getOrDefault(MotorMap.COURTSHIP, 0.0);
            this.song = ch.getOrDefault(MotorMap.SONG, 0.0);
            this.songPulse = ch.getOrDefault(MotorMap.SONG_PULSE, 0.0);
            this.legMotor = ch.getOrDefault(MotorMap.LEG_MOTOR, 0.0);
            this.legMotorAsym = ch.getOrDefault(MotorMap.LEG_MOTOR_ASYM, 0.0);
        }

        public double groom() { return Math.max(Math.max(groomAntenna, groomHead), Math.max(groomLeg, groomAbdomen)); }

        public static MotorCommand idle() { return new MotorCommand(new HashMap<>(), Mode.IDLE, false); }

        @Override
        public String toString() {
            return String.format("Motor[%s fwd=%.2f yaw=%.2f bwd=%.2f stop=%.2f fly=%.2f land=%.2f groom=%.2f feed=%.2f song=%.2f%s]",
                    mode, forward, yaw, backward, stop, flightPower, landing, groom(), feed, song, jump ? " JUMP" : "");
        }
    }

    /** Thresholds and dwell times for arbitration. */
    public static final class Params {
        public double forwardOn = 0.08, backwardOn = 0.5, stopOn = 0.25, landingOn = 0.3, groomOn = 0.2, feedOn = 0.2,
                songOn = 0.2, flightOn = 0.3;
        /** Hysteresis: a mode is left when its drive falls below on × offRatio. */
        public double offRatio = 0.5;
        public double minDwellMs = 250;
        public double escapeLockoutMs = 200;
        public double flightMinMs = 400;
        /** Minimum interval between two giant-fibre escape jumps (real flies do not re-jump every 50 ms). */
        public double jumpRefractoryMs = 1500;
    }

    private final MotorMap map;
    private final Params params;
    private final int[][][] termIdx;     // [channel][term] -> neuron indices
    private final double[] value;        // smoothed channel values
    private final int nCh;
    private volatile MotorCommand latest = MotorCommand.idle();
    private Mode mode = Mode.IDLE;
    private double modeSinceMs, timeMs, escapeUntilMs, flyingSinceMs = -1, jumpUntilMs;
    private volatile boolean flying;

    public MotorDecoder(PopulationIndex pi, MotorMap map, Params params) {
        this.map = map;
        this.params = params;
        this.nCh = map.channels.size();
        this.termIdx = new int[nCh][][];
        this.value = new double[nCh];
        for (int c = 0; c < nCh; c++) {
            MotorMap.Channel ch = map.channels.get(c);
            termIdx[c] = new int[ch.terms.size()][];
            for (int t = 0; t < ch.terms.size(); t++) termIdx[c][t] = pi.resolve(ch.terms.get(t).spec);
        }
    }

    public MotorDecoder(PopulationIndex pi) { this(pi, MotorMap.defaults(), new Params()); }

    public MotorCommand latest() { return latest; }
    public boolean isFlying() { return flying; }
    public void setFlying(boolean f) { flying = f; if (f) flyingSinceMs = timeMs; }

    /** Number of neurons resolved per channel (for diagnostics). */
    public Map<String, Integer> populationSizes() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int c = 0; c < nCh; c++) {
            int n = 0;
            for (int[] ids : termIdx[c]) n += ids.length;
            m.put(map.channels.get(c).name, n);
        }
        return m;
    }

    /** Read this tick's spike counts from the network and produce a new command. */
    public MotorCommand update(LifNetwork net, double tickMs) {
        timeMs += tickMs;
        Map<String, Double> out = new HashMap<>(nCh * 2);
        boolean jump = false;
        for (int c = 0; c < nCh; c++) {
            MotorMap.Channel ch = map.channels.get(c);
            double raw = 0;
            for (int t = 0; t < ch.terms.size(); t++) {
                MotorMap.Term term = ch.terms.get(t);
                int[] ids = termIdx[c][t];
                if (ids.length == 0) continue;
                if (ch.spikeEvent) {
                    if (net.spikesThisTick(ids) > 0) raw = 1;
                } else {
                    double hz = net.populationRateHz(ids, tickMs);
                    raw += term.weight * Math.min(1.0, hz / term.rMaxHz);
                }
            }
            if (ch.spikeEvent) {
                value[c] = raw;
                if (raw > 0 && ch.name.equals(MotorMap.JUMP)) jump = true;
            } else if (ch.tauMs <= 0) {
                value[c] = raw;
            } else {
                double a = 1 - Math.exp(-tickMs / ch.tauMs);
                value[c] += a * (raw - value[c]);
            }
            out.put(ch.name, value[c]);
        }
        // one giant-fibre spike = one jump; ignore further spikes during the jump refractory period
        if (jump) {
            if (timeMs < jumpUntilMs) jump = false;
            else jumpUntilMs = timeMs + params.jumpRefractoryMs;
        }
        Mode m = arbitrate(out, jump);
        latest = new MotorCommand(out, m, jump);
        return latest;
    }

    private Mode arbitrate(Map<String, Double> v, boolean jump) {
        double fwd = v.getOrDefault(MotorMap.FORWARD, 0.0), bwd = v.getOrDefault(MotorMap.BACKWARD, 0.0);
        double halt = v.getOrDefault(MotorMap.HALT, 0.0), brake = v.getOrDefault(MotorMap.BRAKE, 0.0);
        double land = v.getOrDefault(MotorMap.LANDING, 0.0), power = v.getOrDefault(MotorMap.FLIGHT_POWER, 0.0);
        double wing = v.getOrDefault(MotorMap.WING_MOTOR, 0.0), takeoff = v.getOrDefault(MotorMap.TAKEOFF, 0.0);
        double groom = Math.max(Math.max(v.getOrDefault(MotorMap.GROOM_ANTENNA, 0.0), v.getOrDefault(MotorMap.GROOM_HEAD, 0.0)),
                Math.max(v.getOrDefault(MotorMap.GROOM_LEG, 0.0), v.getOrDefault(MotorMap.GROOM_ABDOMEN, 0.0)));
        double feed = v.getOrDefault(MotorMap.FEED, 0.0), song = v.getOrDefault(MotorMap.SONG, 0.0);
        double off = params.offRatio;

        // 0. escape: a giant-fibre spike overrides everything
        if (jump) {
            escapeUntilMs = timeMs + params.escapeLockoutMs;
            flying = true;
            flyingSinceMs = timeMs;
            return switchTo(Mode.ESCAPE);
        }
        if (timeMs < escapeUntilMs) return mode;
        boolean dwellOk = timeMs - modeSinceMs >= params.minDwellMs;

        // flight state machine: take-off is commanded by the DNg02 wingbeat-power DNs or the take-off DNs; the wing
        // motor-neuron pool is a readout only (it saturates under many stimuli and would lock the fly in flight)
        if (!flying && (power > params.flightOn || takeoff > params.flightOn)) {
            flying = true;
            flyingSinceMs = timeMs;
        }
        if (flying) {
            boolean canLand = timeMs - flyingSinceMs >= params.flightMinMs;
            if (canLand && land > params.landingOn) return switchTo(Mode.LANDING);
            if (mode == Mode.LANDING) {
                if (land < params.landingOn * off) { flying = false; return switchTo(Mode.IDLE); }
                return mode;
            }
            if (canLand && power < params.flightOn * off && mode == Mode.FLYING && dwellOk) {
                flying = false;
                return switchTo(Mode.IDLE);
            }
            return switchTo(Mode.FLYING);
        }

        // 2-3. stopping commands beat walking
        if (brake > params.stopOn) return switchTo(Mode.BRAKE);
        if (mode == Mode.BRAKE && brake > params.stopOn * off) return mode;
        if (halt > params.stopOn) return switchTo(Mode.HALT);
        if (mode == Mode.HALT && halt > params.stopOn * off) return mode;
        // 4. backward gates forward
        if (bwd > params.backwardOn) return switchTo(Mode.BACKWARD);
        if (mode == Mode.BACKWARD && bwd > params.backwardOn * off) return mode;
        // 5. forward
        if (fwd > params.forwardOn) return switchTo(Mode.FORWARD);
        if (mode == Mode.FORWARD && (fwd > params.forwardOn * off || !dwellOk)) return mode;
        // 7-8. stationary behaviours
        if (feed > params.feedOn) return switchTo(Mode.FEED);
        if (mode == Mode.FEED && feed > params.feedOn * off) return mode;
        if (groom > params.groomOn) return switchTo(Mode.GROOM);
        if (mode == Mode.GROOM && (groom > params.groomOn * off || !dwellOk)) return mode;
        if (song > params.songOn) return switchTo(Mode.SONG);
        if (mode == Mode.SONG && song > params.songOn * off) return mode;
        return switchTo(Mode.IDLE);
    }

    private Mode switchTo(Mode m) {
        if (m != mode) {
            mode = m;
            modeSinceMs = timeMs;
        }
        return mode;
    }
}
