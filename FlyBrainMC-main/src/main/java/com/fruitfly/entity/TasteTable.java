package com.fruitfly.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Contact taste: Minecraft food → gustatory receptor neuron types (docs/research/sensory-mapping.md §b.3).
 * Modalities follow Engert et al. 2022: sugar = LB3b/LB3c (labellar) + LgLG3/LgLG4 (tarsal) + PhG1a-c (pharyngeal);
 * water = LB3a; bitter = LB1a-d + LgAG1; high salt = LB3d; low salt / amino acids = LB1e.
 */
public final class TasteTable {
    /** GRN type → drive 0..1, split into tarsal (legs touching) and labellar (proboscis on it) sets. */
    public record Taste(Map<String, Float> tarsal, Map<String, Float> labellar, float nutrition) {}

    private static Map<String, Float> m(Object... kv) {
        Map<String, Float> r = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) r.put((String) kv[i], ((Number) kv[i + 1]).floatValue());
        return r;
    }

    public static final Taste SUGAR = new Taste(
            m("LgLG3", 1.0, "LgLG4", 0.8, "WG2", 0.6),
            m("LB3b", 1.0, "LB3c", 1.0, "PhG1a", 0.9, "PhG1b", 0.9, "PhG1c", 0.9), 1.0f);
    public static final Taste FRUIT = new Taste(
            m("LgLG3", 0.8, "LgLG4", 0.6),
            m("LB3b", 0.8, "LB3c", 0.8, "LB3a", 0.4, "PhG1a", 0.7, "PhG1b", 0.7, "PhG1c", 0.7), 0.8f);
    public static final Taste WATER = new Taste(
            m(),
            m("LB3a", 1.0, "PhG3", 0.8, "PhG4", 0.8), 0.0f);
    public static final Taste BITTER = new Taste(
            m("LgAG1", 1.0),
            m("LB1a", 1.0, "LB1b", 1.0, "LB1c", 1.0, "LB1d", 1.0), -0.5f);
    public static final Taste SALTY = new Taste(
            m(),
            m("LB3d", 0.9, "LB1e", 0.4), 0.1f);
    public static final Taste PROTEIN = new Taste(
            m(),
            m("LB1e", 0.9, "dorsal_tpGRN", 0.9, "PhG16", 0.6), 0.5f);
    public static final Taste ROTTEN = new Taste(
            m("LgAG1", 0.7),
            m("LB1a", 0.6, "LB1b", 0.6, "LB1c", 0.6, "LB1d", 0.6, "LB1e", 0.8), 0.1f);
    /** Female cuticular hydrocarbons sensed by male foreleg GRNs (LgLG5-8 are male-specific). */
    public static final Taste FEMALE_PHEROMONE = new Taste(
            m("LgLG5", 1.0, "LgLG6", 1.0, "LgLG7", 1.0, "LgLG8", 1.0, "LgLG1a", 0.9, "LgLG1b", 0.9), m(), 0f);
    public static final Taste MALE_PHEROMONE = new Taste(
            m("LgAG1", 0.9, "LgLG2", 0.5), m(), 0f);

    private static final Map<Item, Taste> ITEMS = new HashMap<>();
    private static final Map<Block, Taste> BLOCKS = new HashMap<>();

    static {
        for (Item i : new Item[]{Items.SUGAR, Items.HONEY_BOTTLE, Items.HONEYCOMB, Items.HONEY_BLOCK, Items.CAKE, Items.COOKIE,
                Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.PUMPKIN_PIE}) ITEMS.put(i, SUGAR);
        for (Item i : new Item[]{Items.APPLE, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.MELON_SLICE, Items.MELON,
                Items.CARROT, Items.GOLDEN_CARROT, Items.CHORUS_FRUIT, Items.BREAD, Items.BEETROOT}) ITEMS.put(i, FRUIT);
        for (Item i : new Item[]{Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.PUFFERFISH, Items.FERMENTED_SPIDER_EYE}) ITEMS.put(i, BITTER);
        ITEMS.put(Items.SUSPICIOUS_STEW, BITTER);
        for (Item i : new Item[]{Items.DRIED_KELP, Items.SEA_PICKLE}) ITEMS.put(i, SALTY);
        for (Item i : new Item[]{Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.CHICKEN, Items.COOKED_CHICKEN,
                Items.MUTTON, Items.COOKED_MUTTON, Items.COD, Items.COOKED_COD, Items.SALMON, Items.COOKED_SALMON, Items.RABBIT, Items.COOKED_RABBIT}) ITEMS.put(i, PROTEIN);
        ITEMS.put(Items.ROTTEN_FLESH, ROTTEN);
        ITEMS.put(Items.WATER_BUCKET, WATER);
        ITEMS.put(Items.POTION, WATER);

        BLOCKS.put(Blocks.CAKE, SUGAR);
        BLOCKS.put(Blocks.HONEY_BLOCK, SUGAR);
        BLOCKS.put(Blocks.SWEET_BERRY_BUSH, SUGAR);
        BLOCKS.put(Blocks.MELON, FRUIT);
        BLOCKS.put(Blocks.WATER, WATER);
        BLOCKS.put(Blocks.CAVE_VINES, SUGAR);
        BLOCKS.put(Blocks.CAVE_VINES_PLANT, SUGAR);
    }

    public static Taste forItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ITEMS.get(stack.getItem());
    }

    public static Taste forBlock(BlockState state) {
        if (state == null || state.isAir()) return null;
        return BLOCKS.get(state.getBlock());
    }
}
