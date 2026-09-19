package com.fruitfly.server;

import com.fruitfly.FlyBrainService;
import com.fruitfly.FruitFlyMod;
import com.fruitfly.entity.FlyEntity;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * /flybrain build [size] | clear | link | status — the connectome as a walk-through block structure.
 */
public final class BrainCommands {
    private BrainCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("flybrain")
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx, 64))
                        .then(Commands.argument("size", IntegerArgumentType.integer(16, 256))
                                .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "size")))))
                .then(Commands.literal("clear").executes(BrainCommands::clear))
                .then(Commands.literal("link").executes(BrainCommands::link))
                .then(Commands.literal("status").executes(BrainCommands::status)));
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int size) {
        CommandSourceStack src = ctx.getSource();
        FlyBrainService svc = FruitFlyMod.BRAIN;
        if (!svc.ready()) {
            src.sendFailure(Component.literal("Connectome not loaded yet" + (svc.loadError() != null ? ": " + svc.loadError() : "")));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        // place the structure a little away from the caller, standing on the caller's feet level
        Vec3 look = src.getRotation() == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(src.getRotation());
        BlockPos origin = BlockPos.containing(p.x + look.x * (size * 0.75 + 4), p.y, p.z + look.z * (size * 0.75 + 4));
        BrainBuilder.Structure s = BrainBuilder.build(level, origin, size, svc.connectome());
        // auto-link the nearest fly
        List<FlyEntity> flies = level.getEntitiesOfClass(FlyEntity.class, AABB.ofSize(p, 128, 128, 128), f -> f.brain() != null);
        if (!flies.isEmpty()) {
            flies.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
            BrainBuilder.link(s, flies.get(0));
        }
        final int cells = s.totalCells();
        final boolean linked = !flies.isEmpty();
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building the male CNS: %d soma-blocks in a %d-block structure at %s (placing ~2500 blocks/tick)%s",
                cells, size, origin.toShortString(), linked ? "; linked to fly #" + flies.get(0).getId() + " — spiking neurons flash" : "")), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int n = BrainBuilder.clear(src.getLevel());
        src.sendSuccess(() -> Component.literal("Clearing " + n + " brain structure(s)"), true);
        return n;
    }

    private static int link(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<BrainBuilder.Structure> ss = BrainBuilder.structures(src.getLevel());
        if (ss.isEmpty()) {
            src.sendFailure(Component.literal("No brain structure in this world; run /flybrain build first"));
            return 0;
        }
        Vec3 p = src.getPosition();
        List<FlyEntity> flies = src.getLevel().getEntitiesOfClass(FlyEntity.class, AABB.ofSize(p, 128, 128, 128), f -> f.brain() != null);
        if (flies.isEmpty()) {
            src.sendFailure(Component.literal("No fly with a running brain within 64 blocks"));
            return 0;
        }
        flies.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        for (BrainBuilder.Structure s : ss) BrainBuilder.link(s, flies.get(0));
        src.sendSuccess(() -> Component.literal("Linked " + ss.size() + " structure(s) to fly #" + flies.get(0).getId()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<BrainBuilder.Structure> ss = BrainBuilder.structures(src.getLevel());
        StringBuilder sb = new StringBuilder("Brain structures: " + ss.size());
        for (BrainBuilder.Structure s : ss) {
            sb.append(String.format(Locale.ROOT, "\n at %s size %d: %d cells, %d blocks still queued%s", s.origin.toShortString(), s.size,
                    s.totalCells(), s.remaining(), s.linked == null ? "" : ", linked to fly #" + s.linked.getId()));
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
}
