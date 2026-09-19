package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retinotopic geometry of the two compound eyes, derived from the connectome's medulla column coordinates.
 *
 * <p>neuPrint's {@code assignedOlHex1/assignedOlHex2} are the two oblique axes (p, q) of the hexagonal column lattice.
 * The dorsoventral (vertical) axis of visual space is the diagonal {@code u = hex1 + hex2} (dorsal = high u; dorsal
 * rim area columns sit at u ≈ 46..72) and the anteroposterior (horizontal) axis is {@code w = hex1 − hex2}
 * (measured ranges u 7..72, w −19..16). A linear map from (u, w) to (elevation, azimuth) is used; it is exact only
 * near the eye centre (±20–30 % at the periphery) and its orientation is configurable via {@link Params}.</p>
 *
 * <p>Head coordinates: x forward, y up, z to the fly's right. Azimuth positive = right; elevation positive = up.
 * The left eye is the mirror image of the right eye.</p>
 */
public final class RetinaGeometry {
    public static final int LEFT = 0, RIGHT = 1;

    public static final class Params {
        public int uMin = 7, uMax = 72, wMin = -19, wMax = 16;
        /** Elevation of the ventral-most (low u) and dorsal-most (high u) columns, degrees. */
        public double elevationMinDeg = -70, elevationMaxDeg = 80;
        /** Azimuth (right eye) of the low-w and high-w extremes, degrees (negative = across the midline). */
        public double azimuthMinDeg = -10, azimuthMaxDeg = 155;
        /**
         * Horizontal axis of the linear fallback map. In the male-cns hex grid +h = hex1 − hex2 points ANTERIOR (four
         * independent lines of evidence, docs/research/gaps/gap-4.md), so the default is flipped relative to the naive
         * "low w = front" guess.
         */
        public boolean flipHorizontal = true;
        /** Flip the vertical axis of the fallback map (dorsal = high hex1 + hex2 is correct as is). */
        public boolean flipVertical = false;
        /** Number of coarse ray directions per eye the game may use to sample the world (columns are grouped). */
        public int raysPerEye = 300;
        /**
         * Use the bundled measured eye map (assets/fruitfly/brain/column_directions.csv: per-column viewing directions
         * derived from the optic-lobe column pins matched to the µCT ommatidium map of Zhao et al. 2025; see
         * docs/research/gaps/gap-4.md). Columns missing from the file fall back to the linear map.
         */
        public boolean useMeasuredEyeMap = true;
    }

    public static final String EYE_MAP_RESOURCE = "/assets/fruitfly/brain/column_directions.csv";

    /** One medulla column = one ommatidium-equivalent. */
    public static final class Column {
        public final int index, side, hex1, hex2, u, w;
        public final float azimuthDeg, elevationDeg;
        /** Unit view direction in head coordinates. */
        public final float dx, dy, dz;
        /** Photoreceptor neuron indices feeding this column (R1-R6, R7, R8); may be empty. */
        public int[] photoreceptors = new int[0];
        /** Subtype-specific photoreceptors: broadband R1-R6, UV-sensing R7, blue/green-sensing R8. */
        public int[] r1r6 = new int[0];
        public int[] r7 = new int[0];
        public int[] r8 = new int[0];
        /** Lamina monopolar cells of this column (−1 if absent). */
        public int l1 = -1, l2 = -1, l3 = -1;
        /** Coarse ray group this column belongs to (0..raysPerEye−1). */
        public int ray;

        Column(int index, int side, int hex1, int hex2, float az, float el) {
            this.index = index;
            this.side = side;
            this.hex1 = hex1;
            this.hex2 = hex2;
            this.u = hex1 + hex2;
            this.w = hex1 - hex2;
            this.azimuthDeg = az;
            this.elevationDeg = el;
            double a = Math.toRadians(az), e = Math.toRadians(el);
            this.dx = (float) (Math.cos(e) * Math.cos(a));
            this.dy = (float) Math.sin(e);
            this.dz = (float) (Math.cos(e) * Math.sin(a));
        }
    }

    /** A coarse sampling ray: mean direction of its member columns. */
    public static final class Ray {
        public final int side, index;
        public float dx, dy, dz, azimuthDeg, elevationDeg;
        public int[] columns = new int[0];

        Ray(int side, int index) { this.side = side; this.index = index; }
    }

    public final Params params;
    private final List<Column> columns = new ArrayList<>();
    private final Map<Integer, Integer> keyToIndex = new HashMap<>();
    private final List<Ray>[] rays;
    private final int[] countBySide = new int[2];
    private final Map<Integer, float[]> measured; // key -> {azimuthDeg (right positive), elevationDeg}
    private int measuredColumns;

