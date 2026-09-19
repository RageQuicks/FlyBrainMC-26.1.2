package com.fruitfly.brain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.zip.GZIPInputStream;

/**
 * In-memory connectome: neuron annotations plus a CSR adjacency (presynaptic -> postsynaptic, synapse counts).
 * Loaded from the FLYB v1 binary written by tools/build_flyb.py. All arrays are indexed by a dense neuron index
 * (0..n-1) sorted by neuPrint bodyId.
 *
 * Retina table: photoreceptors (R1-R6 / R7 / R8) mapped onto medulla column hex coordinates (via their strongest
 * lamina target) together with the lamina monopolar cells L1/L2/L3 of each column, so a rendered image can be
 * projected onto the real retinotopic map of the fly.
 */
public final class Connectome {
    public static final int RETINA_R1R6 = 0, RETINA_R7 = 1, RETINA_R8 = 2, RETINA_L1 = 3, RETINA_L2 = 4, RETINA_L3 = 5;

    public final String dataset;
    public final String metaJson;
    public final int n;
    public final int nEdges;

    public final String[] types, superclasses, classes, subclasses, nts, sides, dimorphisms, fruDsx, neuromeres, nerves;

    public final long[] bodyId;
    public final int[] typeIdx;
    public final byte[] superclassIdx, classIdx;
    public final short[] subclassIdx;
    public final byte[] ntIdx, ntSign, sideIdx, hex1, hex2, dimIdx, fruDsxIdx, neuromereIdx, nerveIdx;
    /** soma x,y,z in raw neuPrint voxel coordinates, NaN when unknown; length 3n. */
    public final float[] soma;
    public final int[] preSyn, postSyn;

    /** CSR adjacency by presynaptic index. weight is an unsigned 16-bit synapse count (use weight[k] & 0xFFFF). */
    public final int[] rowPtr, postIdx;
    public final short[] weight;

    public final int[] retinaNeuron;
    public final byte[] retinaSide, retinaHex1, retinaHex2, retinaKind;

    private Map<String, int[]> typeIndexCache;
    private Map<Long, Integer> bodyIdIndex;

    Connectome(String dataset, String metaJson, int n, int nEdges,
               String[] types, String[] superclasses, String[] classes, String[] subclasses, String[] nts,
               String[] sides, String[] dimorphisms, String[] fruDsx, String[] neuromeres, String[] nerves,
               long[] bodyId, int[] typeIdx, byte[] superclassIdx, byte[] classIdx, short[] subclassIdx,
               byte[] ntIdx, byte[] ntSign, byte[] sideIdx, byte[] hex1, byte[] hex2, byte[] dimIdx, byte[] fruDsxIdx,
               byte[] neuromereIdx, byte[] nerveIdx, float[] soma, int[] preSyn, int[] postSyn,
               int[] rowPtr, int[] postIdx, short[] weight,
               int[] retinaNeuron, byte[] retinaSide, byte[] retinaHex1, byte[] retinaHex2, byte[] retinaKind) {
        this.dataset = dataset;
        this.metaJson = metaJson;
        this.n = n;
        this.nEdges = nEdges;
        this.types = types;
        this.superclasses = superclasses;
        this.classes = classes;
        this.subclasses = subclasses;
        this.nts = nts;
        this.sides = sides;
        this.dimorphisms = dimorphisms;
        this.fruDsx = fruDsx;
        this.neuromeres = neuromeres;
        this.nerves = nerves;
        this.bodyId = bodyId;
        this.typeIdx = typeIdx;
        this.superclassIdx = superclassIdx;
        this.classIdx = classIdx;
        this.subclassIdx = subclassIdx;
        this.ntIdx = ntIdx;
        this.ntSign = ntSign;
        this.sideIdx = sideIdx;
        this.hex1 = hex1;
        this.hex2 = hex2;
        this.dimIdx = dimIdx;
        this.fruDsxIdx = fruDsxIdx;
        this.neuromereIdx = neuromereIdx;
        this.nerveIdx = nerveIdx;
        this.soma = soma;
        this.preSyn = preSyn;
        this.postSyn = postSyn;
        this.rowPtr = rowPtr;
        this.postIdx = postIdx;
        this.weight = weight;
        this.retinaNeuron = retinaNeuron;
        this.retinaSide = retinaSide;
        this.retinaHex1 = retinaHex1;
        this.retinaHex2 = retinaHex2;
        this.retinaKind = retinaKind;
    }

