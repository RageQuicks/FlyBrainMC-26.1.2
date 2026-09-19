package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decodes population activity of a tiny descending/motor neuron set into {@link MotorDecoder.MotorCommand}s.
 * Neurons have no synapses, so their activity is exactly what the test drives (Poisson via setStimulusRate, or
 * deterministic via forceSpike).
 */
class MotorDecoderTest {
    private static final double TICK = 50;

    private static final class Dns {
        final Connectome c;
        final PopulationIndex pi;
        final int[] dnp09, dna02L, dna02R, mdn, dnp01, mn9, dng62;

        Dns() {
            AnnotatedConnectome s = new AnnotatedConnectome();
            dnp09 = new int[]{s.neuron("DNp09", 1, "L"), s.neuron("DNp09", 1, "R"), s.neuron("DNp09", 1, "L"), s.neuron("DNp09", 1, "R")};
            dna02L = new int[]{s.neuron("DNa02", 1, "L")};
            dna02R = new int[]{s.neuron("DNa02", 1, "R")};
            mdn = new int[]{s.neuron("MDN", 1, "L"), s.neuron("MDN", 1, "R")};
            dnp01 = new int[]{s.neuron("DNp01", 1, "L"), s.neuron("DNp01", 1, "R")};
            mn9 = new int[]{s.neuron("MN9", 1, "L"), s.neuron("MN9", 1, "R")};
            dng62 = new int[]{s.neuron("DNg62", 1, "L"), s.neuron("DNg62", 1, "R")};
            for (int i = 0; i < 20; i++) s.neuron("bystander", 1, "L");
            c = s.build();
            pi = new PopulationIndex(c);
        }
    }

    private static LifNetwork network(Connectome c) {
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.5;
        cfg.threads = 1;
        return new LifNetwork(c, cfg);
    }

    /** One brain tick in the order used by FlyEntity/BrainRunner: integrate, decode, close the window. */
    private static MotorDecoder.MotorCommand tick(LifNetwork net, MotorDecoder dec) {
        net.runMs(TICK);
        MotorDecoder.MotorCommand cmd = dec.update(net, TICK);
        net.endTick(TICK);
        return cmd;
    }

    private static MotorDecoder.MotorCommand ticks(LifNetwork net, MotorDecoder dec, int n) {
        MotorDecoder.MotorCommand cmd = null;
        for (int i = 0; i < n; i++) cmd = tick(net, dec);
        return cmd;
    }

