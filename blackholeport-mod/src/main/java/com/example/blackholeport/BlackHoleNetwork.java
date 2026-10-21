package com.example.blackholeport;

import com.example.blackholeport.client.ClientBlackHoles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Sends the black holes of a dimension (and the live pull/damage settings) to the clients every tick. */
public final class BlackHoleNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BlackHolePortMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BlackHoleNetwork() {
    }

    public record HoleData(int id, double x, double y, double z, float size, int countdown) {
    }

    public record SyncPacket(List<HoleData> holes, boolean pull, float pullSpeed, boolean damage) {
        public static void encode(SyncPacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.holes().size());
            for (HoleData h : p.holes()) {
                buf.writeVarInt(h.id());
                buf.writeDouble(h.x());
                buf.writeDouble(h.y());
                buf.writeDouble(h.z());
                buf.writeFloat(h.size());
                buf.writeVarInt(h.countdown());
            }
            buf.writeBoolean(p.pull());
            buf.writeFloat(p.pullSpeed());
            buf.writeBoolean(p.damage());
        }

        public static SyncPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<HoleData> holes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                holes.add(new HoleData(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readVarInt()));
            }
            boolean pull = buf.readBoolean();
            float pullSpeed = buf.readFloat();
            boolean damage = buf.readBoolean();
            return new SyncPacket(holes, pull, pullSpeed, damage);
        }

        public static void handle(SyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlackHoles.apply(packet)));
            context.setPacketHandled(true);
        }
    }

    public static void init() {
        CHANNEL.registerMessage(0, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
    }

    private static SyncPacket build(ServerLevel level) {
        List<HoleData> list = new ArrayList<>();
        for (BlackHole h : BlackHoleSavedData.get(level).holes()) {
            list.add(new HoleData(h.id, h.pos.x, h.pos.y, h.pos.z, h.size, h.countdown));
        }
        return new SyncPacket(list, BlackHoleSettings.pull, BlackHoleSettings.pullSpeed, BlackHoleSettings.damage);
    }

    public static void sendToLevel(ServerLevel level) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), build(level));
    }

    public static void sendToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), build(level));
        }
    }
}
