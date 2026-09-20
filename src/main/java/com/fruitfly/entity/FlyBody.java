package com.fruitfly.entity;

import com.fruitfly.FruitFlyConfig;
import com.fruitfly.brain.MotorDecoder;
import com.fruitfly.brain.SensoryFrame;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;


/**
 * The fly's "spinal cord and biomechanics": turns a decoded {@link MotorDecoder.MotorCommand} into motion.
 *
 * <p>Everything here is hand-built (see docs/research/embodied-precedents.md §11): absolute speed gains, flight
 * physics, hysteresis, and biomechanical translation of neural motor output.</p>
 */
public final class FlyBody {
    public static final class State {
        public boolean flying;
        public double hoverY;
        public int escapeTicks;
        public float proboscis;
        public double smoothedForward, smoothedYaw;
    }

    private FlyBody() {}

    public static void travel(FlyEntity fly, MotorDecoder.MotorCommand cmd, SensoryFrame frame, FruitFlyConfig cfg, State st) {
        final double dt = 0.05;
        MotorDecoder.Mode mode = cmd == null ? MotorDecoder.Mode.IDLE : cmd.mode;
        double fwd = cmd == null ? 0 : cmd.forward;
        double yaw = cmd == null ? 0 : cmd.yaw;
        double bwd = cmd == null ? 0 : cmd.backward;
        if (fly.brain() == null) {
            // No brain means no autonomous behavior. Do not replay or synthesize a fallback controller.
            st.smoothedForward *= 0.65;
            st.smoothedYaw *= 0.65;
            if (fly.onGround()) fly.setDeltaMovement(fly.getDeltaMovement().multiply(0.65, 1.0, 0.65));
            return;
        }

        // ---------------- mode arbitration on top of channels ----------------
        boolean jumpNow = false;
        switch (mode) {
            case ESCAPE -> {
                if (st.escapeTicks == 0) jumpNow = true;
                st.escapeTicks++;
                fwd = 0;
                yaw = 0;
            }
            case BRAKE -> { fwd = 0; yaw = 0; }
            case HALT -> { fwd *= 0.2; yaw *= 0.1; }
            case BACKWARD -> { fwd = -0.5 * Math.max(bwd, 0.3); }
            case FEED, GROOM, SONG -> { fwd = 0; yaw *= 0.3; }
            default -> { }
        }
        if (mode != MotorDecoder.Mode.ESCAPE) st.escapeTicks = 0;

        // smooth commands (DN → behaviour lag ~150 ms already consumed by the decoder; this removes tick jitter)
        st.smoothedForward += 0.35 * (fwd - st.smoothedForward);
        st.smoothedYaw += 0.35 * (yaw - st.smoothedYaw);
        double f = st.smoothedForward, yw = st.smoothedYaw;

        // ---------------- flight state ----------------
        boolean wantFly = cfg.flightEnabled && (mode == MotorDecoder.Mode.FLYING || jumpNow);
        if (wantFly && !st.flying) {
            st.flying = true;
            st.hoverY = fly.getY() + 1.2 + st.random.nextDouble();
        }
        if (mode == MotorDecoder.Mode.LANDING || (st.flying && !cfg.flightEnabled)) {
            if (fly.onGround() || fly.isInWater()) {
                st.flying = false;
                if (fly.decoder() != null) fly.decoder().setFlying(false);
            }
        }
        if (st.flying && mode != MotorDecoder.Mode.FLYING && mode != MotorDecoder.Mode.LANDING && mode != MotorDecoder.Mode.ESCAPE
                && (fly.onGround() || fly.isInWater())) {
            st.flying = false;
            if (fly.decoder() != null) fly.decoder().setFlying(false);
        }

        // ---------------- rotation ----------------
        double turnRate = st.flying ? cfg.flightTurnRateDegPerS : cfg.turnRateDegPerS;
        float newYaw = fly.getYRot() + (float) (yw * turnRate * dt);
        fly.setYRot(newYaw);
        fly.yBodyRot = newYaw;
        fly.yHeadRot = newYaw;
        fly.setXRot(0);

        // ---------------- translation ----------------
        Vec3 forward = WorldSenses.forward(newYaw);
        double scale = fly.getFlyScale();
        double speedPerTick = (st.flying ? cfg.flightSpeedBlocksPerS : cfg.walkSpeedBlocksPerS) * scale / 20.0;
        Vec3 target = forward.scale(f * speedPerTick);
        Vec3 v = fly.getDeltaMovement();
        if (jumpNow) {
            // giant-fibre escape: ~30 ms jump backwards/upwards, then flight if enabled
            Vec3 away = forward.scale(-1);
            SensoryFrame.VisualObject threat = frame == null ? null : biggestObject(frame);
            if (threat != null) {
                Vec3 t = WorldSenses.toWorld((float) Math.cos(Math.toRadians(threat.azimuthDeg)), 0f,
                        (float) Math.sin(Math.toRadians(threat.azimuthDeg)), newYaw);
                away = t.scale(-1);
            }
            v = new Vec3(away.x * 0.35 * scale, 0.42 * scale, away.z * 0.35 * scale);
        } else if (st.flying) {
            fly.setNoGravity(true);
            double vy;
            boolean decoderAirborne = mode == MotorDecoder.Mode.FLYING || mode == MotorDecoder.Mode.ESCAPE;
            if (mode == MotorDecoder.Mode.LANDING || !decoderAirborne) {
                // the decoder has left flight (LANDING, or the FLYING->IDLE exit once wing power is gone, or no brain):
                // descend until onGround/isInWater clears st.flying above; hovering forever would deadlock the body
                vy = -0.09;
            } else {
                double err = st.hoverY - fly.getY();
                vy = Mth.clamp(err * 0.15, -0.08, 0.08) + 0.01 * Math.sin(fly.tickCount * 0.6);
                if (fly.verticalCollision && err > 0) st.hoverY = fly.getY();
            }
            v = new Vec3(v.x * 0.75 + target.x * 0.25, vy, v.z * 0.75 + target.z * 0.25);
            if (fly.horizontalCollision && decoderAirborne) st.hoverY = fly.getY() + 0.5;
        } else {
            fly.setNoGravity(false);
            double vy = v.y - fly.getGravity();
            if (fly.onGround() && vy < 0) vy = -0.02;
            double friction = fly.onGround() ? 0.35 : 0.85;
            v = new Vec3(v.x * friction + target.x * (1 - friction), vy, v.z * friction + target.z * (1 - friction));
            if (fly.onGround() && Math.abs(f) > 0.05 && fly.horizontalCollision) v = v.add(0, 0.28 * scale, 0); // step up
        }
        fly.setDeltaMovement(v);
        fly.move(MoverType.SELF, fly.getDeltaMovement());

        // ---------------- animation state ----------------
        float targetProb = cmd == null ? 0 : (float) Mth.clamp(cmd.feed * 1.5, 0, 1);
        if (mode == MotorDecoder.Mode.FEED) targetProb = Math.max(targetProb, 0.7f);
        st.proboscis += (targetProb - st.proboscis) * 0.3f;
    }

    private static SensoryFrame.VisualObject biggestObject(SensoryFrame frame) {
        SensoryFrame.VisualObject best = null;
        for (SensoryFrame.VisualObject o : frame.objects) {
            if (best == null || o.expansionDegPerS * o.angularSizeDeg > best.expansionDegPerS * best.angularSizeDeg) best = o;
        }
        return best;
    }
}
