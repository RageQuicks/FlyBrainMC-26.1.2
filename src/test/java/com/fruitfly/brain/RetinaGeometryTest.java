package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.fruitfly.brain.RetinaGeometry.LEFT;
import static com.fruitfly.brain.RetinaGeometry.RIGHT;
import static org.junit.jupiter.api.Assertions.*;

class RetinaGeometryTest {

    /**
     * Column keys {hex1, hex2} of the synthetic eyes. All lie inside the default (u, w) ranges (u 7..72, w -19..16)
     * so the linear map is not clamped: u = 20, 60, 30, 30 and w = 0, 0, 10, -6.
     */
    private static final int[][] KEYS = {{10, 10}, {30, 30}, {20, 10}, {12, 18}};

    /** Two mirrored eyes with four columns each; every column has two R1-R6 cells, one R7 and L1/L2/L3. */
    private static Connectome eyes() {
        AnnotatedConnectome s = new AnnotatedConnectome();
        for (String side : new String[]{"L", "R"}) {
            for (int[] k : KEYS) {
                for (int r = 0; r < 2; r++) s.retina(s.neuron("R1-R6", -1, side), side, k[0], k[1], Connectome.RETINA_R1R6);
                s.retina(s.neuron("R7", -1, side), side, k[0], k[1], Connectome.RETINA_R7);
                s.retina(s.neuron("L1", -1, side), side, k[0], k[1], Connectome.RETINA_L1);
                s.retina(s.neuron("L2", -1, side), side, k[0], k[1], Connectome.RETINA_L2);
                s.retina(s.neuron("L3", -1, side), side, k[0], k[1], Connectome.RETINA_L3);
            }
        }
        // entries the geometry must ignore: unknown side, non-positive hex coordinates
        s.retina(s.neuron("R1-R6", -1, "M"), "M", 10, 10, Connectome.RETINA_R1R6);
        s.retina(s.neuron("R1-R6", -1, "R"), "R", 0, 10, Connectome.RETINA_R1R6);
        s.retina(s.neuron("L1", -1, "R"), "R", 10, -1, Connectome.RETINA_L1);
        return s.build();
    }

    /** Linear fallback map with the pre-gap-4 axis orientation, so the mapping arithmetic can be tested in isolation. */
    private static RetinaGeometry.Params linearParams() {
        RetinaGeometry.Params p = new RetinaGeometry.Params();
        p.useMeasuredEyeMap = false;
        p.flipHorizontal = false;
        return p;
    }

    private static double expectedElevation(RetinaGeometry.Params p, int u) {
        double uN = Math.max(0, Math.min(1, (u - p.uMin) / (double) (p.uMax - p.uMin)));
        return p.elevationMinDeg + uN * (p.elevationMaxDeg - p.elevationMinDeg);
    }

    private static double expectedAzimuth(RetinaGeometry.Params p, int w) {
        double wN = Math.max(0, Math.min(1, (w - p.wMin) / (double) (p.wMax - p.wMin)));
        return p.azimuthMinDeg + wN * (p.azimuthMaxDeg - p.azimuthMinDeg);
    }

    private static double angleDeg(RetinaGeometry.Column a, RetinaGeometry.Column b) {
        double dot = a.dx * b.dx + a.dy * b.dy + a.dz * b.dz;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
    }

    private static RetinaGeometry.Column col(RetinaGeometry g, int side, int h1, int h2) {
        int i = g.columnIndex(side, h1, h2);
        assertTrue(i >= 0, "column " + h1 + "," + h2 + " on side " + side + " missing");
        return g.column(i);
    }

    @Test
    void dorsalColumnsMapToHigherElevationAndPosteriorColumnsToLargerAzimuth() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        RetinaGeometry.Params p = g.params;
        assertEquals(8, g.columnCount());
        assertEquals(4, g.columnCount(LEFT));
        assertEquals(4, g.columnCount(RIGHT));

        RetinaGeometry.Column ventral = col(g, RIGHT, 10, 10);
        RetinaGeometry.Column dorsal = col(g, RIGHT, 30, 30);
        RetinaGeometry.Column posterior = col(g, RIGHT, 20, 10);
        RetinaGeometry.Column anterior = col(g, RIGHT, 12, 18);

        assertEquals(20, ventral.u);
        assertEquals(0, ventral.w);
        assertEquals(60, dorsal.u);
        assertEquals(10, posterior.w);
        assertEquals(-6, anterior.w);