    @Test
    void silentBrainDecodesToIdle() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        assertEquals(MotorDecoder.Mode.IDLE, dec.latest().mode);
        MotorDecoder.MotorCommand cmd = ticks(net, dec, 5);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
        assertFalse(cmd.jump);
        assertFalse(dec.isFlying());
        assertEquals(0, cmd.forward + cmd.yaw + cmd.backward + cmd.stop + cmd.groom() + cmd.feed + cmd.song, 0.0);
        assertEquals(MotorMap.defaults().channels.size(), cmd.channels.size(), "every channel is reported");
        assertSame(cmd, dec.latest());
    }

    @Test
    void dnp09DriveRaisesTheForwardChannelAndSwitchesToForwardWalking() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        net.setStimulusRate(d.dnp09, 50);
        List<Double> fwd = new ArrayList<>();
        MotorDecoder.MotorCommand cmd = null;
        for (int t = 0; t < 12; t++) {
            cmd = tick(net, dec);
            fwd.add(cmd.forward);
        }
        assertTrue(fwd.get(11) > fwd.get(0), "forward channel integrates upwards: " + fwd);
        // DNp09 weight 0.3 at rMax 50 Hz -> ~0.25 steady state through the 150 ms filter
        assertTrue(cmd.forward > 0.15 && cmd.forward <= 0.3, "forward=" + cmd.forward);
        assertEquals(MotorDecoder.Mode.FORWARD, cmd.mode);
        assertEquals(0, cmd.yaw, 1e-9, "symmetric DNp09 drive produces no turn");
        assertEquals(cmd.forward, cmd.channels.get(MotorMap.FORWARD), 1e-12);

        // drive off: the channel decays and the mode is released after the dwell time
        net.clearStimuli();
        cmd = ticks(net, dec, 20);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
        assertTrue(cmd.forward < 0.01, "forward decayed to " + cmd.forward);
    }

    @Test
    void dna02RightTurnsRightAndLeftTurnsLeft() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        net.setStimulusRate(d.dna02R, 50);
        MotorDecoder.MotorCommand cmd = ticks(net, dec, 10);
        assertTrue(cmd.yaw > 0.25, "right DNa02 -> positive yaw, was " + cmd.yaw);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode, "yaw alone does not select a locomotor mode");

        net.clearStimuli();
        net.setStimulusRate(d.dna02L, 50);
        cmd = ticks(net, dec, 20);
        assertTrue(cmd.yaw < -0.25, "left DNa02 -> negative yaw, was " + cmd.yaw);

        net.setStimulusRate(d.dna02R, 50); // both sides: the difference cancels on average
        ticks(net, dec, 10);
        double mean = 0;
        for (int t = 0; t < 20; t++) mean += tick(net, dec).yaw / 20;
        assertEquals(0, mean, 0.3, "bilateral DNa02 roughly cancels, mean yaw was " + mean);
    }

    @Test
    void backwardWalkingGatesForwardWalking() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        net.setStimulusRate(d.dnp09, 50);
        net.setStimulusRate(d.mdn, 50); // MDN normalised at 16 Hz -> saturates
        MotorDecoder.MotorCommand cmd = ticks(net, dec, 12);
        assertTrue(cmd.forward > new MotorDecoder.Params().forwardOn, "forward drive is present: " + cmd.forward);
        assertTrue(cmd.backward > 0.5, "backward=" + cmd.backward);
        assertEquals(MotorDecoder.Mode.BACKWARD, cmd.mode, "backward has priority over forward");

        net.setStimulusRate(d.mdn, 0);
        cmd = ticks(net, dec, 20);
        assertEquals(MotorDecoder.Mode.FORWARD, cmd.mode, "forward resumes once MDN is quiet");
    }

    @Test
    void giantFibreSpikeTriggersEscapeWithLockoutThenFlightThenIdle() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder.Params p = new MotorDecoder.Params();
        p.escapeLockoutMs = 200;
        p.flightMinMs = 400;
        p.minDwellMs = 250;
        MotorDecoder dec = new MotorDecoder(d.pi, MotorMap.defaults(), p);

        net.forceSpike(d.dnp01[0]);
        MotorDecoder.MotorCommand first = tick(net, dec);   // t = 50
        assertTrue(first.jump, "a single DNp01 spike is a jump event");
        assertEquals(MotorDecoder.Mode.ESCAPE, first.mode);
        assertTrue(dec.isFlying(), "the escape jump launches the fly");
        assertEquals(1.0, first.channels.get(MotorMap.JUMP), 1e-12);

        List<MotorDecoder.Mode> modes = new ArrayList<>();
        List<Boolean> jumps = new ArrayList<>();
        for (int t = 0; t < 9; t++) {                       // t = 100 .. 500
            MotorDecoder.MotorCommand cmd = tick(net, dec);
            modes.add(cmd.mode);
            jumps.add(cmd.jump);
        }
        assertFalse(jumps.contains(true), "jump is reported only on the tick the giant fibre fired");
        // lockout 200 ms: ticks at t=100,150,200 keep ESCAPE; from t=250 the flight state machine takes over
        assertEquals(List.of(MotorDecoder.Mode.ESCAPE, MotorDecoder.Mode.ESCAPE, MotorDecoder.Mode.ESCAPE), modes.subList(0, 3), "lockout");
        // airborne until flightMinMs (400 ms after the jump) and the 250 ms dwell in FLYING have both elapsed (t=500)
        assertEquals(List.of(MotorDecoder.Mode.FLYING, MotorDecoder.Mode.FLYING, MotorDecoder.Mode.FLYING, MotorDecoder.Mode.FLYING, MotorDecoder.Mode.FLYING),
                modes.subList(3, 8), "flight after the escape");
        assertEquals(MotorDecoder.Mode.IDLE, modes.get(8), "lands once flight power is absent");
        assertFalse(dec.isFlying());
    }

    @Test
    void escapeOverridesOngoingWalkingAndIgnoresWalkingDuringLockout() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        net.setStimulusRate(d.dnp09, 80);
        MotorDecoder.MotorCommand cmd = ticks(net, dec, 10);
        assertEquals(MotorDecoder.Mode.FORWARD, cmd.mode);
        net.forceSpike(d.dnp01[1]);
        cmd = tick(net, dec);
        assertEquals(MotorDecoder.Mode.ESCAPE, cmd.mode);
        assertTrue(cmd.jump);
        cmd = ticks(net, dec, 3);
        assertEquals(MotorDecoder.Mode.ESCAPE, cmd.mode, "walking drive cannot break the escape lockout");
        assertTrue(cmd.forward > 0.15, "the forward channel keeps integrating underneath");
    }

    @Test
    void modePersistsWithHysteresisUntilDriveFallsBelowTheOffRatio() {
        // deterministic drive: ten DNp09 cells, k forced spikes per 50 ms tick -> population rate 2k Hz -> value 0.1k
        AnnotatedConnectome s = new AnnotatedConnectome();
        int[] dn = s.neurons(10, "DNp09", 1, "L", "", "");
        Connectome c = s.build();
        PopulationIndex pi = new PopulationIndex(c);
        MotorMap map = new MotorMap();
        map.channel(MotorMap.FORWARD, 0).add("DNp09", 1.0, 20);
        MotorDecoder.Params p = new MotorDecoder.Params();
        p.forwardOn = 0.5;
        p.offRatio = 0.5;
        p.minDwellMs = 0;
        MotorDecoder dec = new MotorDecoder(pi, map, p);
        LifNetwork net = network(c);

        // below threshold from IDLE: 0.3 < 0.5 -> stays IDLE
        MotorDecoder.MotorCommand cmd = drive(net, dec, dn, 3);
        assertEquals(0.3, cmd.forward, 1e-9);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
        // above threshold: 0.6 > 0.5 -> FORWARD
        cmd = drive(net, dec, dn, 6);
        assertEquals(0.6, cmd.forward, 1e-9);
        assertEquals(MotorDecoder.Mode.FORWARD, cmd.mode);
        // drop to 0.6 x threshold = 0.3: above the off level (0.25) -> mode persists for as long as it lasts
        for (int t = 0; t < 5; t++) {
            cmd = drive(net, dec, dn, 3);
            assertEquals(0.3, cmd.forward, 1e-9);
            assertEquals(MotorDecoder.Mode.FORWARD, cmd.mode, "hysteresis keeps FORWARD at tick " + t);
        }
        // below the off level: 0.2 < 0.25 -> released
        cmd = drive(net, dec, dn, 2);
        assertEquals(0.2, cmd.forward, 1e-9);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
        // and 0.3 again does not re-enter (needs > forwardOn)
        cmd = drive(net, dec, dn, 3);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
    }

    @Test
    void minimumDwellTimeHoldsForwardEvenWhenDriveVanishes() {
        AnnotatedConnectome s = new AnnotatedConnectome();
        int[] dn = s.neurons(10, "DNp09", 1, "L", "", "");
        Connectome c = s.build();
        PopulationIndex pi = new PopulationIndex(c);
        MotorMap map = new MotorMap();
        map.channel(MotorMap.FORWARD, 0).add("DNp09", 1.0, 20);
        MotorDecoder.Params p = new MotorDecoder.Params();
        p.forwardOn = 0.5;
        p.minDwellMs = 200; // 4 ticks
        MotorDecoder dec = new MotorDecoder(pi, map, p);
        LifNetwork net = network(c);
        assertEquals(MotorDecoder.Mode.FORWARD, drive(net, dec, dn, 10).mode);  // t=50, modeSince=50
        assertEquals(MotorDecoder.Mode.FORWARD, drive(net, dec, dn, 0).mode);   // t=100: 50 ms in mode
        assertEquals(MotorDecoder.Mode.FORWARD, drive(net, dec, dn, 0).mode);   // t=150
        assertEquals(MotorDecoder.Mode.FORWARD, drive(net, dec, dn, 0).mode);   // t=200
        assertEquals(MotorDecoder.Mode.IDLE, drive(net, dec, dn, 0).mode);      // t=250: dwell satisfied
    }

    @Test
    void mn9DriveSelectsFeedingAndAdn1SelectsGrooming() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        net.setStimulusRate(d.mn9, 60); // MN9 30-90 Hz during a sugar meal
        MotorDecoder.MotorCommand cmd = ticks(net, dec, 12);
        assertTrue(cmd.feed > new MotorDecoder.Params().feedOn, "feed=" + cmd.feed);
        assertEquals(MotorDecoder.Mode.FEED, cmd.mode);
        assertTrue(cmd.toString().contains("FEED"), cmd.toString());

        net.clearStimuli();
        ticks(net, dec, 20);
        net.setStimulusRate(d.dng62, 50); // aDN1 antennal grooming
        cmd = ticks(net, dec, 25);
        assertTrue(cmd.groomAntenna > new MotorDecoder.Params().groomOn, "groomAntenna=" + cmd.groomAntenna);
        assertEquals(cmd.groomAntenna, cmd.groom(), 1e-12);
        assertEquals(MotorDecoder.Mode.GROOM, cmd.mode);
        assertEquals(0, cmd.groomHead + cmd.groomLeg + cmd.groomAbdomen, 0.0);
    }

    @Test
    void populationSizesReflectTheResolvedNeurons() {
        Dns d = new Dns();
        MotorDecoder dec = new MotorDecoder(d.pi);
        var sizes = dec.populationSizes();
        assertEquals(4, sizes.get(MotorMap.FORWARD), "only DNp09 exists in this connectome");
        assertEquals(2, sizes.get(MotorMap.YAW), "DNa02/L + DNa02/R");
        assertEquals(2, sizes.get(MotorMap.BACKWARD));
        assertEquals(2, sizes.get(MotorMap.JUMP));
        assertEquals(2, sizes.get(MotorMap.FEED));
        assertEquals(2, sizes.get(MotorMap.GROOM_ANTENNA));
        assertEquals(0, sizes.get(MotorMap.SONG));
        assertEquals(MotorMap.defaults().channels.size(), sizes.size());
    }

    @Test
    void setFlyingIsHonouredByTheFlightStateMachine() {
        Dns d = new Dns();
        LifNetwork net = network(d.c);
        MotorDecoder dec = new MotorDecoder(d.pi);
        dec.setFlying(true);
        MotorDecoder.MotorCommand cmd = tick(net, dec);
        assertEquals(MotorDecoder.Mode.FLYING, cmd.mode, "an externally launched fly is decoded as flying");
        // with no flight power it lands after flightMinMs + dwell
        cmd = ticks(net, dec, 20);
        assertEquals(MotorDecoder.Mode.IDLE, cmd.mode);
        assertFalse(dec.isFlying());
    }

    /** Force {@code k} of the DNp09 cells to spike this tick, then run the tick. */
    private static MotorDecoder.MotorCommand drive(LifNetwork net, MotorDecoder dec, int[] dn, int k) {
        for (int i = 0; i < k; i++) net.forceSpike(dn[i]);
        return tick(net, dec);
    }
}
