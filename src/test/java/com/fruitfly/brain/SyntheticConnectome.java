package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds tiny hand-made connectomes for unit tests. */
final class SyntheticConnectome {
    private final List<String> types = new ArrayList<>(List.of(""));
    private final List<long[]> neurons = new ArrayList<>(); // [typeIdx, sign]
    private final List<String> sidesOf = new ArrayList<>();
    private final List<int[]> edges = new ArrayList<>();    // pre, post, weight

    /** Add a neuron; returns its index. sign: +1 excitatory, -1 inhibitory, 0 none. */
    int neuron(String type, int sign, String side) {
        int t = types.indexOf(type);
        if (t < 0) { types.add(type); t = types.size() - 1; }
        neurons.add(new long[]{t, sign});
        sidesOf.add(side);
        return neurons.size() - 1;
    }

    void edge(int pre, int post, int weight) { edges.add(new int[]{pre, post, weight}); }

    Connectome build() {
        int n = neurons.size();
        String[] sides = {"", "L", "R", "M"};
        long[] bodyId = new long[n];
        int[] typeIdx = new int[n];
        byte[] sign = new byte[n], sideIdx = new byte[n], zero = new byte[n], hex = new byte[n];
        Arrays.fill(hex, (byte) -1);
        for (int i = 0; i < n; i++) {
            bodyId[i] = 10000 + i;
            typeIdx[i] = (int) neurons.get(i)[0];
            sign[i] = (byte) neurons.get(i)[1];
            sideIdx[i] = (byte) Math.max(0, Arrays.asList(sides).indexOf(sidesOf.get(i)));
        }
        edges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int[] rowPtr = new int[n + 1];
        for (int[] e : edges) rowPtr[e[0] + 1]++;
        for (int i = 0; i < n; i++) rowPtr[i + 1] += rowPtr[i];
        int[] post = new int[edges.size()];
        short[] w = new short[edges.size()];
        for (int k = 0; k < edges.size(); k++) { post[k] = edges.get(k)[1]; w[k] = (short) edges.get(k)[2]; }
        float[] soma = new float[3 * n];
        Arrays.fill(soma, Float.NaN);
        String[] empty = {""};
        return new Connectome("synthetic", "{}", n, edges.size(),
                types.toArray(new String[0]), empty, empty, empty, new String[]{"", "acetylcholine", "gaba"}, sides,
                empty, empty, empty, empty,
                bodyId, typeIdx, zero, zero, new short[n], zero, sign, sideIdx, hex, hex, zero, zero, zero, zero,
                soma, new int[n], new int[n], rowPtr, post, w,
                new int[0], new byte[0], new byte[0], new byte[0], new byte[0]);
    }
}