        // dorsal = high hex1 + hex2 -> higher elevation
        assertTrue(dorsal.elevationDeg > ventral.elevationDeg + 60, "dorsal " + dorsal.elevationDeg + " vs ventral " + ventral.elevationDeg);
        assertEquals(expectedElevation(p, 20), ventral.elevationDeg, 1e-3);
        assertEquals(expectedElevation(p, 60), dorsal.elevationDeg, 1e-3);
        assertTrue(dorsal.dy > ventral.dy);

        // w = hex1 - hex2: higher w = more posterior = larger azimuth on the right eye
        assertTrue(posterior.azimuthDeg > ventral.azimuthDeg && ventral.azimuthDeg > anterior.azimuthDeg);
        assertEquals(expectedAzimuth(p, 0), ventral.azimuthDeg, 1e-3);
        assertEquals(expectedAzimuth(p, 10), posterior.azimuthDeg, 1e-3);
        assertEquals(expectedAzimuth(p, -6), anterior.azimuthDeg, 1e-3);
        // posterior (20,10) and anterior (12,18) share u = 30: same elevation, different azimuth
        assertEquals(30, posterior.u);
        assertEquals(30, anterior.u);
        assertEquals(posterior.elevationDeg, anterior.elevationDeg, 1e-6, "same u -> same elevation");
        assertEquals(expectedElevation(p, 30), anterior.elevationDeg, 1e-3);
        assertTrue(anterior.elevationDeg > ventral.elevationDeg, "u 30 sits above u 20");
    }

    @Test
    void leftEyeMirrorsRightEyeAcrossTheMidline() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        for (int[] k : KEYS) {
            RetinaGeometry.Column r = col(g, RIGHT, k[0], k[1]);
            RetinaGeometry.Column l = col(g, LEFT, k[0], k[1]);
            assertEquals(RIGHT, r.side);
            assertEquals(LEFT, l.side);
            assertEquals(r.elevationDeg, l.elevationDeg, 1e-6);
            assertEquals(-r.azimuthDeg, l.azimuthDeg, 1e-6, "left eye azimuth is the mirror image");
            assertEquals(r.dx, l.dx, 1e-6);
            assertEquals(r.dy, l.dy, 1e-6);
            assertEquals(-r.dz, l.dz, 1e-6);
            assertTrue(r.dz > 0, "right-eye columns look to the fly's right (+z)");
            assertTrue(l.dz < 0, "left-eye columns look to the fly's left (-z)");
        }
    }

    @Test
    void directionsAreUnitVectorsConsistentWithTheAngles() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        for (RetinaGeometry.Column c : g.allColumns()) {
            double len = Math.sqrt(c.dx * c.dx + c.dy * c.dy + c.dz * c.dz);
            assertEquals(1.0, len, 1e-5);
            double az = Math.toRadians(c.azimuthDeg), el = Math.toRadians(c.elevationDeg);
            assertEquals(Math.cos(el) * Math.cos(az), c.dx, 1e-5);
            assertEquals(Math.sin(el), c.dy, 1e-5);
            assertEquals(Math.cos(el) * Math.sin(az), c.dz, 1e-5);
            assertEquals(c.index, g.column(c.index).index);
        }
    }

    @Test
    void nearestColumnFindsTheColumnLookingThatWay() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        for (RetinaGeometry.Column c : g.allColumns()) {
            assertSame(c, g.nearestColumn(c.dx, c.dy, c.dz));
            // any positive multiple of the direction, slightly perturbed, still resolves to the same column
            assertSame(c, g.nearestColumn(5 * c.dx + 0.01, 5 * c.dy - 0.01, 5 * c.dz));
        }
        assertNull(g.nearestColumn(0, 0, 0), "degenerate direction has no nearest column");
        // straight ahead (+x) is closest to the most anterior columns (smallest |azimuth|) of either eye
        RetinaGeometry.Column ahead = g.nearestColumn(1, 0, 0);
        RetinaGeometry.Column anteriorR = col(g, RIGHT, 12, 18);
        assertEquals(anteriorR.azimuthDeg, Math.abs(ahead.azimuthDeg), 1e-6);
    }

    @Test
    void columnsWithinSelectsByAngularDistance() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        RetinaGeometry.Column v = col(g, RIGHT, 10, 10);
        RetinaGeometry.Column posterior = col(g, RIGHT, 20, 10);
        RetinaGeometry.Column dorsal = col(g, RIGHT, 30, 30);

        assertArrayEquals(new int[]{v.index}, g.columnsWithin(v.azimuthDeg, v.elevationDeg, 1.0));
        assertEquals(8, g.columnsWithin(0, 0, 180).length, "a hemisphere-plus radius covers every column");
        assertEquals(0, g.columnsWithin(180, 0, 5).length, "looking backwards along -x hits nothing");

        double radius = angleDeg(v, posterior) + 1.0;
        assertTrue(radius < angleDeg(v, dorsal), "test radius must not reach the dorsal column");
        int[] got = g.columnsWithin(v.azimuthDeg, v.elevationDeg, radius);
        // independent brute-force reference
        int[] expected = g.allColumns().stream().filter(c -> angleDeg(v, c) <= radius).mapToInt(c -> c.index).sorted().toArray();
        assertArrayEquals(expected, got);
        assertTrue(Arrays.stream(got).anyMatch(i -> i == posterior.index));
        assertTrue(Arrays.stream(got).noneMatch(i -> i == dorsal.index));
        assertTrue(Arrays.stream(got).allMatch(i -> g.column(i).side == RIGHT), "the left eye is on the far side of the head");
    }

    @Test
    void raysPartitionAllColumnsExactlyOnce() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        List<Integer> seen = new ArrayList<>();
        for (int side : new int[]{LEFT, RIGHT}) {
            List<RetinaGeometry.Ray> rays = g.rays(side);
            assertFalse(rays.isEmpty());
            for (int r = 0; r < rays.size(); r++) {
                RetinaGeometry.Ray ray = rays.get(r);
                assertEquals(side, ray.side);
                assertEquals(r, ray.index, "ray index matches its position in rays(side)");
                assertTrue(ray.columns.length > 0, "empty ray");
                double len = Math.sqrt(ray.dx * ray.dx + ray.dy * ray.dy + ray.dz * ray.dz);
                assertEquals(1.0, len, 1e-5);
                assertEquals(Math.toDegrees(Math.asin(ray.dy)), ray.elevationDeg, 1e-3);
                assertEquals(Math.toDegrees(Math.atan2(ray.dz, ray.dx)), ray.azimuthDeg, 1e-3);
                double sx = 0, sy = 0, sz = 0;
                for (int ci : ray.columns) {
                    RetinaGeometry.Column c = g.column(ci);
                    assertEquals(side, c.side, "a ray only groups columns of its own eye");
                    assertEquals(ray.index, c.ray, "column.ray points back at the ray that contains it");
                    seen.add(ci);
                    sx += c.dx; sy += c.dy; sz += c.dz;
                }
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                assertEquals(sx / sl, ray.dx, 1e-5, "ray direction is the normalised mean of its columns");
                assertEquals(sy / sl, ray.dy, 1e-5);
                assertEquals(sz / sl, ray.dz, 1e-5);
            }
        }
        Collections.sort(seen);
        assertEquals(IntStream.range(0, g.columnCount()).boxed().toList(), seen, "every column in exactly one ray");
        // with the default 300 rays per eye, four well-separated columns each get their own ray
        assertEquals(4, g.rays(LEFT).size());
        assertEquals(4, g.rays(RIGHT).size());
    }

    @Test
    void fewRaysPerEyeGroupSeveralColumnsIntoOneRay() {
        RetinaGeometry.Params p = new RetinaGeometry.Params();
        p.useMeasuredEyeMap = false; // these tests exercise the linear fallback map
        p.flipHorizontal = false;
        p.raysPerEye = 1;
        RetinaGeometry g = new RetinaGeometry(eyes(), p);
        for (int side : new int[]{LEFT, RIGHT}) {
            List<RetinaGeometry.Ray> rays = g.rays(side);
            assertTrue(rays.size() >= 1 && rays.size() < 4, "grouping must merge columns, got " + rays.size() + " rays");
            int total = 0;
            List<Integer> seen = new ArrayList<>();
            for (RetinaGeometry.Ray r : rays) {
                total += r.columns.length;
                for (int ci : r.columns) seen.add(ci);
            }
            assertEquals(4, total);
            assertEquals(4, seen.stream().distinct().count());
        }
    }

    @Test
    void photoreceptorsAndLaminaCellsAttachToTheirColumn() {
        Connectome c = eyes();
        RetinaGeometry g = new RetinaGeometry(c, linearParams());
        for (RetinaGeometry.Column col : g.allColumns()) {
            assertEquals(3, col.photoreceptors.length, "two R1-R6 + one R7 per column");
            for (int pr : col.photoreceptors) {
                assertTrue(c.type(pr).startsWith("R"), c.type(pr));
                assertEquals(col.side, RetinaGeometry.sideOf(c.side(pr)));
            }
            assertTrue(col.l1 >= 0 && col.l2 >= 0 && col.l3 >= 0);
            assertEquals("L1", c.type(col.l1));
            assertEquals("L2", c.type(col.l2));
            assertEquals("L3", c.type(col.l3));
            assertEquals(col.side, RetinaGeometry.sideOf(c.side(col.l1)));
        }
    }

    @Test
    void entriesWithUnknownSideOrNonPositiveHexAreIgnored() {
        RetinaGeometry g = new RetinaGeometry(eyes(), linearParams());
        assertEquals(8, g.columnCount(), "the three bogus retina entries must not create columns");
        assertEquals(-1, g.columnIndex(RIGHT, 0, 10));
        assertEquals(-1, g.columnIndex(RIGHT, 10, -1));
        assertEquals(-1, g.columnIndex(LEFT, 99, 99));
        assertEquals(-1, RetinaGeometry.sideOf("M"));
        assertEquals(-1, RetinaGeometry.sideOf(""));
        assertEquals(LEFT, RetinaGeometry.sideOf("L"));
        assertEquals(RIGHT, RetinaGeometry.sideOf("R"));
        assertEquals(4, g.columns(LEFT).size());
        assertTrue(g.columns(LEFT).stream().allMatch(c -> c.side == LEFT));
        assertTrue(g.toString().contains("4 L + 4 R"));
    }

    @Test
    void flipParametersInvertTheAxes() {
        RetinaGeometry.Params p = new RetinaGeometry.Params();
        p.useMeasuredEyeMap = false; // these tests exercise the linear fallback map
        p.flipHorizontal = false;
        p.flipVertical = true;
        p.flipHorizontal = true;
        RetinaGeometry g = new RetinaGeometry(eyes(), p);
        RetinaGeometry.Column ventral = col(g, RIGHT, 10, 10);
        RetinaGeometry.Column dorsal = col(g, RIGHT, 30, 30);
        RetinaGeometry.Column posterior = col(g, RIGHT, 20, 10);
        RetinaGeometry.Column anterior = col(g, RIGHT, 12, 18);
        assertTrue(dorsal.elevationDeg < ventral.elevationDeg, "flipVertical swaps dorsal/ventral");
        assertTrue(posterior.azimuthDeg < anterior.azimuthDeg, "flipHorizontal swaps anterior/posterior");
        // still mirrored between eyes
        assertEquals(-ventral.azimuthDeg, col(g, LEFT, 10, 10).azimuthDeg, 1e-6);
    }

    @Test
    void columnsOutsideTheCalibratedRangeClampToTheExtremes() {
        AnnotatedConnectome s = new AnnotatedConnectome();
        s.retina(s.neuron("L1", -1, "R"), "R", 40, 40, Connectome.RETINA_L1); // u = 80 > uMax, w = 0
        s.retina(s.neuron("L1", -1, "R"), "R", 1, 1, Connectome.RETINA_L1);   // u = 2 < uMin
        s.retina(s.neuron("L1", -1, "R"), "R", 40, 1, Connectome.RETINA_L1);  // w = 39 > wMax
        s.retina(s.neuron("L1", -1, "R"), "R", 1, 40, Connectome.RETINA_L1);  // w = -39 < wMin
        RetinaGeometry g = new RetinaGeometry(s.build(), linearParams());
        RetinaGeometry.Params p = g.params;
        assertEquals(p.elevationMaxDeg, col(g, RIGHT, 40, 40).elevationDeg, 1e-4);
        assertEquals(p.elevationMinDeg, col(g, RIGHT, 1, 1).elevationDeg, 1e-4);
        assertEquals(p.azimuthMaxDeg, col(g, RIGHT, 40, 1).azimuthDeg, 1e-4);
        assertEquals(p.azimuthMinDeg, col(g, RIGHT, 1, 40).azimuthDeg, 1e-4);
        assertEquals(0, g.columnCount(LEFT));
        assertTrue(g.rays(LEFT).isEmpty(), "an eye without columns has no rays");
        assertEquals(4, g.rays(RIGHT).stream().mapToInt(r -> r.columns.length).sum());
    }
}
