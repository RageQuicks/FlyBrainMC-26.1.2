package com.fruitfly.client.hud;

import com.fruitfly.entity.FlyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which fly the client is looking at. Shared by the neuroscope panel, the brain view and the in-world overlay so
 * they always agree. Two modes: <b>nearest</b> (default; the closest fly with fresh telemetry, with hysteresis) and
 * <b>locked</b> onto one fly by number/id (stays even if another fly comes closer; released with {@link #unlock()}).
 *
 * <p>All ids are network entity ids, which are only meaningful within one connection: {@code FruitFlyClient} calls
 * {@link #reset()} on disconnect.</p>
 */
public final class FlyFocus {
    /** Flies farther than this from the player are never auto-selected; also the reach of {@link #lockLooked()}. */
    public static final double RANGE = 48.0;
    private static final long RESCAN_NANOS = 250_000_000L;
    /** Extra hitbox margin (blocks) when picking a fly with the crosshair; the real hitbox is only 0.5 x 0.3. */
    private static final float PICK_INFLATE = 0.25f;

    private static int lockedId = -1;
    private static int currentId = -1;
    private static long lastScanNanos;

    private FlyFocus() { }

    public static boolean isLocked() { return lockedId >= 0; }
    public static int lockedId() { return lockedId; }
    public static int currentId() { return currentId; }

    /** Lock onto a fly by network id. */
    public static void lock(int entityId) {
        lockedId = entityId;
        currentId = entityId;
    }

    /** Lock onto the fly currently shown (nearest at this moment); false if none. */
    public static boolean lockCurrent() {
        FlyEntity f = current();
        if (f == null) return false;
        lock(f.getId());
        return true;
    }

    /** Lock onto the fly under the crosshair (out to {@link #RANGE} blocks); false if the player is not looking at a fly. */
    public static boolean lockLooked() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) return false;
        if (mc.crosshairPickEntity instanceof FlyEntity f && f.isAlive()) {
            lock(f.getId());
            return true;
        }
        // crosshairPickEntity only covers the ~3-block interaction range; ray-pick flies ourselves out to RANGE
        Vec3 eye = player.getEyePosition(1f), dir = player.getViewVector(1f), end = eye.add(dir.scale(RANGE));
        AABB sweep = player.getBoundingBox().expandTowards(dir.scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.level, player, eye, end, sweep,
                e -> e instanceof FlyEntity && e.isAlive(), PICK_INFLATE);
        if (hit != null && hit.getEntity() instanceof FlyEntity f) {
            lock(f.getId());
            return true;
        }
        return false;
    }

    /** Back to automatic nearest-fly selection. */
    public static void unlock() { lockedId = -1; }

    /** Forget lock and selection (network ids do not survive a disconnect; the same id may be a different fly next time). */
    public static void reset() {
        lockedId = -1;
        currentId = -1;
        lastScanNanos = 0L;
    }

    /**
     * Resolve a fly by the number on its name tag ({@code Fly-<number>}); falls back to the network id only when no
     * loaded fly carries that number (fly numbers and network ids are both small ints and do collide).
     */
    public static FlyEntity find(int numberOrId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (numberOrId > 0) for (FlyEntity f : nearby(256)) if (f.getFlyNumber() == numberOrId) return f;
        return mc.level.getEntity(numberOrId) instanceof FlyEntity f ? f : null;
    }

    /** All loaded flies within {@code range} blocks of the player, nearest first. */
    public static List<FlyEntity> nearby(double range) {
        Minecraft mc = Minecraft.getInstance();
        List<FlyEntity> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        LocalPlayer player = mc.player;
        out.addAll(mc.level.getEntitiesOfClass(FlyEntity.class, player.getBoundingBox().inflate(range)));
        out.sort(Comparator.comparingDouble(f -> f.distanceToSqr(player)));
        return out;
    }

    /**
     * The focused fly, or null. Locked mode returns the locked fly while it is loaded and alive; nearest mode picks
     * the closest alive fly with fresh telemetry (rescanned 4x/s, kept unless another fly is clearly closer).
     */
    public static FlyEntity current() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return null;
        if (lockedId >= 0) {
            Entity e = level.getEntity(lockedId);
            if (e instanceof FlyEntity f && f.isAlive()) {
                currentId = f.getId();
                return f;
            }
            return null; // locked fly not loaded here (stay locked; the HUD says so and how to release)
        }
        long now = System.nanoTime();
        FlyEntity cur = null;
        if (currentId >= 0) {
            Entity e = level.getEntity(currentId);
            if (e instanceof FlyEntity f && f.isAlive() && TelemetryStore.hasFresh(f.getId())
                    && f.distanceToSqr(player) <= RANGE * RANGE * 1.25) cur = f;
        }
        if (now - lastScanNanos < RESCAN_NANOS) return cur;
        lastScanNanos = now;
        FlyEntity best = null;
        double bestD = RANGE * RANGE;
        for (FlyEntity f : level.getEntitiesOfClass(FlyEntity.class, player.getBoundingBox().inflate(RANGE))) {
            if (!f.isAlive() || !TelemetryStore.hasFresh(f.getId())) continue;
            double d = f.distanceToSqr(player);
            if (d <= bestD) {
                bestD = d;
                best = f;
            }
        }
        if (cur != null && best != null && best != cur && bestD > 0.5 * cur.distanceToSqr(player)) best = cur;
        currentId = best == null ? -1 : best.getId();
        return best;
    }

    /** Human-readable focus state for HUD status lines. */
    public static String describe(FlyEntity fly) {
        if (lockedId >= 0) return fly == null ? "LOCKED on fly id " + lockedId + " (not loaded)" : "LOCKED";
        return "nearest";
    }
}