    @SuppressWarnings("unchecked")
    public RetinaGeometry(Connectome c, Params p) {
        this.params = p;
        this.measured = p.useMeasuredEyeMap ? loadMeasuredEyeMap() : null;
        // enumerate distinct columns from the retina table
        for (int k = 0; k < c.retinaNeuron.length; k++) {
            int side = sideOf(c.sides[c.retinaSide[k] & 0xFF]);
            if (side < 0) continue;
            int h1 = c.retinaHex1[k], h2 = c.retinaHex2[k];
            if (h1 <= 0 || h2 <= 0) continue;
            int key = key(side, h1, h2);
            Integer idx = keyToIndex.get(key);
            Column col;
            if (idx == null) {
                col = makeColumn(columns.size(), side, h1, h2);
                columns.add(col);
                keyToIndex.put(key, col.index);
                countBySide[side]++;
            } else {
                col = columns.get(idx);
            }
            int neuron = c.retinaNeuron[k];
            switch (c.retinaKind[k]) {
                case Connectome.RETINA_R1R6 -> {
                    col.photoreceptors = Arrays.copyOf(col.photoreceptors, col.photoreceptors.length + 1);
                    col.photoreceptors[col.photoreceptors.length - 1] = neuron;
                    col.r1r6 = Arrays.copyOf(col.r1r6, col.r1r6.length + 1);
                    col.r1r6[col.r1r6.length - 1] = neuron;
                }
                case Connectome.RETINA_R7 -> {
                    col.photoreceptors = Arrays.copyOf(col.photoreceptors, col.photoreceptors.length + 1);
                    col.photoreceptors[col.photoreceptors.length - 1] = neuron;
                    col.r7 = Arrays.copyOf(col.r7, col.r7.length + 1);
                    col.r7[col.r7.length - 1] = neuron;
                }
                case Connectome.RETINA_R8 -> {
                    col.photoreceptors = Arrays.copyOf(col.photoreceptors, col.photoreceptors.length + 1);
                    col.photoreceptors[col.photoreceptors.length - 1] = neuron;
                    col.r8 = Arrays.copyOf(col.r8, col.r8.length + 1);
                    col.r8[col.r8.length - 1] = neuron;
                }
                case Connectome.RETINA_L1 -> col.l1 = neuron;
                case Connectome.RETINA_L2 -> col.l2 = neuron;
                case Connectome.RETINA_L3 -> col.l3 = neuron;
                default -> { }
            }
        }
        rays = new List[]{new ArrayList<Ray>(), new ArrayList<Ray>()};
        buildRays();
    }

    public RetinaGeometry(Connectome c) { this(c, new Params()); }

