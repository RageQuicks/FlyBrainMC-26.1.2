package com.fruitfly.net;

import com.fruitfly.FruitFlyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client brain telemetry for one fly, sent to tracking players every few ticks.
 *
 * @param entityId      the fly's network id
 * @param mode          MotorDecoder.Mode ordinal
 * @param reflex        true when the hand-built reflex layer (not the brain) is driving the body this tick
 * @param spikesThisTick spikes in the whole brain during the last brain tick
 * @param activeNeurons  size of the active set
 * @param realTimeFactor 1.0 = brain keeps up with the game clock
 * @param channelNames   decoded motor channel names
 * @param channelValues  decoded motor channel values (0..1 or −1..1)
 * @param popNames       watched population specs
 * @param popRates       mean rate (Hz) of each watched population over the last tick
 * @param retinaRays     per coarse retina ray luminance 0..255 (left eye rays then right eye rays)
 * @param spikeSample    dense neuron indices of a sample of this tick's spikes (for the raster)
 */
public record BrainTelemetryPayload(int entityId, byte mode, boolean reflex, int spikesThisTick, int activeNeurons,
                                    float realTimeFactor, String[] channelNames, float[] channelValues,
                                    String[] popNames, float[] popRates, byte[] retinaRays, int[] spikeSample,
                                    String[] odorNames, float[] odorValues, float odorBearing)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BrainTelemetryPayload> TYPE =
            new CustomPacketPayload.Type<>(FruitFlyMod.id("brain_telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainTelemetryPayload> CODEC =
            CustomPacketPayload.codec(BrainTelemetryPayload::write, BrainTelemetryPayload::new);

    public BrainTelemetryPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readByte(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                readStrings(buf), readFloats(buf), readStrings(buf), readFloats(buf), buf.readByteArray(), buf.readVarIntArray(),
                readStrings(buf), readFloats(buf), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(mode);
        buf.writeBoolean(reflex);
        buf.writeVarInt(spikesThisTick);
        buf.writeVarInt(activeNeurons);
        buf.writeFloat(realTimeFactor);
        writeStrings(buf, channelNames);
        writeFloats(buf, channelValues);
        writeStrings(buf, popNames);
        writeFloats(buf, popRates);
        buf.writeByteArray(retinaRays);
        buf.writeVarIntArray(spikeSample);
        writeStrings(buf, odorNames);
        writeFloats(buf, odorValues);
        buf.writeFloat(odorBearing);
    }

    private static void writeStrings(FriendlyByteBuf buf, String[] s) {
        buf.writeVarInt(s.length);
        for (String x : s) buf.writeUtf(x);
    }

    private static String[] readStrings(FriendlyByteBuf buf) {
        String[] s = new String[buf.readVarInt()];
        for (int i = 0; i < s.length; i++) s[i] = buf.readUtf();
        return s;
    }

    private static void writeFloats(FriendlyByteBuf buf, float[] f) {
        buf.writeVarInt(f.length);
        for (float x : f) buf.writeFloat(x);
    }

    private static float[] readFloats(FriendlyByteBuf buf) {
        float[] f = new float[buf.readVarInt()];
        for (int i = 0; i < f.length; i++) f[i] = buf.readFloat();
        return f;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public float channel(String name) {
        for (int i = 0; i < channelNames.length; i++) if (channelNames[i].equals(name)) return channelValues[i];
        return 0f;
    }

    public float rate(String pop) {
        for (int i = 0; i < popNames.length; i++) if (popNames[i].equals(pop)) return popRates[i];
        return 0f;
    }
}
