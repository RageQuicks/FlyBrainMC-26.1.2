package com.fruitfly.client.hud;

import com.fruitfly.net.BrainTelemetryPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of brain telemetry: the latest {@link BrainTelemetryPayload} per fly plus a rolling history of
 * the last {@value #HISTORY} payloads (spikes per tick and every watched population's rate) for time-series widgets.
 *
 * <p>{@code FruitFlyClient}'s payload receiver calls {@link #accept(BrainTelemetryPayload)}; the HUD and the in-world
 * overlay read through {@link #get(int)} / {@link #latest(int)}. Fabric play-payload handlers run on the client main
 * thread, but the store is thread-safe regardless (ConcurrentHashMap plus per-entry synchronisation) so a receiver
 * on another thread cannot corrupt the ring buffers.</p>
 */
public final class TelemetryStore {
    /** Payloads kept per fly. At {@code telemetryEveryTicks = 2} (the default) this is ~20 s of history, ~10 s at 1. */
    public static final int HISTORY = 200;
    /** An entry not updated for this long is stale (fly unloaded, brain released, player out of range). */
    public static final long STALE_NANOS = 5_000_000_000L;

    private static final ConcurrentHashMap<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static int acceptCounter;

    private TelemetryStore() { }

    /** Record a payload. Safe to call from any thread; null is ignored. */
    public static void accept(BrainTelemetryPayload payload) {
        if (payload == null) return;
        long now = System.nanoTime();
        ENTRIES.computeIfAbsent(payload.entityId(), Entry::new).push(payload, now);
        if ((++acceptCounter & 127) == 0) prune(now);
    }

    /** The entry for a fly, or null when no telemetry has ever been received for it. */
    public static Entry get(int entityId) { return ENTRIES.get(entityId); }

    /** Latest payload for a fly, or null. */
    public static BrainTelemetryPayload latest(int entityId) {
        Entry e = ENTRIES.get(entityId);
        return e == null ? null : e.latest();
    }

    /** True when the fly has telemetry younger than {@link #STALE_NANOS}. */
    public static boolean hasFresh(int entityId) {
        Entry e = ENTRIES.get(entityId);
        return e != null && e.fresh(System.nanoTime());
    }

    /** All entries (fresh and stale) as a snapshot list. */
    public static Collection<Entry> entries() { return new ArrayList<>(ENTRIES.values()); }

    public static void forget(int entityId) { ENTRIES.remove(entityId); }

    /** Drop everything (call on disconnect). */
    public static void clear() { ENTRIES.clear(); }

    /** Remove entries that have been stale for a long time (10x the freshness window). */
    public static void prune(long nowNanos) {
        ENTRIES.values().removeIf(e -> nowNanos - e.lastUpdateNanos() > 10 * STALE_NANOS);
    }

    /** Per-fly latest payload and ring-buffer history. All accessors are synchronised. */
    public static final class Entry {
        public final int entityId;
        private BrainTelemetryPayload latest;
        private long lastUpdateNanos;
        private long seq;
        private final int[] spikes = new int[HISTORY];
        private final int[] active = new int[HISTORY];
        private final Map<String, float[]> rates = new LinkedHashMap<>();
        /** Next write slot. */
        private int head;
        /** Number of valid samples (≤ HISTORY). */
        private int count;

        Entry(int entityId) { this.entityId = entityId; }

        synchronized void push(BrainTelemetryPayload p, long nowNanos) {
            latest = p;
            lastUpdateNanos = nowNanos;
            seq++;
            spikes[head] = p.spikesThisTick();
            active[head] = p.activeNeurons();
            // populations dropped from the watch list read 0 for this slot
            for (float[] series : rates.values()) series[head] = 0f;
            String[] names = p.popNames();
            float[] vals = p.popRates();
            int n = Math.min(names.length, vals.length);
            for (int i = 0; i < n; i++) {
                float[] series = rates.get(names[i]);
                if (series == null) {
                    series = new float[HISTORY];
                    rates.put(names[i], series);
                }
                series[head] = vals[i];
            }
            head = (head + 1) % HISTORY;
            if (count < HISTORY) count++;
        }

        public synchronized BrainTelemetryPayload latest() { return latest; }

        /** Monotonic counter incremented per payload; consumers compare it to detect new data. */
        public synchronized long seq() { return seq; }

        public synchronized long lastUpdateNanos() { return lastUpdateNanos; }

        public synchronized int count() { return count; }

        public synchronized boolean fresh(long nowNanos) {
            return latest != null && nowNanos - lastUpdateNanos < STALE_NANOS;
        }

        /** Age of the latest payload in milliseconds. */
        public synchronized double ageMillis() { return (System.nanoTime() - lastUpdateNanos) / 1e6; }

        /** Population specs seen in this fly's history, in first-seen order. */
        public synchronized List<String> populations() { return new ArrayList<>(rates.keySet()); }

        /**
         * Copy the newest {@code out.length} spikes-per-tick samples into {@code out}, oldest first and right-aligned
         * (leading slots are zero when fewer samples exist). Returns the number of valid samples.
         */
        public synchronized int spikeHistory(int[] out) { return copy(spikes, out); }

        /** Same as {@link #spikeHistory(int[])} for the active-neuron count. */
        public synchronized int activeHistory(int[] out) { return copy(active, out); }

        /** Same as {@link #spikeHistory(int[])} for one population's rate (Hz); unknown population → zeros, 0. */
        public synchronized int rateHistory(String pop, float[] out) {
            float[] series = rates.get(pop);
            if (series == null) {
                Arrays.fill(out, 0f);
                return 0;
            }
            return copy(series, out);
        }

        /** Maximum of the newest {@code window} samples of a population rate (0 when unknown). */
        public synchronized float rateMax(String pop, int window) {
            float[] series = rates.get(pop);
            if (series == null) return 0f;
            int n = Math.min(Math.min(window, count), HISTORY);
            float m = 0f;
            for (int k = 1; k <= n; k++) m = Math.max(m, series[((head - k) % HISTORY + HISTORY) % HISTORY]);
            return m;
        }

        private int copy(int[] ring, int[] out) {
            int n = Math.min(count, out.length);
            Arrays.fill(out, 0, out.length - n, 0);
            for (int k = 0; k < n; k++) out[out.length - n + k] = ring[((head - n + k) % HISTORY + HISTORY) % HISTORY];
            return n;
        }

        private int copy(float[] ring, float[] out) {
            int n = Math.min(count, out.length);
            Arrays.fill(out, 0, out.length - n, 0f);
            for (int k = 0; k < n; k++) out[out.length - n + k] = ring[((head - n + k) % HISTORY + HISTORY) % HISTORY];
            return n;
        }
    }
}