    /**
     * Parse the bundled eye map: side,hex1,hex2,az_deg_ipsi,el_deg,... (azimuth positive toward the ipsilateral side).
     * Returns null when the resource is absent.
     */
    static Map<Integer, float[]> loadMeasuredEyeMap() {
        try (java.io.InputStream in = RetinaGeometry.class.getResourceAsStream(EYE_MAP_RESOURCE)) {
            if (in == null) return null;
            Map<Integer, float[]> m = new HashMap<>(2048);
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String header = r.readLine();
            if (header == null) return null;
            String[] cols = header.split(",");
            int iSide = -1, iH1 = -1, iH2 = -1, iAz = -1, iEl = -1;
            for (int i = 0; i < cols.length; i++) {
                switch (cols[i].trim()) {
                    case "side" -> iSide = i;
                    case "hex1" -> iH1 = i;
                    case "hex2" -> iH2 = i;
                    case "az_deg_ipsi" -> iAz = i;
                    case "el_deg" -> iEl = i;
                    default -> { }
                }
            }
            if (iSide < 0 || iH1 < 0 || iH2 < 0 || iAz < 0 || iEl < 0) return null;
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length <= Math.max(iAz, iEl)) continue;
                int side = sideOf(f[iSide].trim());
                if (side < 0) continue;
                int h1 = Integer.parseInt(f[iH1].trim()), h2 = Integer.parseInt(f[iH2].trim());
                float azIpsi = Float.parseFloat(f[iAz].trim()), el = Float.parseFloat(f[iEl].trim());
                float azRight = side == RIGHT ? azIpsi : -azIpsi;
                m.put(key(side, h1, h2), new float[]{azRight, el});
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** Number of columns whose direction came from the measured eye map (0 = linear fallback everywhere). */
    public int measuredColumnCount() { return measuredColumns; }

    private Column makeColumn(int index, int side, int h1, int h2) {
        if (measured != null) {
            float[] m = measured.get(key(side, h1, h2));
            if (m != null) {
                measuredColumns++;
                return new Column(index, side, h1, h2, m[0], m[1]);
            }
        }
        int u = h1 + h2, w = h1 - h2;
        double uN = (u - params.uMin) / (double) (params.uMax - params.uMin);
        double wN = (w - params.wMin) / (double) (params.wMax - params.wMin);
        uN = Math.max(0, Math.min(1, uN));
        wN = Math.max(0, Math.min(1, wN));
        if (params.flipVertical) uN = 1 - uN;
        if (params.flipHorizontal) wN = 1 - wN;
        double el = params.elevationMinDeg + uN * (params.elevationMaxDeg - params.elevationMinDeg);
        double az = params.azimuthMinDeg + wN * (params.azimuthMaxDeg - params.azimuthMinDeg);
        if (side == LEFT) az = -az;
        return new Column(index, side, h1, h2, (float) az, (float) el);
    }

    /** Group columns of each eye into {@code raysPerEye} clusters on a regular az/el grid, then average directions. */
    private void buildRays() {
        for (int side = 0; side < 2; side++) {
            List<Column> cols = columns(side);
            if (cols.isEmpty()) continue;
            int n = Math.max(1, params.raysPerEye);
            int gridW = (int) Math.ceil(Math.sqrt(n * 1.4));
            int gridH = (int) Math.ceil(n / (double) gridW);
            float azMin = Float.MAX_VALUE, azMax = -Float.MAX_VALUE, elMin = Float.MAX_VALUE, elMax = -Float.MAX_VALUE;
            for (Column col : cols) {
                azMin = Math.min(azMin, col.azimuthDeg); azMax = Math.max(azMax, col.azimuthDeg);
                elMin = Math.min(elMin, col.elevationDeg); elMax = Math.max(elMax, col.elevationDeg);
            }
            Map<Integer, Ray> cells = new HashMap<>();
            Map<Integer, List<Integer>> members = new HashMap<>();
            for (Column col : cols) {
                int gx = (int) Math.min(gridW - 1, (col.azimuthDeg - azMin) / Math.max(1e-3f, azMax - azMin) * gridW);
                int gy = (int) Math.min(gridH - 1, (col.elevationDeg - elMin) / Math.max(1e-3f, elMax - elMin) * gridH);
                int cell = gy * gridW + gx;
                members.computeIfAbsent(cell, k -> new ArrayList<>()).add(col.index);
            }
            List<Ray> list = rays[side];
            for (Map.Entry<Integer, List<Integer>> e : members.entrySet()) {
                Ray r = new Ray(side, list.size());
                double sx = 0, sy = 0, sz = 0;
                r.columns = e.getValue().stream().mapToInt(Integer::intValue).toArray();
                for (int ci : r.columns) {
                    Column col = columns.get(ci);
                    col.ray = r.index;
                    sx += col.dx; sy += col.dy; sz += col.dz;
                }
                double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
                r.dx = (float) (sx / len); r.dy = (float) (sy / len); r.dz = (float) (sz / len);
                r.elevationDeg = (float) Math.toDegrees(Math.asin(r.dy));
                r.azimuthDeg = (float) Math.toDegrees(Math.atan2(r.dz, r.dx));
                list.add(r);
            }
        }
    }

    private static int key(int side, int h1, int h2) { return (side << 16) | (h1 << 8) | h2; }

    public static int sideOf(String s) {
        if ("L".equals(s)) return LEFT;
        if ("R".equals(s)) return RIGHT;
        return -1;
    }

    public int columnCount() { return columns.size(); }
    public int columnCount(int side) { return countBySide[side]; }
    public Column column(int index) { return columns.get(index); }
    public List<Column> allColumns() { return columns; }

    public List<Column> columns(int side) {
        List<Column> r = new ArrayList<>(countBySide[side]);
        for (Column c : columns) if (c.side == side) r.add(c);
        return r;
    }

    public int columnIndex(int side, int hex1, int hex2) {
        Integer i = keyToIndex.get(key(side, hex1, hex2));
        return i == null ? -1 : i;
    }

    public List<Ray> rays(int side) { return rays[side]; }

    /** Column whose view direction is closest to the given head-frame direction (brute force; ~1.8k columns). */
    public Column nearestColumn(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return null;
        dx /= len; dy /= len; dz /= len;
        Column best = null;
        double bestDot = -2;
        for (Column c : columns) {
            double d = c.dx * dx + c.dy * dy + c.dz * dz;
            if (d > bestDot) { bestDot = d; best = c; }
        }
        return best;
    }

    /** Columns whose direction lies within {@code radiusDeg} of the given azimuth/elevation. */
    public int[] columnsWithin(double azimuthDeg, double elevationDeg, double radiusDeg) {
        double a = Math.toRadians(azimuthDeg), e = Math.toRadians(elevationDeg);
        double dx = Math.cos(e) * Math.cos(a), dy = Math.sin(e), dz = Math.cos(e) * Math.sin(a);
        double cosR = Math.cos(Math.toRadians(radiusDeg));
        int[] buf = new int[64];
        int n = 0;
        for (Column c : columns) {
            if (c.dx * dx + c.dy * dy + c.dz * dz >= cosR) {
                if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                buf[n++] = c.index;
            }
        }
        return Arrays.copyOf(buf, n);
    }

    @Override
    public String toString() {
        return "RetinaGeometry[" + countBySide[LEFT] + " L + " + countBySide[RIGHT] + " R columns (" + measuredColumns
                + " from the measured eye map), rays " + rays[LEFT].size() + "/" + rays[RIGHT].size() + "]";
    }
}
