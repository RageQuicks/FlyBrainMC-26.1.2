package com.fruitfly.entity;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft odor sources → glomerular drive vectors (docs/research/sensory-mapping.md §a.3).
 * Keys are glomerulus names matching ORN_<glomerulus> types (DM1, VA2, V = CO2, DA2 = geosmin ...) and the
 * hygro/thermo VP glomeruli (VP2 heat, VP3a/b cold, VP4 dry, VP5 moist).
 */
public final class OdorTable {
    /** An odor source: glomerular vector and emission strength (multiplies the vector). */
    public record Odor(Map<String, Float> glomeruli, float strength) {}

    private static final Map<Item, Odor> ITEMS = new HashMap<>();
    private static final Map<Block, Odor> BLOCKS = new HashMap<>();

    private static Odor odor(float strength, Object... kv) {
        Map<String, Float> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).floatValue());
        return new Odor(m, strength);
    }

    static {
        Odor apple = odor(1.0f, "DM1", 1.0, "DM2", 0.9, "VA2", 0.6, "DM4", 0.5, "VM2", 0.4, "DM3", 0.3);
        ITEMS.put(Items.APPLE, apple);
        ITEMS.put(Items.GOLDEN_APPLE, odor(1.5f, "DM1", 1.0, "DM2", 0.9, "VA2", 0.6, "DM4", 0.5, "VM2", 0.4, "DM3", 0.3));
        ITEMS.put(Items.ENCHANTED_GOLDEN_APPLE, odor(1.5f, "DM1", 1.0, "DM2", 0.9, "VA2", 0.6, "DM4", 0.5));
        Odor berries = odor(0.9f, "DM1", 0.8, "DM2", 0.7, "VA2", 0.5, "DM4", 0.4, "VL2a", 0.3);
        ITEMS.put(Items.SWEET_BERRIES, berries);
        ITEMS.put(Items.GLOW_BERRIES, berries);
        Odor melon = odor(0.9f, "DM1", 0.7, "DM2", 0.6, "VM5d", 0.5, "VA2", 0.4);
        ITEMS.put(Items.MELON_SLICE, melon);
        ITEMS.put(Items.MELON, melon);
        Odor honey = odor(1.0f, "VL2a", 0.9, "D", 0.6, "DM1", 0.4, "VA2", 0.3);
        ITEMS.put(Items.HONEY_BOTTLE, honey);
        ITEMS.put(Items.HONEY_BLOCK, honey);
        ITEMS.put(Items.HONEYCOMB, odor(0.6f, "VL2a", 0.6, "D", 0.4));
        Odor baked = odor(1.0f, "VA2", 0.8, "DP1l", 0.6, "DM1", 0.5, "VM2", 0.4, "DL2d", 0.3);
        ITEMS.put(Items.CAKE, baked);
        ITEMS.put(Items.COOKIE, baked);
        ITEMS.put(Items.BREAD, odor(0.8f, "VA2", 0.8, "DP1l", 0.6, "DM1", 0.3));
        ITEMS.put(Items.SUGAR, odor(0.5f, "VA2", 0.5, "DM1", 0.3));
        ITEMS.put(Items.PUMPKIN_PIE, baked);
        ITEMS.put(Items.FERMENTED_SPIDER_EYE, odor(1.0f, "DP1l", 0.9, "VA2", 0.7, "DM1", 0.5, "DM5", 0.6));
        ITEMS.put(Items.ROTTEN_FLESH, odor(1.0f, "VM1", 1.0, "VM6v", 0.9, "VM6m", 0.9, "VM6l", 0.9, "VC5", 0.9, "VM4", 0.7, "DP1l", 0.5, "DA2", 0.8));
        Odor aversive = odor(1.0f, "DA2", 1.0, "DL4", 0.4);
        ITEMS.put(Items.SPIDER_EYE, aversive);
        ITEMS.put(Items.POISONOUS_POTATO, aversive);
        ITEMS.put(Items.PUFFERFISH, aversive);
        Odor mushroom = odor(0.7f, "DC2", 0.8, "VA3", 0.5, "VM5d", 0.3);
        ITEMS.put(Items.BROWN_MUSHROOM, mushroom);
        ITEMS.put(Items.RED_MUSHROOM, mushroom);
        Odor green = odor(0.5f, "DL5", 0.7, "VA3", 0.5, "D", 0.3);
        ITEMS.put(Items.WHEAT, green);
        ITEMS.put(Items.HAY_BLOCK, green);
        ITEMS.put(Items.CARROT, odor(0.6f, "DM1", 0.4, "DL5", 0.4, "VA3", 0.3));
        ITEMS.put(Items.POTATO, odor(0.4f, "DL5", 0.4, "VC1", 0.3));
        ITEMS.put(Items.BEETROOT, odor(0.6f, "DA2", 0.5, "DL5", 0.4));
        ITEMS.put(Items.CHORUS_FRUIT, odor(0.8f, "DM1", 0.6, "VL2a", 0.5));
        ITEMS.put(Items.DRIED_KELP, odor(0.4f, "VM1", 0.4, "VC5", 0.3));
        ITEMS.put(Items.TORCH, odor(0.4f, "VC2", 0.6, "V", 0.4));
        ITEMS.put(Items.CAMPFIRE, odor(1.0f, "V", 0.9, "VC2", 0.8, "DP1l", 0.4, "VP2", 0.7));

        BLOCKS.put(Blocks.CAKE, baked);
        BLOCKS.put(Blocks.MELON, melon);
        BLOCKS.put(Blocks.SWEET_BERRY_BUSH, berries);
        BLOCKS.put(Blocks.CAVE_VINES, berries);
        BLOCKS.put(Blocks.CAVE_VINES_PLANT, berries);
        BLOCKS.put(Blocks.HONEY_BLOCK, honey);
        BLOCKS.put(Blocks.BEE_NEST, odor(0.8f, "VL2a", 0.7, "D", 0.5));
        BLOCKS.put(Blocks.BEEHIVE, odor(0.8f, "VL2a", 0.7, "D", 0.5));
        BLOCKS.put(Blocks.COMPOSTER, odor(0.9f, "DP1l", 0.7, "VA2", 0.6, "DM5", 0.5, "VM1", 0.4));
        Odor fire = odor(1.0f, "V", 0.9, "VC2", 0.8, "DP1l", 0.4, "VP2", 0.7);
        BLOCKS.put(Blocks.CAMPFIRE, fire);
        BLOCKS.put(Blocks.SOUL_CAMPFIRE, fire);
        BLOCKS.put(Blocks.FIRE, fire);
        BLOCKS.put(Blocks.SOUL_FIRE, fire);
        BLOCKS.put(Blocks.TORCH, odor(0.4f, "VC2", 0.6, "V", 0.4));
        BLOCKS.put(Blocks.WALL_TORCH, odor(0.4f, "VC2", 0.6, "V", 0.4));
        Odor lava = odor(1.0f, "VP2", 1.0, "V", 0.6, "VP4", 0.8);
        BLOCKS.put(Blocks.LAVA, lava);
        BLOCKS.put(Blocks.MAGMA_BLOCK, odor(0.8f, "VP2", 1.0, "VP4", 0.6));
        Odor cold = odor(0.7f, "VP3a", 1.0, "VP3b", 1.0, "VP5", 0.5);
        BLOCKS.put(Blocks.ICE, cold);
        BLOCKS.put(Blocks.PACKED_ICE, cold);
        BLOCKS.put(Blocks.BLUE_ICE, cold);
        BLOCKS.put(Blocks.SNOW_BLOCK, cold);
        BLOCKS.put(Blocks.SNOW, cold);
        BLOCKS.put(Blocks.POWDER_SNOW, cold);
        BLOCKS.put(Blocks.WATER, odor(0.8f, "VP5", 1.0));
        BLOCKS.put(Blocks.BROWN_MUSHROOM, mushroom);
        BLOCKS.put(Blocks.RED_MUSHROOM, mushroom);
        BLOCKS.put(Blocks.HAY_BLOCK, green);
        BLOCKS.put(Blocks.WHEAT, green);
    }

    private static final Odor FLOWER = odor(0.6f, "VL2a", 0.7, "D", 0.6, "VA6", 0.5);
    private static final Odor LEAVES = odor(0.25f, "DL5", 0.7, "VA3", 0.4);

    public static Odor forItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ITEMS.get(stack.getItem());
    }

    public static Odor forBlock(BlockState state) {
        if (state == null || state.isAir()) return null;
        Odor o = BLOCKS.get(state.getBlock());
        if (o != null) return o;
        if (state.is(BlockTags.FLOWERS)) return FLOWER;
        if (state.is(BlockTags.LEAVES)) return LEAVES;
        return null;
    }

    /** Glomeruli marked aversive at high concentration (used for HUD colouring only). */
    public static boolean isAversive(String glomerulus) {
        return switch (glomerulus) {
            case "DA2", "V", "DL4", "DM5", "VC2", "VP2", "VP4" -> true;
            default -> false;
        };
    }
}
