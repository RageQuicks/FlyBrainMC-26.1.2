package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative mapping from descending / motor neuron populations to behavioural command channels.
 * Each channel is a weighted sum of normalised population rates (rate / rMax, clipped to 1) smoothed with its own
 * time constant. Negative weights implement lateral differences (right − left) and antagonism.
 *
 * <p>The default map follows the literature summarised in docs/research/dn-behavior.md: DNp09 and Sapkal-2024
 * BDN1-4/oDN1 for forward walking; DNa02 (± DNg13, DNa01) right−left for turning; MDN for backward walking;
 * DNg60 (bluebell), AN19A018 (brake) and DNg74 (web) for halting; DNp01 (giant fibre) for the escape jump;
 * DNp07/DNp10 for landing; DNg02 for wingbeat power; aDN1/aDN2 (DNg62/DNge078) for antennal grooming;
 * DNg12/DNg07/DNg08 head grooming; MN9 + Fudog/Rounddown/Bract for proboscis extension; pC1 for courtship state;
 * pIP10 for song; pMP2 for pulse vs sine mode.</p>
 */
public final class MotorMap {
    public static final class Term {
        public final String spec;
        public final double weight;
        /** Normalisation rate (Hz) for this population; rate / rMax is clipped to [0, 1]. */
        public final double rMaxHz;

        public Term(String spec, double weight, double rMaxHz) {
            this.spec = spec;
            this.weight = weight;
            this.rMaxHz = rMaxHz;
        }
    }

    public static final class Channel {
        public final String name;
        public final List<Term> terms = new ArrayList<>();
        /** Smoothing time constant (ms); 0 = no smoothing (single tick). */
        public final double tauMs;
        /** If true the channel reports whether any spike occurred this tick (used for the giant-fibre jump). */
        public final boolean spikeEvent;

        public Channel(String name, double tauMs, boolean spikeEvent) {
            this.name = name;
            this.tauMs = tauMs;
            this.spikeEvent = spikeEvent;
        }

        public Channel add(String spec, double weight, double rMaxHz) {
            terms.add(new Term(spec, weight, rMaxHz));
            return this;
        }
    }

    public final List<Channel> channels = new ArrayList<>();

    public Channel channel(String name, double tauMs) {
        Channel c = new Channel(name, tauMs, false);
        channels.add(c);
        return c;
    }

    public Channel eventChannel(String name) {
        Channel c = new Channel(name, 0, true);
        channels.add(c);
        return c;
    }

    public Channel get(String name) {
        for (Channel c : channels) if (c.name.equals(name)) return c;
        return null;
    }

    /** Channel names used by {@link MotorDecoder}. */
    public static final String FORWARD = "forward", YAW = "yaw", BACKWARD = "backward", HALT = "halt", BRAKE = "brake",
            JUMP = "jump", TAKEOFF = "takeoff", LANDING = "landing", FLIGHT_POWER = "flightPower", FLIGHT_YAW = "flightYaw",
            WING_MOTOR = "wingMotor", GROOM_ANTENNA = "groomAntenna", GROOM_HEAD = "groomHead", GROOM_LEG = "groomLeg",
            GROOM_ABDOMEN = "groomAbdomen", FEED = "feed", COURTSHIP = "courtship", SONG = "song", SONG_PULSE = "songPulse",
            LEG_MOTOR = "legMotor", LEG_MOTOR_ASYM = "legMotorAsym";

    /** Literature-derived default. rMax values are anchors where published, otherwise 50 Hz. */
    public static MotorMap defaults() {
        MotorMap m = new MotorMap();
        double dn = 50;
        m.channel(FORWARD, 150)
                .add("DNp09", 0.3, dn).add("DNg100", 0.25, dn).add("DNge053", 0.15, dn).add("DNge050", 0.15, dn).add("DNg97", 0.15, dn);
        m.channel(YAW, 100)
                .add("DNa02/R", 1.0, dn).add("DNa02/L", -1.0, dn)
                .add("DNg13/R", 0.5, 100).add("DNg13/L", -0.5, 100)
                .add("DNa01/R", 0.25, dn).add("DNa01/L", -0.25, dn);
        // MDN: baseline 8.5 Hz, active 12-16 Hz in vivo; in this model strong sensory input produces diffuse ~13 Hz MDN
        // activity, so backward walking is only decoded from a sustained >20 Hz (rMax 40 x threshold 0.5)
        m.channel(BACKWARD, 150).add("MDN", 1.0, 40);
        m.channel(HALT, 250).add("DNg60", 0.6, dn).add("DNg74_a,DNg74_b", 0.4, dn);
        m.channel(BRAKE, 250).add("AN19A018", 1.0, dn);
        m.eventChannel(JUMP).add("DNp01", 1.0, 1);
        m.channel(TAKEOFF, 25).add("DNp11", 0.5, dn).add("DNp02", 0.25, dn).add("DNp04", 0.25, dn);
        m.channel(LANDING, 25).add("DNp07", 0.5, 92).add("DNp10", 0.5, dn);
        m.channel(FLIGHT_POWER, 100).add("prefix:DNg02", 1.0, dn);
        // DNg02 drives the contralateral wing (sign flip); DNp03 is contraversive
        m.channel(FLIGHT_YAW, 100)
                .add("prefix:DNg02/L", 0.5, dn).add("prefix:DNg02/R", -0.5, dn)
                .add("DNp03/L", -0.5, dn).add("DNp03/R", 0.5, dn);
        m.channel(WING_MOTOR, 100).add("subclass:wm", 1.0, 80); // readout only (not used to gate flight)
        m.channel(GROOM_ANTENNA, 300).add("DNg62", 0.5, dn).add("DNge078", 0.5, dn);
        m.channel(GROOM_HEAD, 300).add("prefix:DNg12", 0.6, dn).add("DNg07", 0.2, dn).add("DNg08", 0.2, dn);
        m.channel(GROOM_LEG, 300).add("DNg11", 1.0, dn);
        m.channel(GROOM_ABDOMEN, 300).add("DNp29", 1.0, dn);
        m.channel(FEED, 100)
                .add("MN9", 0.6, 60).add("DNg67", 0.1, dn).add("DNge080", 0.1, dn).add("DNge173", 0.1, dn).add("DNge174", 0.1, dn);
        m.channel(COURTSHIP, 30000).add("prefix:pC1_", 1.0, 20);
        m.channel(SONG, 500).add("pIP10", 1.0, dn);
        m.channel(SONG_PULSE, 50).add("pMP2", 1.0, dn);
        m.channel(LEG_MOTOR, 150).add("subclass:fl,subclass:ml,subclass:hl", 1.0, 30);
        m.channel(LEG_MOTOR_ASYM, 150)
                .add("subclass:fl/R,subclass:ml/R,subclass:hl/R", 1.0, 30)
                .add("subclass:fl/L,subclass:ml/L,subclass:hl/L", -1.0, 30);
        return m;
    }
}
