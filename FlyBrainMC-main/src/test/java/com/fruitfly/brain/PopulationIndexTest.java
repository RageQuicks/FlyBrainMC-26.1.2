package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PopulationIndexTest {

    /** Fixture with every annotation kind the spec grammar can address. */
    private static final class Fixture {
        final Connectome c;
        final PopulationIndex pi;
        final int dnp09L, dnp09R, dna02L, dna01R, ornDm1L, ornDm4R, tactileWing, tactileLeg, hairPlate, joA1, joCl, mn9, kc;

        Fixture() {
            AnnotatedConnectome s = new AnnotatedConnectome();
            dnp09L = s.neuron("DNp09", 1, "L");
            s.superclass(dnp09L, "descending_neuron").nt(dnp09L, "acetylcholine");
            dnp09R = s.neuron("DNp09", 1, "R");
            s.superclass(dnp09R, "descending_neuron").nt(dnp09R, "acetylcholine");
            dna02L = s.neuron("DNa02", 1, "L");
            s.superclass(dna02L, "descending_neuron").nt(dna02L, "acetylcholine");
            dna01R = s.neuron("DNa01", 1, "R");
            s.superclass(dna01R, "descending_neuron").nt(dna01R, "acetylcholine");
            ornDm1L = s.neuron("ORN_DM1", 1, "L", "olfactory", "");
            ornDm4R = s.neuron("ORN_DM4", 1, "R", "olfactory", "");
            tactileWing = s.neuron("SNta01", 1, "L", "mechanosensory_tactile", "");
            s.nerve(tactileWing, "ADMN");
            tactileLeg = s.neuron("SNta02", 1, "R", "mechanosensory_tactile", "");
            s.nerve(tactileLeg, "ProLN");
            hairPlate = s.neuron("SNpp19", 1, "R", "mechanosensory_proprioceptive", "hair plate");
            s.nerve(hairPlate, "ProLN");
            joA1 = s.neuron("JO-A1", 1, "L", "mechanosensory", "auditory");
            joCl = s.neuron("JO-CL", 1, "R", "mechanosensory", "wind_gravity");
            mn9 = s.neuron("MN9", 1, "M", "", "pm");
            s.superclass(mn9, "cb_motor").nt(mn9, "acetylcholine").nerve(mn9, "PhN");
            kc = s.neuron("KCg-m", -1, "L", "Kenyon_Cell", "");
            s.nt(kc, "gaba");
            c = s.build();
            pi = new PopulationIndex(c);
        }
    }

    @Test
    void exactTypesAndSideSuffixes() {
        Fixture f = new Fixture();
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R}, f.pi.resolve("DNp09"));
        assertArrayEquals(new int[]{f.dnp09L}, f.pi.resolve("DNp09/L"));
        assertArrayEquals(new int[]{f.dnp09R}, f.pi.resolve("DNp09/R"));
        assertEquals(0, f.pi.resolve("DNp09/M").length, "no midline DNp09");
        assertArrayEquals(new int[]{f.mn9}, f.pi.resolve("MN9/M"));
        assertArrayEquals(new int[]{f.dnp09L}, f.pi.resolve("type:DNp09/L"), "explicit type: kind");
        assertArrayEquals(new int[]{f.ornDm4R}, f.pi.resolve("prefix:ORN_/R"), "side suffix applies to any term kind");
        assertArrayEquals(new int[]{f.dnp09L, f.dna02L}, f.pi.resolve("superclass:descending_neuron/L"));
    }

    @Test
    void unknownTypesAndEmptySpecsResolveToNothing() {
        Fixture f = new Fixture();
        assertEquals(0, f.pi.resolve("DNx99").length);
        assertEquals(0, f.pi.resolve("dnp09").length, "type names are case-sensitive");
        assertEquals(0, f.pi.resolve("class:nothing").length);
        assertEquals(0, f.pi.resolve("subclass:xyz").length);
        assertEquals(0, f.pi.resolve("prefix:ZZZ").length);
        assertEquals(0, f.pi.resolve("body:1").length, "unknown bodyId");
        assertEquals(0, f.pi.resolve("").length);
        assertEquals(0, f.pi.resolve(" , ").length);
        assertEquals(0, f.pi.resolve("DNx99&DNp09").length, "intersection with an empty set is empty");
    }

    @Test
    void unknownTermKindIsRejected() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.pi.resolve("bogus:x"));
        assertThrows(IllegalArgumentException.class, () -> f.pi.resolve("DNp09,bogus:x"));
    }

    @Test
    void intersectionsWithAmpersand() {
        Fixture f = new Fixture();
        // the exact specs SensoryEncoders uses for body-region tactile neurons
        assertArrayEquals(new int[]{f.tactileWing}, f.pi.resolve("class:mechanosensory_tactile&nerve:ADMN"));
        assertArrayEquals(new int[]{f.tactileLeg}, f.pi.resolve("class:mechanosensory_tactile&nerve:ProLN"));
        assertArrayEquals(new int[]{f.hairPlate}, f.pi.resolve("class:mechanosensory_proprioceptive&subclass:hair plate"));
        assertEquals(0, f.pi.resolve("class:mechanosensory_tactile&nerve:PhN").length);
        // intersections of type filters
        assertArrayEquals(new int[]{f.dna02L, f.dna01R}, f.pi.resolve("prefix:DNa&contains:0"));
        assertArrayEquals(new int[]{f.dna02L}, f.pi.resolve("prefix:DNa&contains:02"));
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R, f.dna02L, f.dna01R}, f.pi.resolve("superclass:descending_neuron&nt:acetylcholine"));
        assertArrayEquals(new int[]{f.dna02L}, f.pi.resolve("superclass:descending_neuron&nt:acetylcholine&prefix:DNa&contains:02"), "chained");
        // side suffix applies after the intersection
        assertArrayEquals(new int[]{f.dna01R}, f.pi.resolve("prefix:DNa&contains:0/R"));
        assertArrayEquals(new int[]{f.dna02L}, f.pi.resolve("prefix:DNa&contains:0/L"));
        // whitespace around the ampersand is tolerated
        assertArrayEquals(new int[]{f.tactileWing}, f.pi.resolve("class:mechanosensory_tactile & nerve:ADMN"));
    }

    @Test
    void unionsAreDeduplicatedAndSortedByIndex() {
        Fixture f = new Fixture();
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R}, f.pi.resolve("DNp09/R, DNp09"), "duplicates collapse");
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R, f.dna02L}, f.pi.resolve("DNa02, DNp09"), "sorted by dense index");
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R, f.dna02L, f.dna01R},
                f.pi.resolve("DNp09/L,DNp09/R,DNa02/L,DNa01/R"));
        assertArrayEquals(new int[]{f.ornDm1L, f.ornDm4R}, f.pi.resolve("prefix:ORN_DM1,prefix:ORN_DM4"));
        assertArrayEquals(new int[]{f.tactileWing, f.tactileLeg},
                f.pi.resolve("class:mechanosensory_tactile&nerve:ProLN,class:mechanosensory_tactile&nerve:ADMN"), "union of intersections");
    }

    @Test
    void annotationKindsResolveThroughTheConnectomeTables() {
        Fixture f = new Fixture();
        assertArrayEquals(new int[]{f.ornDm1L, f.ornDm4R}, f.pi.resolve("class:olfactory"));
        assertArrayEquals(new int[]{f.joA1}, f.pi.resolve("subclass:auditory"));
        assertArrayEquals(new int[]{f.joCl}, f.pi.resolve("subclass:wind_gravity"));
        assertArrayEquals(new int[]{f.mn9}, f.pi.resolve("subclass:pm"));
        assertArrayEquals(new int[]{f.kc}, f.pi.resolve("class:Kenyon_Cell"));
        assertArrayEquals(new int[]{f.kc}, f.pi.resolve("prefix:KC"));
        assertArrayEquals(new int[]{f.kc}, f.pi.resolve("nt:gaba"));
        assertArrayEquals(new int[]{f.mn9}, f.pi.resolve("superclass:cb_motor"));
        assertArrayEquals(new int[]{f.tactileLeg, f.hairPlate}, f.pi.resolve("nerve:ProLN"));
        assertArrayEquals(new int[]{f.joA1, f.joCl}, f.pi.resolve("contains:JO-"));
        assertArrayEquals(new int[]{f.dnp09L, f.dnp09R, f.dna02L, f.dna01R}, f.pi.resolve("prefix:DN"));
        assertArrayEquals(new int[]{f.mn9}, f.pi.resolve("body:" + f.c.bodyId[f.mn9]));
        assertArrayEquals(new int[]{f.mn9}, f.pi.resolve("BODY:" + f.c.bodyId[f.mn9]), "kind is case-insensitive");
        // a fixture with empty dimorphism / fruDsx / neuromere tables resolves those to nothing rather than failing
        assertEquals(0, f.pi.resolve("dimorphism:male-specific").length);
        assertEquals(0, f.pi.resolve("frudsx:fru").length);
        assertEquals(0, f.pi.resolve("neuromere:T1").length);
    }

    @Test
    void allSelectsEveryNeuron() {
        Fixture f = new Fixture();
        assertArrayEquals(IntStream.range(0, f.c.n).toArray(), f.pi.resolve("all"));
        assertArrayEquals(IntStream.range(0, f.c.n).toArray(), f.pi.resolve("ALL"));
        assertArrayEquals(IntStream.range(0, f.c.n).toArray(), f.pi.resolve("all:x"));
        int[] left = f.pi.resolve("all/L");
        assertTrue(left.length > 0 && Arrays.stream(left).allMatch(i -> "L".equals(f.c.side(i))));
        assertEquals(f.c.n, f.pi.resolve("all/L").length + f.pi.resolve("all/R").length + f.pi.resolve("all/M").length);
    }

    @Test
    void resultsAreCachedPerSpecString() {
        Fixture f = new Fixture();
        int[] a = f.pi.resolve("DNp09/L");
        assertSame(a, f.pi.resolve("DNp09/L"), "second lookup returns the cached array");
        assertNotSame(a, f.pi.resolve("DNp09/L "), "the cache key is the literal spec string");
        assertTrue(Arrays.asList(f.pi.cachedSpecs()).contains("DNp09/L"));
        assertSame(f.c, f.pi.connectome());
    }

    @Test
    void worksWithThePlainSyntheticConnectomeToo() {
        // SyntheticConnectome leaves class/subclass tables empty: class terms are empty, type terms still work
        SyntheticConnectome s = new SyntheticConnectome();
        int a = s.neuron("LC4", 1, "L"), b = s.neuron("LC4", 1, "R");
        PopulationIndex pi = new PopulationIndex(s.build());
        assertArrayEquals(new int[]{a, b}, pi.resolve("LC4"));
        assertArrayEquals(new int[]{b}, pi.resolve("LC4/R"));
        assertEquals(0, pi.resolve("class:visual").length);
        assertEquals(0, pi.resolve("subclass:wm").length);
    }
}
