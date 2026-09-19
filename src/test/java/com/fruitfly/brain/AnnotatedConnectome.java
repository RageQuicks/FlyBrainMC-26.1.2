package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test helper: like {@link SyntheticConnectome} but with class / subclass / superclass / neurotransmitter / nerve
 * annotations and a retina table, so the class-based population specs used by {@link SensoryEncoders} and the
 * column lattice used by {@link RetinaGeometry} can be exercised without the 23 MB FLYB.
 *
 * <p>Neuron indices are dense in insertion order; bodyId = 10000 + index. The side table is fixed to
 * {@code "", "L", "R", "M"} (same as {@link SyntheticConnectome}); string tables intern "" at index 0 to match the
 * FLYB convention that index 0 means "unannotated".</p>
 */
final class AnnotatedConnectome {
    private static final String[] SIDES = {"", "L", "R", "M"};

    private static final class Neuron {
        String type = "", side = "", cls = "", subclass = "", superclass = "", nt = "", nerve = "";
        int sign;
        int hex1 = -1, hex2 = -1;
    }

    private static final class Table {
        final List<String> values = new ArrayList<>(List.of(""));

        int index(String s) {
            if (s == null || s.isEmpty()) return 0;
            int i = values.indexOf(s);
            if (i < 0) {
                values.add(s);
                i = values.size() - 1;
            }
            return i;
        }

        String[] toArray() { return values.toArray(new String[0]); }
    }

    private final List<Neuron> neurons = new ArrayList<>();
    private final List<int[]> edges = new ArrayList<>();     // pre, post, weight
    private final List<int[]> retina = new ArrayList<>();    // neuron, sideIdx, hex1, hex2, kind

    /** Add an unclassified neuron; returns its index. sign: +1 excitatory, -1 inhibitory, 0 none. */
    int neuron(String type, int sign, String side) { return neuron(type, sign, side, "", ""); }

    /** Add a neuron with class and subclass annotations (e.g. "olfactory", "" or "mechanosensory", "auditory"). */
    int neuron(String type, int sign, String side, String cls, String subclass) {
        Neuron n = new Neuron();
        n.type = type;
        n.sign = sign;
        n.side = side == null ? "" : side;
        n.cls = cls == null ? "" : cls;
        n.subclass = subclass == null ? "" : subclass;
        neurons.add(n);
        return neurons.size() - 1;
    }

    /** Add {@code count} identical neurons; returns their indices. */
    int[] neurons(int count, String type, int sign, String side, String cls, String subclass) {
        int[] ids = new int[count];
        for (int k = 0; k < count; k++) ids[k] = neuron(type, sign, side, cls, subclass);
        return ids;
    }

    AnnotatedConnectome superclass(int i, String sc) { neurons.get(i).superclass = sc; return this; }
    AnnotatedConnectome nt(int i, String nt) { neurons.get(i).nt = nt; return this; }
    AnnotatedConnectome nerve(int i, String nerve) { neurons.get(i).nerve = nerve; return this; }
    AnnotatedConnectome hex(int i, int hex1, int hex2) { neurons.get(i).hex1 = hex1; neurons.get(i).hex2 = hex2; return this; }

    void edge(int pre, int post, int weight) { edges.add(new int[]{pre, post, weight}); }

    /** Add a retina-table entry (kind = one of {@code Connectome.RETINA_*}). Unknown sides map to index 0 (""). */
    void retina(int neuron, String side, int hex1, int hex2, int kind) {
        int s = Arrays.asList(SIDES).indexOf(side);
        retina.add(new int[]{neuron, Math.max(0, s), hex1, hex2, kind});
    }

    int size() { return neurons.size(); }

    Connectome build() {
        int n = neurons.size();
        Table types = new Table(), superclasses = new Table(), classes = new Table(), subclasses = new Table(),
                nts = new Table(), nerves = new Table();
        long[] bodyId = new long[n];
        int[] typeIdx = new int[n];
        byte[] superclassIdx = new byte[n], classIdx = new byte[n], ntIdx = new byte[n], sign = new byte[n],
                sideIdx = new byte[n], hex1 = new byte[n], hex2 = new byte[n], nerveIdx = new byte[n], zero = new byte[n];
        short[] subclassIdx = new short[n];
        for (int i = 0; i < n; i++) {
            Neuron nn = neurons.get(i);
            bodyId[i] = 10000 + i;
            typeIdx[i] = types.index(nn.type);
            superclassIdx[i] = (byte) superclasses.index(nn.superclass);
            classIdx[i] = (byte) classes.index(nn.cls);
            subclassIdx[i] = (short) subclasses.index(nn.subclass);
            ntIdx[i] = (byte) nts.index(nn.nt);
            nerveIdx[i] = (byte) nerves.index(nn.nerve);
            sign[i] = (byte) nn.sign;
            sideIdx[i] = (byte) Math.max(0, Arrays.asList(SIDES).indexOf(nn.side));
            hex1[i] = (byte) nn.hex1;
            hex2[i] = (byte) nn.hex2;
        }
        edges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int[] rowPtr = new int[n + 1];
        for (int[] e : edges) rowPtr[e[0] + 1]++;
        for (int i = 0; i < n; i++) rowPtr[i + 1] += rowPtr[i];
        int[] post = new int[edges.size()];
        short[] w = new short[edges.size()];
        for (int k = 0; k < edges.size(); k++) {
            post[k] = edges.get(k)[1];
            w[k] = (short) edges.get(k)[2];
        }
        float[] soma = new float[3 * n];
        Arrays.fill(soma, Float.NaN);
        int nr = retina.size();
        int[] retinaNeuron = new int[nr];
        byte[] retinaSide = new byte[nr], retinaHex1 = new byte[nr], retinaHex2 = new byte[nr], retinaKind = new byte[nr];
        for (int k = 0; k < nr; k++) {
            int[] e = retina.get(k);
            retinaNeuron[k] = e[0];
            retinaSide[k] = (byte) e[1];
            retinaHex1[k] = (byte) e[2];
            retinaHex2[k] = (byte) e[3];
            retinaKind[k] = (byte) e[4];
        }
        String[] empty = {""};
        return new Connectome("annotated-synthetic", "{}", n, edges.size(),
                types.toArray(), superclasses.toArray(), classes.toArray(), subclasses.toArray(), nts.toArray(),
                SIDES.clone(), empty, empty, empty, nerves.toArray(),
                bodyId, typeIdx, superclassIdx, classIdx, subclassIdx, ntIdx, sign, sideIdx, hex1, hex2,
                zero, zero, zero, nerveIdx,
                soma, new int[n], new int[n], rowPtr, post, w,
                retinaNeuron, retinaSide, retinaHex1, retinaHex2, retinaKind);
    }
}