    // ------------------------------------------------------------------ loading

    /** Load a gzip-compressed FLYB stream. */
    public static Connectome load(InputStream gzipped) throws IOException {
        byte[] all;
        try (GZIPInputStream gz = new GZIPInputStream(gzipped, 1 << 16)) {
            all = gz.readAllBytes();
        }
        return parse(ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN));
    }

    /** Parse an uncompressed FLYB buffer. */
    public static Connectome parse(ByteBuffer b) throws IOException {
        b.order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        b.get(magic);
        if (magic[0] != 'F' || magic[1] != 'L' || magic[2] != 'Y' || magic[3] != 'B') {
            throw new IOException("Not a FLYB file (bad magic)");
        }
        int version = b.getInt();
        if (version != 1) throw new IOException("Unsupported FLYB version " + version);
        int n = b.getInt();
        int nEdges = b.getInt();
        int nRetina = b.getInt();
        String dataset = str16(b);
        String meta = str32(b);
        String[] types = table(b), superclasses = table(b), classes = table(b), subclasses = table(b), nts = table(b),
                sides = table(b), dimorphisms = table(b), fruDsx = table(b), neuromeres = table(b), nerves = table(b);

        long[] bodyId = new long[n];
        b.asLongBuffer().get(bodyId);
        b.position(b.position() + 8 * n);
        int[] typeIdx = ints(b, n);
        byte[] superclassIdx = bytes(b, n), classIdx = bytes(b, n);
        short[] subclassIdx = shorts(b, n);
        byte[] ntIdx = bytes(b, n), ntSign = bytes(b, n), sideIdx = bytes(b, n), hex1 = bytes(b, n), hex2 = bytes(b, n),
                dimIdx = bytes(b, n), fruDsxIdx = bytes(b, n), neuromereIdx = bytes(b, n), nerveIdx = bytes(b, n);
        float[] soma = new float[3 * n];
        b.asFloatBuffer().get(soma);
        b.position(b.position() + 12 * n);
        int[] preSyn = ints(b, n), postSyn = ints(b, n);
        int[] rowPtr = ints(b, n + 1);
        int[] postIdx = ints(b, nEdges);
        short[] weight = shorts(b, nEdges);
        if (rowPtr[n] != nEdges) throw new IOException("Corrupt FLYB: rowPtr[n]=" + rowPtr[n] + " != nEdges=" + nEdges);

        int[] retinaNeuron = new int[nRetina];
        byte[] retinaSide = new byte[nRetina], retinaHex1 = new byte[nRetina], retinaHex2 = new byte[nRetina], retinaKind = new byte[nRetina];
        for (int i = 0; i < nRetina; i++) {
            retinaNeuron[i] = b.getInt();
            retinaSide[i] = b.get();
            retinaHex1[i] = b.get();
            retinaHex2[i] = b.get();
            retinaKind[i] = b.get();
        }
        if (b.hasRemaining()) throw new IOException("Corrupt FLYB: " + b.remaining() + " trailing bytes");
        return new Connectome(dataset, meta, n, nEdges, types, superclasses, classes, subclasses, nts, sides, dimorphisms,
                fruDsx, neuromeres, nerves, bodyId, typeIdx, superclassIdx, classIdx, subclassIdx, ntIdx, ntSign, sideIdx,
                hex1, hex2, dimIdx, fruDsxIdx, neuromereIdx, nerveIdx, soma, preSyn, postSyn, rowPtr, postIdx, weight,
                retinaNeuron, retinaSide, retinaHex1, retinaHex2, retinaKind);
    }

    private static int[] ints(ByteBuffer b, int count) {
        int[] a = new int[count];
        b.asIntBuffer().get(a);
        b.position(b.position() + 4 * count);
        return a;
    }

    private static short[] shorts(ByteBuffer b, int count) {
        short[] a = new short[count];
        b.asShortBuffer().get(a);
        b.position(b.position() + 2 * count);
        return a;
    }

    private static byte[] bytes(ByteBuffer b, int count) {
        byte[] a = new byte[count];
        b.get(a);
        return a;
    }

    private static String str16(ByteBuffer b) {
        int len = b.getShort() & 0xFFFF;
        byte[] s = new byte[len];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    private static String str32(ByteBuffer b) {
        int len = b.getInt();
        byte[] s = new byte[len];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    private static String[] table(ByteBuffer b) {
        int count = b.getShort() & 0xFFFF;
        String[] t = new String[count];
        for (int i = 0; i < count; i++) t[i] = str16(b);
        return t;
    }

    // ------------------------------------------------------------------ accessors

    public String type(int i) { return types[typeIdx[i]]; }
    public String superclass(int i) { return superclasses[superclassIdx[i] & 0xFF]; }
    public String neuronClass(int i) { return classes[classIdx[i] & 0xFF]; }
    public String subclass(int i) { return subclasses[subclassIdx[i] & 0xFFFF]; }
    public String nt(int i) { return nts[ntIdx[i] & 0xFF]; }
    public String side(int i) { return sides[sideIdx[i] & 0xFF]; }
    public String dimorphism(int i) { return dimorphisms[dimIdx[i] & 0xFF]; }
    public String fruDsxOf(int i) { return fruDsx[fruDsxIdx[i] & 0xFF]; }
    public String neuromere(int i) { return neuromeres[neuromereIdx[i] & 0xFF]; }
    public String entryNerve(int i) { return nerves[nerveIdx[i] & 0xFF]; }
    public boolean hasSoma(int i) { return !Float.isNaN(soma[3 * i]); }
    public int outDegree(int i) { return rowPtr[i + 1] - rowPtr[i]; }
    public int synapseCount(int edge) { return weight[edge] & 0xFFFF; }

    /** Total synapses (kept edges) out of neuron i. */
    public long outSynapses(int i) {
        long s = 0;
        for (int k = rowPtr[i]; k < rowPtr[i + 1]; k++) s += weight[k] & 0xFFFF;
        return s;
    }

    public synchronized int indexOfBodyId(long id) {
        if (bodyIdIndex == null) {
            Map<Long, Integer> m = new HashMap<>(n * 2);
            for (int i = 0; i < n; i++) m.put(bodyId[i], i);
            bodyIdIndex = m;
        }
        Integer i = bodyIdIndex.get(id);
        return i == null ? -1 : i;
    }

    /** All neuron indices with exactly this type (cached). */
    public synchronized int[] indicesOfType(String type) {
        if (typeIndexCache == null) {
            Map<String, List<Integer>> tmp = new HashMap<>();
            for (int i = 0; i < n; i++) {
                tmp.computeIfAbsent(types[typeIdx[i]], k -> new ArrayList<>()).add(i);
            }
            typeIndexCache = new HashMap<>(tmp.size() * 2);
            for (Map.Entry<String, List<Integer>> e : tmp.entrySet()) {
                typeIndexCache.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
            }
        }
        int[] r = typeIndexCache.get(type);
        return r == null ? new int[0] : r;
    }

    public int[] indicesWhere(IntPredicate p) {
        int[] buf = new int[64];
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (p.test(i)) {
                if (c == buf.length) buf = Arrays.copyOf(buf, c * 2);
                buf[c++] = i;
            }
        }
        return Arrays.copyOf(buf, c);
    }

    public int[] indicesOfSuperclass(String sc) { return indicesWhere(i -> superclass(i).equals(sc)); }
    public int[] indicesOfClass(String cl) { return indicesWhere(i -> neuronClass(i).equals(cl)); }
    public int[] indicesOfTypePrefix(String prefix) { return indicesWhere(i -> type(i).startsWith(prefix)); }

    /** Distinct type names, sorted. */
    public String[] typeNames() {
        String[] t = types.clone();
        Arrays.sort(t);
        return t;
    }

    @Override
    public String toString() {
        return "Connectome[" + dataset + ", " + n + " neurons, " + nEdges + " edges, " + retinaNeuron.length + " retina entries]";
    }
}
