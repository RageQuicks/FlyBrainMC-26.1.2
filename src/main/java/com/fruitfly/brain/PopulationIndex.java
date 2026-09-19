package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Resolves human-readable population specs into neuron index arrays and caches them.
 *
 * Spec grammar (comma-separated union of terms; each term optionally suffixed with {@code /L}, {@code /R} or
 * {@code /M} to restrict soma side):
 * <ul>
 *   <li>{@code DNp09}            exact type</li>
 *   <li>{@code prefix:ORN_}      type prefix</li>
 *   <li>{@code class:olfactory}  neuron class</li>
 *   <li>{@code superclass:descending_neuron}</li>
 *   <li>{@code subclass:wm}      subclass (e.g. wing motor)</li>
 *   <li>{@code body:10783}       neuPrint bodyId</li>
 *   <li>{@code contains:LC10}    type contains substring</li>
 *   <li>{@code dimorphism:male-specific}</li>
 *   <li>{@code all}              every neuron</li>
 * </ul>
 * Example: {@code "DNa02/L, DNa01/L"} or {@code "prefix:ORN_DM1,prefix:ORN_VA2"}.
 */
public final class PopulationIndex {
    private final Connectome c;
    private final Map<String, int[]> cache = new LinkedHashMap<>();

    public PopulationIndex(Connectome c) { this.c = c; }

    public Connectome connectome() { return c; }

    public synchronized int[] resolve(String spec) {
        int[] r = cache.get(spec);
        if (r == null) {
            r = compute(spec);
            cache.put(spec, r);
        }
        return r;
    }

    private int[] compute(String spec) {
        List<int[]> parts = new ArrayList<>();
        for (String raw : spec.split(",")) {
            String term = raw.trim();
            if (term.isEmpty()) continue;
            String side = null;
            int slash = term.lastIndexOf('/');
            if (slash > 0 && slash == term.length() - 2) {
                side = term.substring(slash + 1);
                term = term.substring(0, slash);
            }
            int[] ids = computeTerm(term);
            if (side != null) {
                final String s = side;
                ids = Arrays.stream(ids).filter(i -> c.side(i).equals(s)).toArray();
            }
            parts.add(ids);
        }
        return parts.stream().flatMapToInt(IntStream::of).distinct().sorted().toArray();
    }

    /** A term may be an intersection of sub-terms joined by '&', e.g. {@code class:mechanosensory_tactile&nerve:ADMN}. */
    private int[] computeTerm(String term) {
        if (term.indexOf('&') >= 0) {
            int[] acc = null;
            for (String sub : term.split("&")) {
                int[] ids = computeSimpleTerm(sub.trim());
                if (acc == null) {
                    acc = ids;
                } else {
                    java.util.BitSet bs = new java.util.BitSet(c.n);
                    for (int i : ids) bs.set(i);
                    acc = Arrays.stream(acc).filter(bs::get).toArray();
                }
            }
            return acc == null ? new int[0] : acc;
        }
        return computeSimpleTerm(term);
    }

    private int[] computeSimpleTerm(String term) {
        int colon = term.indexOf(':');
        if (colon < 0) {
            if (term.equalsIgnoreCase("all")) return IntStream.range(0, c.n).toArray();
            return c.indicesOfType(term);
        }
        String kind = term.substring(0, colon).trim().toLowerCase();
        String arg = term.substring(colon + 1).trim();
        switch (kind) {
            case "prefix": return c.indicesOfTypePrefix(arg);
            case "contains": return c.indicesWhere(i -> c.type(i).contains(arg));
            case "type": return c.indicesOfType(arg);
            case "class": return c.indicesOfClass(arg);
            case "superclass": return c.indicesOfSuperclass(arg);
            case "subclass": return c.indicesWhere(i -> c.subclass(i).equals(arg));
            case "dimorphism": return c.indicesWhere(i -> c.dimorphism(i).equals(arg));
            case "frudsx": return c.indicesWhere(i -> c.fruDsxOf(i).equals(arg));
            case "nt": return c.indicesWhere(i -> c.nt(i).equals(arg));
            case "neuromere": return c.indicesWhere(i -> c.neuromere(i).equals(arg));
            case "nerve": return c.indicesWhere(i -> c.entryNerve(i).equals(arg));
            case "body": {
                int idx = c.indexOfBodyId(Long.parseLong(arg));
                return idx < 0 ? new int[0] : new int[]{idx};
            }
            case "all": return IntStream.range(0, c.n).toArray();
            default: throw new IllegalArgumentException("Unknown population term: " + term);
        }
    }

    /** Names of all cached populations (for debugging / HUD). */
    public synchronized String[] cachedSpecs() { return cache.keySet().toArray(new String[0]); }
}
